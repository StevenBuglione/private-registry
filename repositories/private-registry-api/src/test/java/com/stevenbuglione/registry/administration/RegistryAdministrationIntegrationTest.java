package com.stevenbuglione.registry.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stevenbuglione.registry.audit.AuditLogService;
import com.stevenbuglione.registry.catalog.HomepageSettingsService;
import com.stevenbuglione.registry.eventing.CatalogArtifactChanged;
import com.stevenbuglione.registry.eventing.CatalogEventPublisher;
import com.stevenbuglione.registry.health.WorkerDependencyHealthService;
import java.util.List;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
class RegistryAdministrationIntegrationTest {

  @Container
  private static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer("postgres:18-alpine")
          .withDatabaseName("registry_admin_test")
          .withUsername("registry")
          .withPassword("registry");

  private static JdbcClient jdbc;
  private static AuditLogService audit;

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
    audit = new AuditLogService(jdbc, new ObjectMapper());
  }

  @BeforeEach
  void resetAdministrationData() {
    jdbc.sql(
            """
            TRUNCATE TABLE
                registry_sync_credentials,
                audit_events,
                catalog_event_queue,
                ingestion_quarantine,
                ingestion_events,
                reconciliation_runs
            """)
        .update();
  }

  @Test
  void createsAuthenticatesAuditsAndRevokesOneTimeCredentials() {
    var service = new SyncCredentialService(jdbc, audit);

    var created =
        service.create(
            new SyncCredentialService.CreateCommand("GitHub module releases", "module", 90),
            "admin-1");

    assertThat(created.token()).startsWith("rgs." + created.credential().id() + ".");
    assertThat(created.credential().status()).isEqualTo(SyncCredentialService.Status.ACTIVE);
    assertThat(service.list())
        .singleElement()
        .satisfies(value -> assertThat(value.useCount()).isZero());

    var authenticated = service.authenticate("Bearer " + created.token());
    assertThat(authenticated.id()).isEqualTo(created.credential().id());
    assertThat(authenticated.scope()).isEqualTo(SyncCredentialService.Scope.MODULE);
    assertThat(service.list())
        .singleElement()
        .satisfies(value -> assertThat(value.useCount()).isOne());

    var revoked = service.revoke(created.credential().id(), "admin-2");
    assertThat(revoked.status()).isEqualTo(SyncCredentialService.Status.REVOKED);
    assertThatThrownBy(() -> service.authenticate("Bearer " + created.token()))
        .isInstanceOf(SyncCredentialService.CredentialAuthenticationException.class);
    assertThat(audit.recent(20, null))
        .extracting(AuditLogService.AuditEvent::action)
        .containsExactly("registry.sync_credential.revoked", "registry.sync_credential.created");
  }

  @Test
  void validatesCredentialPolicyWithoutPersistingUnsafeValues() {
    var service = new SyncCredentialService(jdbc, audit);

    assertThatThrownBy(
            () ->
                service.create(new SyncCredentialService.CreateCommand("x", "module", 90), "admin"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("3 to 80");
    assertThatThrownBy(
            () ->
                service.create(
                    new SyncCredentialService.CreateCommand("Runner", "invalid", 90), "admin"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("scope");
    assertThatThrownBy(
            () ->
                service.create(
                    new SyncCredentialService.CreateCommand("Runner", "provider", 0), "admin"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("365");
    assertThatThrownBy(() -> service.authenticate("Bearer malformed"))
        .isInstanceOf(SyncCredentialService.CredentialAuthenticationException.class);
    assertThatThrownBy(() -> service.revoke(java.util.UUID.randomUUID(), "admin"))
        .isInstanceOf(SyncCredentialService.CredentialNotFoundException.class);
    assertThat(SyncCredentialService.Scope.ALL.allows("provider")).isTrue();
    assertThat(SyncCredentialService.Scope.MODULE.allows("provider")).isFalse();
  }

  @Test
  void routesScopedRunnerRequestsThroughTheDurableEventPublisher() {
    var credentials = new SyncCredentialService(jdbc, audit);
    var created =
        credentials.create(
            new SyncCredentialService.CreateCommand("Module runner", "module", 30), "admin");
    var publisher = mock(CatalogEventPublisher.class);
    when(publisher.publish(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new CatalogEventPublisher.PublishReceipt("publication-1"));
    ObjectProvider<CatalogEventPublisher> publisherProvider = mock();
    when(publisherProvider.getIfAvailable()).thenReturn(publisher);
    var service = new SyncTriggerService(credentials, publisherProvider, audit);

    var receipt =
        service.trigger(
            "Bearer " + created.token(),
            new SyncTriggerService.TriggerCommand(
                "github-12345678",
                "module",
                "iac-module-release-local",
                "Azure/vnet/azurerm/1.0.0.zip",
                SyncTriggerService.Action.DEPLOYED));

    assertThat(receipt.status()).isEqualTo("accepted");
    var event = ArgumentCaptor.forClass(CatalogArtifactChanged.class);
    verify(publisher).publish(event.capture());
    assertThat(event.getValue().repository()).isEqualTo("iac-module-release-local");
    assertThat(event.getValue().path()).isEqualTo("Azure/vnet/azurerm/1.0.0.zip");
    assertThat(audit.recent(10, null))
        .extracting(AuditLogService.AuditEvent::action)
        .contains("registry.sync.triggered");

    assertThatThrownBy(
            () ->
                service.trigger(
                    "Bearer " + created.token(),
                    new SyncTriggerService.TriggerCommand(
                        "github-87654321",
                        "provider",
                        "iac-provider-release-local",
                        "hashicorp/aws/1.0.0/linux_amd64.zip",
                        SyncTriggerService.Action.DEPLOYED)))
        .isInstanceOf(SyncTriggerService.CredentialScopeException.class);
    assertThatThrownBy(
            () ->
                service.trigger(
                    "Bearer " + created.token(),
                    new SyncTriggerService.TriggerCommand(
                        "short",
                        "module",
                        "iac-module-release-local",
                        "Azure/vnet/azurerm/1.0.0.zip",
                        SyncTriggerService.Action.DEPLOYED)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Idempotency-Key");
  }

  @Test
  void reportsCatalogQueueIngestionReconciliationAndStructuredActivity() {
    jdbc.sql(
            """
            INSERT INTO catalog_event_queue (event_id, payload, status)
            VALUES (
                'event-1',
                '{"repository":"iac-module-release-local","path":"Azure/vnet/azurerm/1.0.0.zip"}',
                'dead_letter')
            """)
        .update();
    jdbc.sql(
            """
            INSERT INTO ingestion_events (
                event_id, idempotency_key, event_type, schema_version, package_digest,
                status, attempts, correlation_id, source_repository, source_path,
                payload, last_attempt_at, completed_at)
            VALUES (
                'ingestion-1', 'key-1', 'deployed', 1, NULL,
                'quarantined', 1, 'correlation-1', 'iac-module-release-local',
                'Azure/vnet/azurerm/1.0.0.zip', '{}'::jsonb, now(), now())
            """)
        .update();
    jdbc.sql(
            """
            INSERT INTO reconciliation_runs (
                mode, scope, status, discrepancies, repaired, completed_at)
            VALUES ('repair', 'all-ready-manifests', 'completed', 2, 2, now())
            """)
        .update();
    ObjectProvider<WorkerDependencyHealthService> health = mock();
    when(health.getIfAvailable()).thenReturn(null);
    var dashboards = new AdminDashboardService(jdbc, health);

    var dashboard = dashboards.dashboard();
    assertThat(dashboard.status()).isEqualTo("degraded");
    assertThat(dashboard.queue().deadLetter()).isOne();
    assertThat(dashboard.ingestion().quarantined()).isOne();
    assertThat(dashboard.dependencies()).containsEntry("artifactory", "not_configured");
    assertThat(dashboard.reconciliation()).isNotNull();

    var activity = new AdminOperationsService(jdbc).recent(20);
    assertThat(activity)
        .extracting(AdminOperationsService.OperationalEvent::source)
        .contains("ingestion", "queue", "reconciliation");
  }

  @Test
  void homepageChangesRecordCompleteBeforeAndAfterAuditEvidence() {
    var homepage = new HomepageSettingsService(jdbc, audit);
    var updated =
        homepage.update(
            new HomepageSettingsService.Update(
                true,
                "Planned maintenance",
                "The Registry will remain readable.",
                "Status",
                "/status",
                List.of("provider/hashicorp/aws"),
                List.of("module/terraform-aws-modules/iam/aws")),
            "admin-home");

    assertThat(updated.notificationTitle()).isEqualTo("Planned maintenance");
    var event = audit.recent(1, null).getFirst();
    assertThat(event.action()).isEqualTo("registry.homepage.updated");
    assertThat(event.actorId()).isEqualTo("admin-home");
    assertThat(event.detail().path("before").isObject()).isTrue();
    assertThat(event.detail().path("after").path("notificationTitle").stringValue())
        .isEqualTo("Planned maintenance");
    assertThat(Set.copyOf(updated.featuredProviderIds())).containsExactly("provider/hashicorp/aws");
    assertThat(Set.copyOf(updated.featuredModuleIds()))
        .containsExactly("module/terraform-aws-modules/iam/aws");
  }
}
