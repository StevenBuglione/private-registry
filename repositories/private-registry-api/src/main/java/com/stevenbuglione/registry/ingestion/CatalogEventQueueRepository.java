package com.stevenbuglione.registry.ingestion;

import com.stevenbuglione.registry.eventing.CatalogArtifactChanged;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class CatalogEventQueueRepository {

  private final JdbcClient jdbc;
  private final ObjectMapper objectMapper;

  public CatalogEventQueueRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public List<QueueItem> claim(int limit) {
    return jdbc.sql(
            """
            WITH candidates AS (
                SELECT id
                  FROM catalog_event_queue
                 WHERE status IN ('queued', 'retry')
                   AND available_at <= now()
                 ORDER BY created_at
                 FOR UPDATE SKIP LOCKED
                 LIMIT :limit)
            UPDATE catalog_event_queue queued
               SET status = 'processing',
                   attempts = attempts + 1,
                   claimed_at = now(),
                   updated_at = now()
              FROM candidates
             WHERE queued.id = candidates.id
            RETURNING queued.id,
                      queued.payload::text AS payload,
                      queued.attempts
            """)
        .param("limit", limit)
        .query(this::map)
        .list();
  }

  public void complete(QueueItem item) {
    jdbc.sql(
            """
            UPDATE catalog_event_queue
               SET status = 'completed',
                   completed_at = now(),
                   failure_code = NULL,
                   failure_detail = NULL,
                   updated_at = now()
             WHERE id = :id AND status = 'processing'
            """)
        .param("id", item.id())
        .update();
  }

  public void retryOrDeadLetter(QueueItem item, RuntimeException failure, int maximumAttempts) {
    var deadLetter = item.attempts() >= maximumAttempts || failure instanceof QuarantineException;
    jdbc.sql(
            """
            UPDATE catalog_event_queue
               SET status = :status,
                   available_at = CASE
                       WHEN :deadLetter THEN available_at
                       ELSE now() + (LEAST(attempts, 10) * interval '5 seconds')
                   END,
                   completed_at = CASE WHEN :deadLetter THEN now() ELSE NULL END,
                   failure_code = :failureCode,
                   failure_detail = :failureDetail,
                   updated_at = now()
             WHERE id = :id AND status = 'processing'
            """)
        .param("id", item.id())
        .param("status", deadLetter ? "dead_letter" : "retry")
        .param("deadLetter", deadLetter)
        .param("failureCode", failureCode(failure))
        .param("failureDetail", truncate(failure.getMessage()))
        .update();
  }

  public void deadLetterQuarantined(QueueItem item) {
    retryOrDeadLetter(
        item,
        new QuarantineException("ingestion_quarantined", "Catalog ingestion quarantined the event"),
        item.attempts());
  }

  public void recoverStaleClaims(Duration claimTimeout) {
    jdbc.sql(
            """
            UPDATE catalog_event_queue
               SET status = 'retry',
                   available_at = now(),
                   failure_code = 'stale_claim_recovered',
                   failure_detail = 'Recovered stale processing claim',
                   updated_at = now()
             WHERE status = 'processing'
               AND claimed_at < :claimedBefore
            """)
        .param("claimedBefore", Timestamp.from(Instant.now().minus(claimTimeout)))
        .update();
  }

  private QueueItem map(ResultSet resultSet, int rowNumber) throws SQLException {
    try {
      return new QueueItem(
          resultSet.getObject("id", UUID.class),
          objectMapper.readValue(resultSet.getString("payload"), CatalogArtifactChanged.class),
          resultSet.getInt("attempts"));
    } catch (JacksonException exception) {
      throw new SQLException("Invalid catalog event queue payload", exception);
    }
  }

  private static String failureCode(RuntimeException failure) {
    return failure instanceof QuarantineException quarantine
        ? quarantine.code()
        : failure.getClass().getSimpleName();
  }

  private static String truncate(@Nullable String message) {
    if (message == null) {
      return "Unspecified catalog event processing failure";
    }
    return message.substring(0, Math.min(message.length(), 4_000));
  }

  public record QueueItem(UUID id, CatalogArtifactChanged event, int attempts) {}
}
