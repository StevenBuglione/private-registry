package com.stevenbuglione.registry.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stevenbuglione.registry.model.CatalogPackage;
import com.stevenbuglione.registry.model.PackageKind;
import com.stevenbuglione.registry.security.identity.AccessContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class CatalogQueryBuilderTest {

  private final CatalogQueryBuilder queries = new CatalogQueryBuilder();

  @Test
  void searchesAndRanksInsideTheAuthorizedPostgresqlQuery() {
    var plan = queries.findPackages(member(), query("azure network", "relevance", null, 25));

    assertThat(plan.sql())
        .contains("p.search_document @@ websearch_to_tsquery")
        .contains("similarity(")
        .contains("package_apm_access")
        .contains("ORDER BY relevance_score DESC")
        .doesNotContain("searchPackageIds")
        .doesNotContain(" IN (:searchPackageIds)")
        .doesNotContain("package_download_totals");
    assertThat(plan.parameters())
        .containsEntry("query", "azure network")
        .containsEntry("queryPattern", "%azure network%")
        .containsEntry("authorizedApmIds", Set.of("APM0000001"));
  }

  @Test
  void projectsLatestVersionDownloadsBeforeSorting() {
    var plan = queries.findPackages(member(), query(null, "downloads", null, 25));

    assertThat(plan.sql())
        .contains("WITH latest_version_downloads AS")
        .contains("SELECT DISTINCT ON (statistics.package_version_id)")
        .contains("package_download_totals AS")
        .contains("ORDER BY download_count DESC");
  }

  @Test
  void appliesAuthorizationTaxonomyAndOffsetFiltersAsParameters() {
    var plan =
        queries.findPackages(
            member(),
            new CatalogQuery(
                new CatalogQuery.Criteria(
                    null,
                    PackageKind.PROVIDER,
                    "provider",
                    "official",
                    "public-cloud",
                    "name",
                    null,
                    20,
                    "HashiCorp"),
                3));

    assertThat(plan.sql())
        .contains("visible_apm.apm_id IN (:authorizedApmIds)")
        .contains("p.kind = CAST(:kind AS package_kind)")
        .contains("p.registry_tier IN (:registryTiers)")
        .contains("FROM package_categories visible_category")
        .contains("visible_category.package_id = p.id")
        .contains("visible_category.category_slug IN (:registryCategories)")
        .doesNotContain("unnest(p.categories)")
        .contains("OFFSET :pageOffset");
    assertThat(plan.parameters())
        .containsEntry("pageOffset", 40)
        .containsEntry("namespace", "HashiCorp")
        .containsEntry("pageSize", 20);
  }

  @Test
  void closesCatalogForAUserWithoutEntitlementsButLetsAdministratorsBypassApmFiltering() {
    var noEntitlement =
        queries.findPackages(
            new AccessContext("empty", Set.of(), false), query(null, "updated", null, 25));
    var administrator =
        queries.findPackages(
            new AccessContext("admin", Set.of(), true), query(null, "updated", null, 25));

    assertThat(noEntitlement.sql()).contains("AND 1 = 0");
    assertThat(administrator.sql())
        .doesNotContain("package_apm_access")
        .doesNotContain("AND 1 = 0");
  }

  @Test
  void roundTripsDatabaseDerivedCursorValuesAndRejectsTampering() {
    var cursor =
        CatalogQueryBuilder.encodeCursor(query("azure", "relevance", null, 1), row(23, 75.875));
    var plan = queries.findPackages(member(), query("azure", "relevance", cursor, 1));

    assertThat(plan.parameters())
        .containsEntry("cursorId", "provider/hashicorp/azurerm")
        .containsEntry("cursorRelevance", 75.875);
    assertThat(plan.sql()).contains(":cursorRelevance");

    var wrongSort =
        query(
            null,
            "downloads",
            Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                    "name\u001f\u001fprovider/hashicorp/azurerm".getBytes(StandardCharsets.UTF_8)),
            1);
    assertThatThrownBy(() -> queries.findPackages(member(), wrongSort))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not match");
  }

  @Test
  void rejectsMalformedAndNonFiniteRelevanceCursors() {
    assertThatThrownBy(
            () -> queries.findPackages(member(), query("azure", "relevance", "not-base64", 1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid catalog cursor");

    var nonFinite =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                "relevance\u001fNaN\u001fprovider/hashicorp/azurerm"
                    .getBytes(StandardCharsets.UTF_8));
    assertThatThrownBy(
            () -> queries.findPackages(member(), query("azure", "relevance", nonFinite, 1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid catalog cursor");
  }

  @Test
  void usesDatabaseDerivedValuesForNameUpdatedAndDownloadCursors() {
    var row = row(23, 0);
    var nameQuery = query(null, "name", null, 1);
    var updatedQuery = query(null, "updated", null, 1);
    var downloadsQuery = query(null, "downloads", null, 1);

    var namePlan =
        queries.findPackages(
            member(), query(null, "name", CatalogQueryBuilder.encodeCursor(nameQuery, row), 1));
    var updatedPlan =
        queries.findPackages(
            member(),
            query(null, "updated", CatalogQueryBuilder.encodeCursor(updatedQuery, row), 1));
    var downloadsPlan =
        queries.findPackages(
            member(),
            query(null, "downloads", CatalogQueryBuilder.encodeCursor(downloadsQuery, row), 1));

    assertThat(namePlan.parameters()).containsEntry("cursorId", "provider/hashicorp/azurerm");
    assertThat(updatedPlan.parameters())
        .containsEntry("cursorUpdated", Instant.parse("2026-07-23T12:00:00Z"));
    assertThat(downloadsPlan.parameters()).containsEntry("cursorDownloads", 23L);
  }

  @Test
  void rejectsInvalidTypedCursorValues() {
    var invalidUpdated = encodedCursor("updated", "not-an-instant");
    var invalidDownloads = encodedCursor("downloads", "not-a-number");

    assertThatThrownBy(
            () -> queries.findPackages(member(), query(null, "updated", invalidUpdated, 1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid catalog cursor");
    assertThatThrownBy(
            () -> queries.findPackages(member(), query(null, "downloads", invalidDownloads, 1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid catalog cursor");
  }

  @Test
  void treatsTheNoneTierAsAnExplicitEmptyCatalog() {
    var plan = queries.findPackages(member(), queryWithTier(null, "updated", null, 25, "none"));

    assertThat(plan.sql()).contains("AND 1 = 0");
  }

  @Test
  void usesDeterministicSemverAwareOrderingForTheLatestVersion() {
    var plan = queries.findPackages(member(), query(null, "updated", null, 25));

    assertThat(plan.sql())
        .contains("pv.published_at DESC")
        .contains("string_to_array(")
        .contains("::numeric[]")
        .contains("pv.prerelease ASC")
        .contains("pv.version COLLATE \"C\" DESC")
        .contains("pv.id DESC");
  }

  private static AccessContext member() {
    return new AccessContext("member", Set.of("APM0000001"), false);
  }

  private static CatalogQuery query(
      @Nullable String q, String sort, @Nullable String cursor, int limit) {
    return new CatalogQuery(
        new CatalogQuery.Criteria(q, null, null, null, null, sort, cursor, limit, null));
  }

  private static CatalogQuery queryWithTier(
      @Nullable String q, String sort, @Nullable String cursor, int limit, String tier) {
    return new CatalogQuery(
        new CatalogQuery.Criteria(q, null, null, tier, null, sort, cursor, limit, null));
  }

  private static CatalogReadRow row(long downloads, double relevance) {
    var item =
        new CatalogPackage(
            "provider/hashicorp/azurerm",
            PackageKind.PROVIDER,
            "hashicorp",
            "azurerm",
            "",
            "AzureRM",
            "Azure resources",
            "4.0.0",
            List.of(),
            "supported",
            "approved",
            "verified",
            "official",
            "low",
            "hashicorp/azurerm",
            Instant.parse("2026-07-23T12:00:00Z"),
            List.of(),
            List.of());
    return new CatalogReadRow(UUID.randomUUID(), item, downloads, relevance);
  }

  private static String encodedCursor(String sort, String value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(
            String.join("\u001f", sort, value, "provider/hashicorp/azurerm")
                .getBytes(StandardCharsets.UTF_8));
  }
}
