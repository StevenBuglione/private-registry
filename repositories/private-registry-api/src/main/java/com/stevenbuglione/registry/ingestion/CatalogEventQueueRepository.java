package com.stevenbuglione.registry.ingestion;

import com.stevenbuglione.registry.eventing.CatalogArtifactChanged;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
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
  public Optional<QueueItem> claimOne() {
    while (true) {
      var candidate = claimRaw();
      if (candidate.isEmpty()) {
        return Optional.empty();
      }
      var decoded = decode(candidate.orElseThrow());
      if (decoded.isPresent()) {
        return decoded;
      }
    }
  }

  public boolean complete(QueueItem item) {
    return jdbc.sql(
                """
            UPDATE catalog_event_queue
               SET status = 'completed',
                   completed_at = now(),
                   claimed_at = NULL,
                   claim_token = NULL,
                   failure_code = NULL,
                   failure_detail = NULL,
                   updated_at = now()
             WHERE id = :id
               AND status = 'processing'
               AND claim_token = :claimToken
            """)
            .param("id", item.id())
            .param("claimToken", item.claimToken())
            .update()
        == 1;
  }

  public boolean retryOrDeadLetter(QueueItem item, RuntimeException failure, int maximumAttempts) {
    var deadLetter = item.attempts() >= maximumAttempts || failure instanceof QuarantineException;
    return jdbc.sql(
                """
            UPDATE catalog_event_queue
               SET status = :status,
                   available_at = CASE
                       WHEN :deadLetter THEN available_at
                       ELSE now() + (LEAST(attempts, 10) * interval '5 seconds')
                   END,
                   claimed_at = NULL,
                   claim_token = NULL,
                   completed_at = CASE WHEN :deadLetter THEN now() ELSE NULL END,
                   failure_code = :failureCode,
                   failure_detail = :failureDetail,
                   updated_at = now()
             WHERE id = :id
               AND status = 'processing'
               AND claim_token = :claimToken
            """)
            .param("id", item.id())
            .param("claimToken", item.claimToken())
            .param("status", deadLetter ? "dead_letter" : "retry")
            .param("deadLetter", deadLetter)
            .param("failureCode", failureCode(failure))
            .param("failureDetail", truncate(failure.getMessage()))
            .update()
        == 1;
  }

  public boolean deadLetterQuarantined(QueueItem item) {
    return retryOrDeadLetter(
        item,
        new QuarantineException("ingestion_quarantined", "Catalog ingestion quarantined the event"),
        item.attempts());
  }

  public int recoverStaleClaims(Duration claimTimeout) {
    return jdbc.sql(
            """
            UPDATE catalog_event_queue
               SET status = 'retry',
                   available_at = now(),
                   claimed_at = NULL,
                   claim_token = NULL,
                   failure_code = 'stale_claim_recovered',
                   failure_detail = 'Recovered stale processing claim',
                   updated_at = now()
             WHERE status = 'processing'
               AND claimed_at < :claimedBefore
            """)
        .param("claimedBefore", Timestamp.from(Instant.now().minus(claimTimeout)))
        .update();
  }

  public RetentionResult purgeTerminalEvents(
      Duration completedRetention, Duration deadLetterRetention) {
    var completed =
        jdbc.sql(
                """
                DELETE FROM catalog_event_queue
                 WHERE status = 'completed'
                   AND completed_at < :completedBefore
                """)
            .param("completedBefore", Timestamp.from(Instant.now().minus(completedRetention)))
            .update();
    var deadLetters =
        jdbc.sql(
                """
                DELETE FROM catalog_event_queue
                 WHERE status = 'dead_letter'
                   AND updated_at < :deadLetterBefore
                """)
            .param("deadLetterBefore", Timestamp.from(Instant.now().minus(deadLetterRetention)))
            .update();
    return new RetentionResult(completed, deadLetters);
  }

  private Optional<RawQueueItem> claimRaw() {
    return jdbc.sql(
            """
            WITH candidate AS (
                SELECT id
                  FROM catalog_event_queue
                 WHERE status IN ('queued', 'retry')
                   AND available_at <= now()
                 ORDER BY created_at, id
                 FOR UPDATE SKIP LOCKED
                 LIMIT 1)
            UPDATE catalog_event_queue queued
               SET status = 'processing',
                   attempts = attempts + 1,
                   claimed_at = now(),
                   claim_token = gen_random_uuid(),
                   updated_at = now()
              FROM candidate
             WHERE queued.id = candidate.id
            RETURNING queued.id,
                      queued.payload::text AS payload,
                      queued.attempts,
                      queued.claim_token
            """)
        .query(
            (resultSet, rowNumber) ->
                new RawQueueItem(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getString("payload"),
                    resultSet.getInt("attempts"),
                    resultSet.getObject("claim_token", UUID.class)))
        .optional();
  }

  private Optional<QueueItem> decode(RawQueueItem raw) {
    try {
      var event =
          Objects.requireNonNull(
              objectMapper.readValue(raw.payload(), CatalogArtifactChanged.class),
              "Catalog event queue payload decoded to null");
      return Optional.of(new QueueItem(raw.id(), event, raw.attempts(), raw.claimToken()));
    } catch (RuntimeException exception) {
      deadLetterInvalidPayload(raw, exception);
      return Optional.empty();
    }
  }

  private void deadLetterInvalidPayload(RawQueueItem raw, Exception failure) {
    jdbc.sql(
            """
            UPDATE catalog_event_queue
               SET status = 'dead_letter',
                   claimed_at = NULL,
                   claim_token = NULL,
                   completed_at = now(),
                   failure_code = 'invalid_event_payload',
                   failure_detail = :failureDetail,
                   updated_at = now()
             WHERE id = :id
               AND status = 'processing'
               AND claim_token = :claimToken
            """)
        .param("id", raw.id())
        .param("claimToken", raw.claimToken())
        .param("failureDetail", truncate(failure.getMessage()))
        .update();
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

  public record QueueItem(UUID id, CatalogArtifactChanged event, int attempts, UUID claimToken) {}

  public record RetentionResult(int completed, int deadLetters) {}

  private record RawQueueItem(UUID id, String payload, int attempts, UUID claimToken) {}
}
