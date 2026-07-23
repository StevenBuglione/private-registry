package com.stevenbuglione.registry.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stevenbuglione.registry.security.identity.AccessContext;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class JdbcCatalogSearchIntegrationTest {

  private static final AccessContext APM_ONE =
      new AccessContext("apm-one-user", Set.of("APM0000001"), false);
  private static final AccessContext ADMINISTRATOR =
      new AccessContext("registry-admin", Set.of(), true);

  @Container
  private static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer("postgres:16-alpine")
          .withDatabaseName("registry_catalog_test")
          .withUsername("registry")
          .withPassword("registry");

  private static JdbcClient jdbc;
  private static JdbcCatalogService catalog;

  @BeforeAll
  static void migrateDatabase() {
    var dataSource = new PGSimpleDataSource();
    dataSource.setUrl(POSTGRESQL.getJdbcUrl());
    dataSource.setUser(POSTGRESQL.getUsername());
    dataSource.setPassword(POSTGRESQL.getPassword());
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .cleanDisabled(true)
        .load()
        .migrate();
    jdbc = JdbcClient.create(dataSource);
    catalog =
        new JdbcCatalogService(jdbc, new CatalogQueryBuilder(), new CatalogPackageEnricher(jdbc));
  }

  @BeforeEach
  void seedCatalog() {
    jdbc.sql("TRUNCATE TABLE packages CASCADE").update();
    jdbc.sql("TRUNCATE TABLE teams CASCADE").update();
    jdbc.sql(
            """
            INSERT INTO teams (id, display_name, support_url)
            VALUES ('platform-azure', 'Azure Platform', 'https://support.example.test/azure')
            """)
        .update();
    var azureRm =
        insertPackage(
            "azurerm",
            "Azure Resource Manager",
            "Manage Azure cloud networking and compute resources",
            "APM0000001",
            Instant.parse("2026-07-23T12:00:00Z"));
    var latestAzureRm =
        insertVersion(azureRm, "4.1.0", '1', 12, Instant.parse("2026-07-23T12:00:00Z"));
    insertVersion(azureRm, "4.0.0", '2', 8, Instant.parse("2026-07-22T12:00:00Z"));
    jdbc.sql(
            """
            INSERT INTO package_owners (package_id, team_id, owner_order)
            VALUES (:packageId, 'platform-azure', 0)
            """)
        .param("packageId", azureRm)
        .update();
    jdbc.sql(
            """
            INSERT INTO symbols (
                package_version_id, kind, name, description, document_path,
                symbol_type, is_required, sensitive)
            VALUES (
                :versionId, 'resource', 'azurerm_resource_group',
                'Manages an Azure resource group.',
                'resources/resource_group.md', 'resource', false, false)
            """)
        .param("versionId", latestAzureRm)
        .update();
    jdbc.sql(
            """
            INSERT INTO documentation_pages (
                package_version_id, path, title, content_type,
                storage_key, digest, size_bytes, content)
            VALUES (
                :versionId, 'README.md', 'AzureRM', 'text/markdown',
                'docs/azurerm/README.md',
                'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                24, '# AzureRM provider')
            """)
        .param("versionId", latestAzureRm)
        .update();
    jdbc.sql(
            """
            INSERT INTO approvals (
                package_version_id, approval_type, decision, decided_by,
                decided_at, policy_version, evidence_uri)
            VALUES (
                :versionId, 'security', 'approved', 'security-team',
                now(), '2026.1', 's3://evidence/azurerm')
            """)
        .param("versionId", latestAzureRm)
        .update();

    var azureAd =
        insertPackage(
            "azuread",
            "Microsoft Entra ID",
            "Manage Azure identity groups and applications",
            "APM0000001",
            Instant.parse("2026-07-22T12:00:00Z"));
    insertVersion(azureAd, "3.0.0", '3', 5, Instant.parse("2026-07-22T12:00:00Z"));

    var aws =
        insertPackage(
            "aws",
            "Amazon Web Services",
            "Manage AWS cloud infrastructure",
            "APM0000002",
            Instant.parse("2026-07-21T12:00:00Z"));
    insertVersion(aws, "6.0.0", '4', 100, Instant.parse("2026-07-21T12:00:00Z"));
  }

  @Test
  void searchesRanksAndPaginatesInsideTheApmAuthorizedCatalog() {
    var first = catalog.findPackages(APM_ONE, query("azure", "relevance", null, 1));

    assertThat(first.items()).extracting(item -> item.name()).containsExactly("azurerm");
    assertThat(first.total()).isEqualTo(2);
    assertThat(first.nextCursor()).isNotBlank();

    var second =
        catalog.findPackages(
            APM_ONE,
            query("azure", "relevance", java.util.Objects.requireNonNull(first.nextCursor()), 1));
    assertThat(second.items()).extracting(item -> item.name()).containsExactly("azuread");
    assertThat(second.nextCursor()).isNull();
  }

  @Test
  void sortsByProjectedLatestDownloadTotalsWithoutLeakingOtherApmPackages() {
    var memberPage = catalog.findPackages(APM_ONE, query(null, "downloads", null, 25));
    var adminPage = catalog.findPackages(ADMINISTRATOR, query(null, "downloads", null, 25));

    assertThat(memberPage.items())
        .extracting(item -> item.name())
        .containsExactly("azurerm", "azuread");
    assertThat(adminPage.items())
        .extracting(item -> item.name())
        .containsExactly("aws", "azurerm", "azuread");
    assertThat(memberPage.items().getFirst().versions())
        .extracting(
            version -> java.util.Objects.requireNonNull(version.downloadStatistics()).allTime())
        .containsExactly(12L, 8L);
  }

  @Test
  void returnsAuthorizedDetailsDocumentationGovernanceAndCountsWithoutNPlusOneSearches() {
    var details = catalog.getPackage(APM_ONE, "provider/hashicorp/azurerm");
    var document = catalog.readDocument(APM_ONE, "provider/hashicorp/azurerm", "index.md");
    var governance = catalog.getGovernance(APM_ONE, "provider/hashicorp/azurerm");

    assertThat(details.owners()).containsExactly("platform-azure");
    assertThat(details.symbols())
        .extracting(symbol -> symbol.name())
        .containsExactly("azurerm_resource_group");
    assertThat(details.versions()).hasSize(2);
    assertThat(document.content()).isEqualTo("# AzureRM provider");
    assertThat(governance.approvals()).hasSize(1);
    assertThat(governance.supportUrl()).isEqualTo("https://support.example.test/azure");
    assertThat(
            catalog.countPackages(APM_ONE, com.stevenbuglione.registry.model.PackageKind.PROVIDER))
        .isEqualTo(2);
    assertThat(
            catalog.filterAccessiblePackageIds(
                APM_ONE, java.util.List.of("provider/hashicorp/aws", "provider/hashicorp/azurerm")))
        .containsExactly("provider/hashicorp/azurerm");
    assertThatThrownBy(() -> catalog.getPackage(APM_ONE, "provider/hashicorp/aws"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void filtersByTheAuthoritativeNormalizedCategoryRelation() {
    var azureAd =
        jdbc.sql("SELECT id FROM packages WHERE name = 'azuread'").query(UUID.class).single();
    jdbc.sql("DELETE FROM package_categories WHERE package_id = :packageId")
        .param("packageId", azureAd)
        .update();
    jdbc.sql(
            """
            INSERT INTO package_categories (package_id, category_slug)
            VALUES (:packageId, 'networking')
            """)
        .param("packageId", azureAd)
        .update();

    var networking = catalog.findPackages(APM_ONE, queryByCategory("networking"));
    var publicCloud = catalog.findPackages(APM_ONE, queryByCategory("public-cloud"));

    assertThat(networking.items()).extracting(item -> item.name()).containsExactly("azuread");
    assertThat(publicCloud.items()).extracting(item -> item.name()).containsExactly("azurerm");
  }

  @Test
  void deterministicallySelectsSemanticVersionsBeforePublicationTimes() {
    var azureRm =
        jdbc.sql("SELECT id FROM packages WHERE name = 'azurerm'").query(UUID.class).single();
    insertVersion(azureRm, "4.9.0", 'a', 9, Instant.parse("2026-07-24T12:00:00Z"));
    insertVersion(azureRm, "4.10.0", 'b', 10, Instant.parse("2026-07-20T12:00:00Z"));

    var details = catalog.getPackage(APM_ONE, "provider/hashicorp/azurerm");

    assertThat(details.latestVersion()).isEqualTo("4.10.0");
    assertThat(details.versions())
        .extracting(version -> version.version())
        .containsExactly("4.10.0", "4.9.0", "4.1.0", "4.0.0");
  }

  private static UUID insertPackage(
      String name, String title, String description, String apmId, Instant updatedAt) {
    var packageId =
        jdbc.sql(
                """
                INSERT INTO packages (
                    kind, namespace, name, target, title, description,
                    source_address, visibility, risk_tier, verification,
                    support_level, lifecycle, registry_tier, updated_at)
                VALUES (
                    'provider', 'hashicorp', :name, '', :title, :description,
                    'hashicorp/' || :name, 'restricted', 'low', 'verified',
                    'supported', 'approved', 'official', :updatedAt)
                RETURNING id
                """)
            .param("name", name)
            .param("title", title)
            .param("description", description)
            .param("updatedAt", java.sql.Timestamp.from(updatedAt))
            .query(UUID.class)
            .single();
    jdbc.sql(
            """
            INSERT INTO package_categories (package_id, category_slug)
            VALUES (:packageId, 'public-cloud')
            """)
        .param("packageId", packageId)
        .update();
    jdbc.sql("INSERT INTO package_apm_access (package_id, apm_id) VALUES (:packageId, :apmId)")
        .param("packageId", packageId)
        .param("apmId", apmId)
        .update();
    return packageId;
  }

  private static UUID insertVersion(
      UUID packageId, String version, char digestCharacter, long downloads, Instant publishedAt) {
    var digest = "sha256:" + String.valueOf(digestCharacter).repeat(64);
    var versionId =
        jdbc.sql(
                """
                INSERT INTO package_versions (
                    package_id, version, package_digest, documentation_digest,
                    documentation_root, artifact_repository, artifact_path,
                    source_repository, source_commit, source_tag,
                    terraform_constraint, published_at)
                VALUES (
                    :packageId, :version, :digest, :documentationDigest,
                    'docs', 'iac-provider-release-local', :artifactPath,
                    'https://example.test/source', 'commit', :sourceTag,
                    '>= 1.5', :publishedAt)
                RETURNING id
                """)
            .param("packageId", packageId)
            .param("version", version)
            .param("digest", digest)
            .param(
                "documentationDigest",
                "sha256:" + String.valueOf((char) (digestCharacter + 4)).repeat(64))
            .param("artifactPath", "hashicorp/provider/" + version + ".zip")
            .param("sourceTag", "v" + version)
            .param("publishedAt", java.sql.Timestamp.from(publishedAt))
            .query(UUID.class)
            .single();
    jdbc.sql(
            """
            INSERT INTO artifact_download_statistics (
                package_version_id, observed_on, observed_at, download_count)
            VALUES (:versionId, CURRENT_DATE, now(), :downloads)
            """)
        .param("versionId", versionId)
        .param("downloads", downloads)
        .update();
    return versionId;
  }

  private static CatalogQuery query(
      @Nullable String q, String sort, @Nullable String cursor, int limit) {
    return new CatalogQuery(
        new CatalogQuery.Criteria(q, null, null, null, null, sort, cursor, limit, null));
  }

  private static CatalogQuery queryByCategory(String category) {
    return new CatalogQuery(
        new CatalogQuery.Criteria(null, null, null, null, category, "name", null, 25, null));
  }
}
