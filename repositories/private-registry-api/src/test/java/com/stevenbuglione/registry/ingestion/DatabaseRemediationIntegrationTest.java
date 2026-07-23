package com.stevenbuglione.registry.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stevenbuglione.registry.eventing.CatalogArtifactChanged;
import com.stevenbuglione.registry.eventing.CatalogEventIdentityCollisionException;
import com.stevenbuglione.registry.eventing.PostgresCatalogEventPublisher;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
class DatabaseRemediationIntegrationTest {

  private static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(5);
  private static final Instant OCCURRED_AT = Instant.parse("2026-07-23T12:00:00Z");

  @Container
  private static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer("postgres:16-alpine")
          .withDatabaseName("registry_remediation_test")
          .withUsername("registry")
          .withPassword("registry");

  private static PGSimpleDataSource dataSource;
  private static JdbcClient jdbc;
  private static ObjectMapper objectMapper;

  @BeforeAll
  static void migrateDatabase() {
    dataSource = dataSource(POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .cleanDisabled(true)
        .load()
        .migrate();
    jdbc = JdbcClient.create(dataSource);
    objectMapper = new ObjectMapper();
  }

  @BeforeEach
  void resetDatabase() {
    jdbc.sql(
            """
            TRUNCATE TABLE
                catalog_event_queue,
                ingestion_quarantine,
                ingestion_events,
                registry_page_views,
                registry_traffic_identities,
                packages,
                teams
            RESTART IDENTITY CASCADE
            """)
        .update();
  }

  @Test
  void normalizesRegistryRelationsAndEnforcesOwnershipIntegrity() {
    var packageId = insertPackage("azurerm", "public-cloud", "networking");
    var otherPackageId = insertPackage("azuread", "security-authentication");
    var versionId = insertVersion(packageId, false, true);

    assertThat(
            jdbc.sql(
                    """
                    SELECT category_slug
                      FROM package_categories
                     WHERE package_id = :packageId
                     ORDER BY category_slug
                    """)
                .param("packageId", packageId)
                .query(String.class)
                .list())
        .containsExactly("networking", "public-cloud");
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        """
                        INSERT INTO package_categories (package_id, category_slug)
                        VALUES (:packageId, 'not-governed')
                        """)
                    .param("packageId", packageId)
                    .update())
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        """
                        INSERT INTO package_categories (package_id, category_slug)
                        VALUES (:packageId, 'networking')
                        """)
                    .param("packageId", packageId)
                    .update())
        .isInstanceOf(RuntimeException.class);

    jdbc.sql(
            """
            INSERT INTO package_apm_access (package_id, apm_id)
            VALUES (:packageId, 'APM0000001')
            """)
        .param("packageId", packageId)
        .update();
    assertThat(
            jdbc.sql("SELECT display_name FROM apm_contexts WHERE apm_id = 'APM0000001'")
                .query(String.class)
                .single())
        .isEqualTo("APM0000001");

    jdbc.sql(
            """
            INSERT INTO registry_homepage_features (
                feature_kind, package_id, display_order)
            VALUES ('provider', :packageId, 0)
            """)
        .param("packageId", packageId)
        .update();
    assertThat(
            jdbc.sql(
                    """
                    SELECT package_id
                      FROM registry_homepage_features
                     WHERE feature_kind = 'provider'
                    """)
                .query(UUID.class)
                .single())
        .isEqualTo(packageId);
    assertThat(
            jdbc.sql("SELECT notification_message FROM registry_homepage_settings")
                .query(String.class)
                .single())
        .doesNotContainIgnoringCase("approved providers");

    var identityId =
        jdbc.sql(
                """
                INSERT INTO registry_traffic_identities (
                    subject, display_name, email, first_seen_at, last_seen_at)
                VALUES (
                    'subject-1', 'Registry User', 'registry@example.test', now(), now())
                RETURNING id
                """)
            .query(UUID.class)
            .single();
    jdbc.sql(
            """
            INSERT INTO registry_page_views (identity_id, path, occurred_at)
            VALUES (:identityId, '/providers', now())
            """)
        .param("identityId", identityId)
        .update();
    assertThat(
            jdbc.sql(
                    """
                    SELECT identity.subject
                      FROM registry_page_views page_view
                      JOIN registry_traffic_identities identity
                        ON identity.id = page_view.identity_id
                    """)
                .query(String.class)
                .single())
        .isEqualTo("subject-1");
    jdbc.sql("DELETE FROM registry_page_views WHERE identity_id = :identityId")
        .param("identityId", identityId)
        .update();
    assertThat(
            jdbc.sql("SELECT count(*) FROM registry_traffic_identities").query(Long.class).single())
        .isZero();

    jdbc.sql(
            """
            INSERT INTO teams (id, display_name)
            VALUES ('team-1', 'Team One'), ('team-2', 'Team Two')
            """)
        .update();
    jdbc.sql(
            """
            INSERT INTO package_owners (package_id, team_id, owner_order)
            VALUES (:packageId, 'team-1', 0)
            """)
        .param("packageId", packageId)
        .update();

    assertThatThrownBy(
            () ->
                jdbc.sql(
                        """
                        INSERT INTO lifecycle_events (
                            package_id, package_version_id, event_type, reason, effective_at)
                        VALUES (
                            :otherPackageId, :versionId, 'deprecated', 'ownership test', now())
                        """)
                    .param("otherPackageId", otherPackageId)
                    .param("versionId", versionId)
                    .update())
        .isInstanceOf(RuntimeException.class);
    assertThat(
            jdbc.sql(
                    """
                    INSERT INTO symbols (
                        package_version_id, kind, name, document_path,
                        default_value, is_required)
                    VALUES (
                        :versionId, 'dependency', 'aws', 'versions.tf',
                        '>= 5.0', true)
                    RETURNING kind
                    """)
                .param("versionId", versionId)
                .query(String.class)
                .single())
        .isEqualTo("dependency");
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        """
                        INSERT INTO symbols (
                            package_version_id, kind, name, document_path)
                        VALUES (:versionId, 'output', 'missing_path', NULL)
                        """)
                    .param("versionId", versionId)
                    .update())
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        """
                        INSERT INTO ingestion_events (
                            event_id, idempotency_key, event_type, schema_version,
                            status, attempts, correlation_id, payload,
                            last_attempt_at, completed_at)
                        VALUES (
                            'completed-without-digest',
                            'completed-without-digest',
                            'deployed',
                            1,
                            'completed',
                            1,
                            'completed-without-digest',
                            '{}'::jsonb,
                            now(),
                            now())
                        """)
                    .update())
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        """
                        INSERT INTO package_owners (package_id, team_id, owner_order)
                        VALUES (:packageId, 'team-2', -1)
                        """)
                    .param("packageId", packageId)
                    .update())
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        """
                        INSERT INTO reconciliation_runs (
                            mode, scope, status, discrepancies, repaired, completed_at)
                        VALUES ('repair', 'all', 'completed', 1, 2, now())
                        """)
                    .update())
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        """
                        INSERT INTO symbols (package_version_id, kind, name, document_path)
                        VALUES (:versionId, 'unknown', 'invalid', '/absolute.md')
                        """)
                    .param("versionId", versionId)
                    .update())
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        """
                        UPDATE package_versions
                           SET active = true, revoked = true
                         WHERE id = :versionId
                        """)
                    .param("versionId", versionId)
                    .update())
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void usesSemanticQueueIdentityAndPostgresNotificationWakeups() throws Exception {
    var publisher = new PostgresCatalogEventPublisher(jdbc, objectMapper);
    var original = event("transport-1", "correlation-1", OCCURRED_AT);
    var transportRetry = event("transport-2", "correlation-2", OCCURRED_AT);
    var sameTransportRetry = event("transport-1", "correlation-3", OCCURRED_AT);
    var reusedTransport = event("transport-1", "correlation-reused", OCCURRED_AT.plusSeconds(1));
    var later = event("transport-3", "correlation-3", OCCURRED_AT.plusSeconds(1));

    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(true);
      try (var statement = connection.createStatement()) {
        statement.execute("LISTEN registry_catalog_work");
      }

      var originalReceipt = publisher.publish(original);
      var notification = connection.unwrap(PGConnection.class).getNotifications(5_000);

      assertThat(notification).isNotNull().isNotEmpty();
      assertThat(notification[0].getParameter()).isEqualTo(originalReceipt.eventId());
      assertThat(publisher.publish(transportRetry).eventId()).isEqualTo(originalReceipt.eventId());
      assertThat(publisher.publish(sameTransportRetry).eventId())
          .isEqualTo(originalReceipt.eventId());
      assertThatThrownBy(() -> publisher.publish(reusedTransport))
          .isInstanceOf(CatalogEventIdentityCollisionException.class);
      assertThat(publisher.publish(later).eventId()).isNotEqualTo(originalReceipt.eventId());
    }

    assertThat(jdbc.sql("SELECT count(*) FROM catalog_event_queue").query(Long.class).single())
        .isEqualTo(2);
  }

  @Test
  void handlesAllTransportAndSemanticRetryCombinationsWithoutUniqueKeyFailures() {
    var events = new IngestionEventRepository(jdbc, objectMapper);
    var original = event("transport-1", "correlation-1", OCCURRED_AT);
    var sameTransportAndSemantic = event("transport-1", "correlation-2", OCCURRED_AT);
    var otherTransportSameSemantic = event("transport-2", "correlation-3", OCCURRED_AT);
    var sameTransportOtherSemantic =
        event("transport-1", "correlation-4", OCCURRED_AT.plusSeconds(1));

    assertThat(events.claim(original, CLAIM_TIMEOUT)).isTrue();
    assertThat(events.claim(sameTransportAndSemantic, CLAIM_TIMEOUT)).isFalse();
    assertThat(events.claim(otherTransportSameSemantic, CLAIM_TIMEOUT)).isFalse();
    assertThat(events.claim(sameTransportOtherSemantic, CLAIM_TIMEOUT)).isFalse();

    events.fail(original, new IllegalStateException("temporary Artifactory failure"));
    assertThat(events.claim(otherTransportSameSemantic, CLAIM_TIMEOUT)).isTrue();

    jdbc.sql(
            """
            UPDATE ingestion_events
               SET received_at = now() - interval '20 minutes',
                   last_attempt_at = now() - interval '10 minutes'
             WHERE idempotency_key = :idempotencyKey
            """)
        .param("idempotencyKey", original.idempotencyKey())
        .update();
    assertThat(events.claim(otherTransportSameSemantic, CLAIM_TIMEOUT)).isTrue();
    assertThat(
            jdbc.sql(
                    """
                    SELECT attempts
                      FROM ingestion_events
                     WHERE idempotency_key = :idempotencyKey
                    """)
                .param("idempotencyKey", original.idempotencyKey())
                .query(Integer.class)
                .single())
        .isEqualTo(3);
  }

  @Test
  void retriesStaleQueueClaimsAndPurgesExpiredTerminalRecords() {
    var publisher = new PostgresCatalogEventPublisher(jdbc, objectMapper);
    var queue = new CatalogEventQueueRepository(jdbc, objectMapper);
    var journal = new IngestionEventRepository(jdbc, objectMapper);
    var event = event("transport-retention", "correlation-retention", OCCURRED_AT);

    publisher.publish(event);
    var item = queue.claimOne().orElseThrow();
    queue.retryOrDeadLetter(item, new IllegalStateException("temporary"), 5);
    assertThat(
            jdbc.sql("SELECT status FROM catalog_event_queue WHERE id = :id")
                .param("id", item.id())
                .query(String.class)
                .single())
        .isEqualTo("retry");
    assertThat(
            jdbc.sql(
                    """
                    SELECT available_at > now()
                      FROM catalog_event_queue
                     WHERE id = :id
                    """)
                .param("id", item.id())
                .query(Boolean.class)
                .single())
        .isTrue();

    jdbc.sql("UPDATE catalog_event_queue SET available_at = now() WHERE id = :id")
        .param("id", item.id())
        .update();
    var retried = queue.claimOne().orElseThrow();
    jdbc.sql(
            """
            UPDATE catalog_event_queue
               SET created_at = now() - interval '20 minutes',
                   claimed_at = now() - interval '10 minutes'
             WHERE id = :id
            """)
        .param("id", retried.id())
        .update();
    assertThat(queue.recoverStaleClaims(CLAIM_TIMEOUT)).isEqualTo(1);

    jdbc.sql("UPDATE catalog_event_queue SET available_at = now() WHERE id = :id")
        .param("id", retried.id())
        .update();
    var terminal = queue.claimOne().orElseThrow();
    queue.complete(terminal);
    jdbc.sql(
            """
            UPDATE catalog_event_queue
               SET created_at = now() - interval '30 days',
                   available_at = now() - interval '30 days',
                   completed_at = now() - interval '20 days',
                   updated_at = now() - interval '20 days'
             WHERE id = :id
            """)
        .param("id", terminal.id())
        .update();

    assertThat(journal.claim(event, CLAIM_TIMEOUT)).isTrue();
    journal.complete(event, "sha256:" + "a".repeat(64));
    jdbc.sql(
            """
            UPDATE ingestion_events
               SET received_at = now() - interval '30 days',
                   last_attempt_at = now() - interval '20 days',
                   completed_at = now() - interval '20 days'
             WHERE idempotency_key = :idempotencyKey
            """)
        .param("idempotencyKey", event.idempotencyKey())
        .update();

    assertThat(queue.purgeTerminalEvents(Duration.ofDays(7), Duration.ofDays(90)).completed())
        .isEqualTo(1);
    assertThat(journal.purgeTerminalEvents(Duration.ofDays(7), Duration.ofDays(90)).completed())
        .isEqualTo(1);
  }

  @Test
  void deadLettersInvalidPayloadAndContinuesToTheNextQueueItem() {
    var queue = new CatalogEventQueueRepository(jdbc, objectMapper);
    jdbc.sql(
            """
            INSERT INTO catalog_event_queue (event_id, semantic_key, payload)
            VALUES (
                'invalid-runtime-payload',
                :semanticKey,
                '{"schemaVersion":1}'::jsonb)
            """)
        .param("semanticKey", "sha256:" + "c".repeat(64))
        .update();
    var valid = event("transport-after-invalid", "correlation-after-invalid", OCCURRED_AT);
    new PostgresCatalogEventPublisher(jdbc, objectMapper).publish(valid);

    assertThat(queue.claimOne().orElseThrow().event()).isEqualTo(valid);
    assertThat(
            jdbc.sql(
                    """
                    SELECT status, failure_code
                      FROM catalog_event_queue
                     WHERE event_id = 'invalid-runtime-payload'
                    """)
                .query(
                    (resultSet, rowNumber) ->
                        Map.entry(
                            resultSet.getString("status"), resultSet.getString("failure_code")))
                .single())
        .isEqualTo(Map.entry("dead_letter", "invalid_event_payload"));
  }

  @Test
  void staleClaimCannotCompleteAReplacementLease() {
    var queue = new CatalogEventQueueRepository(jdbc, objectMapper);
    var event = event("transport-stale-race", "correlation-stale-race", OCCURRED_AT);
    new PostgresCatalogEventPublisher(jdbc, objectMapper).publish(event);
    var abandoned = queue.claimOne().orElseThrow();
    jdbc.sql(
            """
            UPDATE catalog_event_queue
               SET created_at = now() - interval '20 minutes',
                   claimed_at = now() - interval '10 minutes'
             WHERE id = :id
            """)
        .param("id", abandoned.id())
        .update();

    assertThat(queue.recoverStaleClaims(CLAIM_TIMEOUT)).isEqualTo(1);
    var replacement = queue.claimOne().orElseThrow();

    assertThat(replacement.claimToken()).isNotEqualTo(abandoned.claimToken());
    assertThat(queue.complete(abandoned)).isFalse();
    assertThat(
            jdbc.sql("SELECT claim_token FROM catalog_event_queue WHERE id = :id")
                .param("id", replacement.id())
                .query(UUID.class)
                .single())
        .isEqualTo(replacement.claimToken());
    assertThat(queue.complete(replacement)).isTrue();
  }

  @Test
  void databaseRolesSeparateReadOnlyWebAndIndexerCapabilities() {
    jdbc.sql("ALTER ROLE registry_app PASSWORD 'registry-app-test'").update();
    jdbc.sql("ALTER ROLE registry_web PASSWORD 'registry-web-test'").update();
    jdbc.sql("ALTER ROLE registry_indexer PASSWORD 'registry-indexer-test'").update();
    var application = JdbcClient.create(dataSource("registry_app", "registry-app-test"));
    var web = JdbcClient.create(dataSource("registry_web", "registry-web-test"));
    var indexer = JdbcClient.create(dataSource("registry_indexer", "registry-indexer-test"));

    assertThat(
            application.sql("SELECT count(*) FROM registry_categories").query(Long.class).single())
        .isPositive();
    assertThatThrownBy(
            () ->
                application
                    .sql(
                        """
                        INSERT INTO registry_categories (slug, display_name, description)
                        VALUES ('application-write', 'Application Write', 'Must be rejected.')
                        """)
                    .update())
        .isInstanceOf(RuntimeException.class);

    assertThat(
            web.sql(
                    """
                    INSERT INTO audit_events (
                        occurred_at, actor_type, actor_id, action, resource_type,
                        resource_id, correlation_id)
                    VALUES (
                        now(), 'user', 'web-role-test', 'test.write', 'quality-gate',
                        'web-role', 'web-role-test')
                    """)
                .update())
        .isEqualTo(1);
    assertThatThrownBy(
            () ->
                web.sql(
                        """
                        UPDATE packages
                           SET description = 'Public API must not mutate catalog rows.'
                         WHERE id = :id
                        """)
                    .param("id", insertPackage("web-role-catalog-denial"))
                    .update())
        .isInstanceOf(RuntimeException.class);

    assertThat(
            indexer
                .sql(
                    """
                    INSERT INTO registry_categories (slug, display_name, description)
                    VALUES ('indexer-write', 'Indexer Write', 'Indexer-managed vocabulary.')
                    """)
                .update())
        .isEqualTo(1);
    assertThat(indexer.sql("DELETE FROM registry_categories WHERE slug = 'indexer-write'").update())
        .isEqualTo(1);
  }

  private static UUID insertPackage(String name, String... categories) {
    var packageId =
        jdbc.sql(
                """
            INSERT INTO packages (
                kind, namespace, name, title, description, source_address,
                visibility, risk_tier, verification, support_level, lifecycle)
            VALUES (
                'provider', 'hashicorp', :name, :title, 'Provider for integration tests',
                :sourceAddress, 'restricted', 'low', 'enterprise-verified',
                'supported', 'approved')
            RETURNING id
            """)
            .param("name", name)
            .param("title", name)
            .param("sourceAddress", "hashicorp/" + name)
            .query(UUID.class)
            .single();
    for (var category : categories) {
      jdbc.sql(
              """
              INSERT INTO package_categories (package_id, category_slug)
              VALUES (:packageId, :category)
              """)
          .param("packageId", packageId)
          .param("category", category)
          .update();
    }
    return packageId;
  }

  private static UUID insertVersion(UUID packageId, boolean revoked, boolean active) {
    return jdbc.sql(
            """
            INSERT INTO package_versions (
                package_id, version, package_digest, documentation_digest,
                documentation_root, artifact_repository, artifact_path,
                source_repository, source_commit, source_tag, terraform_constraint,
                published_at, revoked, active)
            VALUES (
                :packageId, '1.0.0', :packageDigest, :documentationDigest,
                'index.md', 'iac-provider-release-local', 'hashicorp/provider/1.0.0.zip',
                'https://example.test/provider', 'commit-1', 'v1.0.0', '>= 1.8',
                now(), :revoked, :active)
            RETURNING id
            """)
        .param("packageId", packageId)
        .param("packageDigest", "sha256:" + "a".repeat(64))
        .param("documentationDigest", "sha256:" + "b".repeat(64))
        .param("revoked", revoked)
        .param("active", active)
        .query(UUID.class)
        .single();
  }

  private static CatalogArtifactChanged event(
      String eventId, String correlationId, Instant occurredAt) {
    return new CatalogArtifactChanged(
        1,
        eventId,
        CatalogArtifactChanged.Action.PROPERTIES_CHANGED,
        "jfrog.example",
        "registry-events",
        "iac-catalog-release-local",
        "v1/providers/hashicorp/null/3.2.4/catalog-manifest.json",
        occurredAt,
        correlationId,
        Map.of("apm.id", "APM0000001"));
  }

  private static PGSimpleDataSource dataSource(String username, String password) {
    var configured = new PGSimpleDataSource();
    configured.setUrl(POSTGRESQL.getJdbcUrl());
    configured.setUser(username);
    configured.setPassword(password);
    return configured;
  }
}
