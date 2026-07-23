package com.stevenbuglione.registry.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.stevenbuglione.registry.audit.AuditLogService;
import com.stevenbuglione.registry.security.identity.AccessContext;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class HomepageSettingsIntegrationTest {

  @Container
  private static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer("postgres:16-alpine")
          .withDatabaseName("registry_homepage_test")
          .withUsername("registry")
          .withPassword("registry");

  private static JdbcClient jdbc;
  private static HomepageSettingsService service;

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
    service = new HomepageSettingsService(jdbc, mock(AuditLogService.class));
  }

  @BeforeEach
  void resetHomepage() {
    jdbc.sql("TRUNCATE TABLE packages CASCADE").update();
    jdbc.sql(
            """
            UPDATE registry_homepage_settings
               SET notification_enabled = true,
                   notification_title = 'Registry',
                   notification_message = 'Available packages.',
                   notification_link_label = NULL,
                   notification_link_url = NULL,
                   updated_by = 'test',
                   updated_at = now()
            """)
        .update();
  }

  @Test
  void normalizedFeaturesAreOrderedAuthoritativeAndApmFiltered() {
    var aws = insertPackage("provider", "hashicorp", "aws", "", "APM0000001");
    insertPackage("provider", "hashicorp", "azurerm", "", "APM0000002");
    var module = insertPackage("module", "Azure", "avm-res-web-site", "azurerm", "APM0000001");

    var updated =
        service.update(
            update(
                List.of("provider/hashicorp/azurerm", "provider/hashicorp/aws"),
                List.of("module/Azure/avm-res-web-site/azurerm")),
            "registry-admin");

    assertThat(updated.featuredProviderIds())
        .containsExactly("provider/hashicorp/azurerm", "provider/hashicorp/aws");
    assertThat(updated.featuredModuleIds())
        .containsExactly("module/Azure/avm-res-web-site/azurerm");
    assertThat(
            service
                .get(new AccessContext("apm-one", Set.of("APM0000001"), false))
                .featuredProviderIds())
        .containsExactly("provider/hashicorp/aws");
    assertThat(
            service
                .get(new AccessContext("apm-one", Set.of("APM0000001"), false))
                .featuredModuleIds())
        .containsExactly("module/Azure/avm-res-web-site/azurerm");
    assertThat(service.get(new AccessContext("no-access", Set.of(), false)).featuredProviderIds())
        .isEmpty();

    assertThat(service.get().featuredProviderIds())
        .containsExactly("provider/hashicorp/azurerm", "provider/hashicorp/aws");
    assertThat(service.get().featuredModuleIds())
        .containsExactly("module/Azure/avm-res-web-site/azurerm");
    assertThat(
            jdbc.sql(
                    """
                    SELECT count(*)
                      FROM information_schema.columns
                     WHERE table_schema = 'public'
                       AND table_name = 'registry_homepage_settings'
                       AND column_name IN ('featured_provider_ids', 'featured_module_ids')
                    """)
                .query(Long.class)
                .single())
        .isZero();
    assertThat(
            jdbc.sql(
                    """
                    SELECT package_id
                      FROM registry_homepage_features
                     WHERE feature_kind = 'provider'
                     ORDER BY display_order
                    """)
                .query(UUID.class)
                .list())
        .containsExactly(packageId("provider", "hashicorp", "azurerm", ""), aws);
    assertThat(
            jdbc.sql(
                    """
                    SELECT package_id
                      FROM registry_homepage_features
                     WHERE feature_kind = 'module'
                    """)
                .query(UUID.class)
                .single())
        .isEqualTo(module);
  }

  @Test
  void rejectsDuplicateMissingAndKindMismatchedFeaturesWithoutChangingCurrentSelection() {
    var provider = insertPackage("provider", "hashicorp", "aws", "", "APM0000001");
    var module = insertPackage("module", "Azure", "avm-res-web-site", "azurerm", "APM0000001");
    service.update(
        update(List.of("provider/hashicorp/aws"), List.of("module/Azure/avm-res-web-site/azurerm")),
        "registry-admin");

    assertThatThrownBy(
            () ->
                service.update(
                    update(List.of("provider/hashicorp/aws", "provider/hashicorp/aws"), List.of()),
                    "registry-admin"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicates");
    assertThatThrownBy(
            () ->
                service.update(
                    update(List.of("provider/hashicorp/missing"), List.of()), "registry-admin"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("existing Registry package");
    assertThat(service.get().featuredProviderIds()).containsExactly("provider/hashicorp/aws");
    assertThat(service.get().featuredModuleIds())
        .containsExactly("module/Azure/avm-res-web-site/azurerm");

    assertThatThrownBy(
            () ->
                jdbc.sql(
                        """
                        INSERT INTO registry_homepage_features (
                            feature_kind, package_id, display_order)
                        VALUES ('provider', :moduleId, 5)
                        """)
                    .param("moduleId", module)
                    .update())
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        """
                        INSERT INTO registry_homepage_features (
                            feature_kind, package_id, display_order)
                        VALUES ('provider', :providerId, 0)
                        """)
                    .param("providerId", provider)
                    .update())
        .isInstanceOf(RuntimeException.class);
  }

  private static HomepageSettingsService.Update update(
      List<String> providers, List<String> modules) {
    return new HomepageSettingsService.Update(
        true, "Registry", "Available packages.", null, null, providers, modules);
  }

  private static UUID insertPackage(
      String kind, String namespace, String name, String target, String apmId) {
    var packageId =
        jdbc.sql(
                """
                INSERT INTO packages (
                    kind, namespace, name, target, title, description, source_address,
                    visibility, risk_tier, verification, support_level, lifecycle)
                VALUES (
                    CAST(:kind AS package_kind), :namespace, :name, :target, :name,
                    'Homepage integration fixture', :sourceAddress, 'restricted', 'low',
                    'enterprise-verified', 'supported', 'approved')
                RETURNING id
                """)
            .param("kind", kind)
            .param("namespace", namespace)
            .param("name", name)
            .param("target", target)
            .param("sourceAddress", namespace + "/" + name)
            .query(UUID.class)
            .single();
    jdbc.sql(
            """
            INSERT INTO package_apm_access (package_id, apm_id)
            VALUES (:packageId, :apmId)
            """)
        .param("packageId", packageId)
        .param("apmId", apmId)
        .update();
    return packageId;
  }

  private static UUID packageId(String kind, String namespace, String name, String target) {
    return jdbc.sql(
            """
            SELECT id
              FROM packages
             WHERE kind = CAST(:kind AS package_kind)
               AND namespace = :namespace
               AND name = :name
               AND target = :target
            """)
        .param("kind", kind)
        .param("namespace", namespace)
        .param("name", name)
        .param("target", target)
        .query(UUID.class)
        .single();
  }
}
