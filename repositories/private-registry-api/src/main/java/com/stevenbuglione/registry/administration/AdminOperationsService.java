package com.stevenbuglione.registry.administration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class AdminOperationsService {

  private static final int MAXIMUM_PAGE_SIZE = 100;

  private final JdbcClient jdbc;

  public AdminOperationsService(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public List<OperationalEvent> recent(int requestedLimit) {
    var limit = Math.max(1, Math.min(requestedLimit, MAXIMUM_PAGE_SIZE));
    return jdbc.sql(
            """
            SELECT source,
                   event_id,
                   status,
                   title,
                   detail,
                   repository,
                   path,
                   correlation_id,
                   occurred_at
              FROM (
                  SELECT 'ingestion' AS source,
                         event_id,
                         status,
                         event_type AS title,
                         COALESCE(error_code || ': ' || error_detail, 'Artifact event processed') AS detail,
                         source_repository AS repository,
                         source_path AS path,
                         correlation_id,
                         received_at AS occurred_at
                    FROM ingestion_events
                  UNION ALL
                  SELECT 'queue' AS source,
                         event_id,
                         status,
                         'Queue delivery' AS title,
                         COALESCE(failure_code || ': ' || failure_detail, 'Event awaiting processing') AS detail,
                         payload ->> 'repository' AS repository,
                         payload ->> 'path' AS path,
                         COALESCE(payload ->> 'correlationId', payload ->> 'correlation_id', event_id)
                             AS correlation_id,
                         updated_at AS occurred_at
                    FROM catalog_event_queue
                   WHERE status IN ('processing', 'retry', 'dead_letter')
                  UNION ALL
                  SELECT 'reconciliation' AS source,
                         id::text AS event_id,
                         status,
                         mode || ' reconciliation' AS title,
                         scope || '; discrepancies=' || discrepancies || '; repaired=' || repaired AS detail,
                         NULL AS repository,
                         NULL AS path,
                         id::text AS correlation_id,
                         started_at AS occurred_at
                    FROM reconciliation_runs) operational_events
             ORDER BY occurred_at DESC
             LIMIT :limit
            """)
        .param("limit", limit)
        .query(this::map)
        .list();
  }

  private OperationalEvent map(ResultSet resultSet, int rowNumber) throws SQLException {
    return new OperationalEvent(
        resultSet.getString("source"),
        resultSet.getString("event_id"),
        resultSet.getString("status"),
        resultSet.getString("title"),
        resultSet.getString("detail"),
        resultSet.getString("repository"),
        resultSet.getString("path"),
        resultSet.getString("correlation_id"),
        resultSet.getTimestamp("occurred_at").toInstant());
  }

  public record OperationalEvent(
      String source,
      String eventId,
      String status,
      String title,
      String detail,
      @Nullable String repository,
      @Nullable String path,
      String correlationId,
      Instant occurredAt) {}
}
