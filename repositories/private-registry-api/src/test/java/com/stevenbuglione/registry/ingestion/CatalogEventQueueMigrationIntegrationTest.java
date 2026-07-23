package com.stevenbuglione.registry.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class CatalogEventQueueMigrationIntegrationTest {

  @Container
  private static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer("postgres:16-alpine")
          .withDatabaseName("registry_queue_migration_test")
          .withUsername("registry")
          .withPassword("registry");

  @Test
  void v119DeadLettersMalformedLegacyPayloadsWithoutDiscardingCanonicalWork() {
    var dataSource = dataSource();
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target("118")
        .cleanDisabled(true)
        .load()
        .migrate();
    var jdbc = JdbcClient.create(dataSource);
    jdbc.sql(
            """
            INSERT INTO catalog_event_queue (event_id, payload, status, claimed_at)
            VALUES
                ('legacy-scalar', '"legacy"'::jsonb, 'queued', NULL),
                ('legacy-incomplete', '{"schema_version":1}'::jsonb, 'processing', now()),
                (
                    'legacy-canonical',
                    '{
                        "schema_version": 1,
                        "event_id": "legacy-canonical",
                        "action": "DEPLOYED",
                        "origin": "jfrog.example",
                        "subscription_id": "registry-events",
                        "repository": "iac-catalog-release-local",
                        "path": "v1/providers/hashicorp/null/3.2.4/catalog-manifest.json",
                        "occurred_at": "2026-07-23T12:00:00Z",
                        "correlation_id": "legacy-canonical",
                        "properties": {}
                    }'::jsonb,
                    'queued',
                    NULL
                )
            """)
        .update();

    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .cleanDisabled(true)
        .load()
        .migrate();

    assertThat(
            jdbc.sql(
                    """
                    SELECT event_id
                      FROM catalog_event_queue
                     WHERE status = 'dead_letter'
                       AND completed_at IS NOT NULL
                       AND failure_code = 'legacy_payload_invalid'
                     ORDER BY event_id
                    """)
                .query(String.class)
                .list())
        .isEqualTo(List.of("legacy-incomplete", "legacy-scalar"));
    assertThat(
            jdbc.sql(
                    """
                    SELECT status
                      FROM catalog_event_queue
                     WHERE event_id = 'legacy-canonical'
                       AND claim_token IS NULL
                    """)
                .query(String.class)
                .single())
        .isEqualTo("queued");
  }

  private static PGSimpleDataSource dataSource() {
    var configured = new PGSimpleDataSource();
    configured.setUrl(POSTGRESQL.getJdbcUrl());
    configured.setUser(POSTGRESQL.getUsername());
    configured.setPassword(POSTGRESQL.getPassword());
    return configured;
  }
}
