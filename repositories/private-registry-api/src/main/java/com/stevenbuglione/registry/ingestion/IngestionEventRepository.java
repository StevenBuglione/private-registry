package com.stevenbuglione.registry.ingestion;

import com.stevenbuglione.registry.eventing.CatalogArtifactChanged;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class IngestionEventRepository {

  private final JdbcClient jdbc;
  private final ObjectMapper objectMapper;

  public IngestionEventRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public boolean claim(CatalogArtifactChanged event) {
    return jdbc.sql(
                """
                        INSERT INTO ingestion_events (
                            event_id, idempotency_key, event_type, schema_version, package_digest,
                            status, attempts, correlation_id, source_repository, source_path,
                            payload, last_attempt_at
                        ) VALUES (
                            :eventId, :idempotencyKey, :eventType, :schemaVersion, NULL,
                            'processing', 1, :correlationId, :repository, :path,
                            CAST(:payload AS jsonb), now()
                        )
                        ON CONFLICT (event_id) DO UPDATE SET
                            status = 'processing',
                            attempts = ingestion_events.attempts + 1,
                            last_attempt_at = now(),
                            error_code = NULL,
                            error_detail = NULL
                        WHERE ingestion_events.status = 'failed'
                        """)
            .param("eventId", event.eventId())
            .param("idempotencyKey", event.idempotencyKey())
            .param("eventType", event.action().name().toLowerCase(java.util.Locale.ROOT))
            .param("schemaVersion", event.schemaVersion())
            .param("correlationId", event.correlationId())
            .param("repository", event.repository())
            .param("path", event.path())
            .param("payload", json(event))
            .update()
        == 1;
  }

  public void complete(CatalogArtifactChanged event, String digest) {
    jdbc.sql(
            """
                        UPDATE ingestion_events
                           SET status = 'completed', package_digest = :digest,
                               completed_at = now(), error_code = NULL, error_detail = NULL
                         WHERE event_id = :eventId
                        """)
        .param("eventId", event.eventId())
        .param("digest", digest)
        .update();
  }

  public void fail(CatalogArtifactChanged event, RuntimeException failure) {
    jdbc.sql(
            """
                        UPDATE ingestion_events
                           SET status = 'failed', error_code = :code, error_detail = :detail,
                               last_attempt_at = now()
                         WHERE event_id = :eventId
                        """)
        .param("eventId", event.eventId())
        .param("code", failure.getClass().getSimpleName())
        .param("detail", truncate(failure.getMessage()))
        .update();
  }

  public void quarantine(CatalogArtifactChanged event, QuarantineException failure) {
    jdbc.sql(
            """
                        UPDATE ingestion_events
                           SET status = 'quarantined', error_code = :code, error_detail = :detail,
                               completed_at = now(), last_attempt_at = now()
                         WHERE event_id = :eventId
                        """)
        .param("eventId", event.eventId())
        .param("code", failure.code())
        .param("detail", truncate(failure.getMessage()))
        .update();
    jdbc.sql(
            """
                        INSERT INTO ingestion_quarantine (
                            event_id, source_repository, source_path, reason_code, reason_detail, payload
                        ) VALUES (
                            :eventId, :repository, :path, :code, :detail, CAST(:payload AS jsonb)
                        ) ON CONFLICT (event_id, reason_code) DO NOTHING
                        """)
        .param("eventId", event.eventId())
        .param("repository", event.repository())
        .param("path", event.path())
        .param("code", failure.code())
        .param("detail", truncate(failure.getMessage()))
        .param("payload", json(event))
        .update();
  }

  private String json(CatalogArtifactChanged event) {
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to serialize ingestion event", exception);
    }
  }

  private static String truncate(@Nullable String message) {
    if (message == null) {
      return "Unspecified failure";
    }
    return message.substring(0, Math.min(message.length(), 4_000));
  }
}
