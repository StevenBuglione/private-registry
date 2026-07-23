package com.stevenbuglione.registry.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class AuditLogService {

  private static final int MAXIMUM_PAGE_SIZE = 100;

  private final JdbcClient jdbc;
  private final ObjectMapper objectMapper;

  public AuditLogService(JdbcClient jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void record(AuditEntry entry) {
    jdbc.sql(
            """
            INSERT INTO audit_events (
                occurred_at,
                actor_type,
                actor_id,
                action,
                resource_type,
                resource_id,
                correlation_id,
                detail)
            VALUES (
                now(),
                :actorType,
                :actorId,
                :action,
                :resourceType,
                :resourceId,
                :correlationId,
                CAST(:detail AS jsonb))
            """)
        .param("actorType", requireText(entry.actorType(), "actorType"))
        .param("actorId", requireText(entry.actorId(), "actorId"))
        .param("action", requireText(entry.action(), "action"))
        .param("resourceType", requireText(entry.resourceType(), "resourceType"))
        .param("resourceId", requireText(entry.resourceId(), "resourceId"))
        .param("correlationId", correlationId())
        .param("detail", json(entry.detail()))
        .update();
  }

  public List<AuditEvent> recent(int requestedLimit, @Nullable Instant before) {
    var limit = Math.max(1, Math.min(requestedLimit, MAXIMUM_PAGE_SIZE));
    if (before != null) {
      return jdbc.sql(
              """
              SELECT id,
                     occurred_at,
                     actor_type,
                     actor_id,
                     action,
                     resource_type,
                     resource_id,
                     correlation_id,
                     detail::text AS detail
                FROM audit_events
               WHERE occurred_at < :before
               ORDER BY occurred_at DESC, id DESC
               LIMIT :limit
              """)
          .param("before", Timestamp.from(before))
          .param("limit", limit)
          .query(this::map)
          .list();
    }
    return jdbc.sql(
            """
            SELECT id,
                   occurred_at,
                   actor_type,
                   actor_id,
                   action,
                   resource_type,
                   resource_id,
                   correlation_id,
                   detail::text AS detail
              FROM audit_events
             ORDER BY occurred_at DESC, id DESC
             LIMIT :limit
            """)
        .param("limit", limit)
        .query(this::map)
        .list();
  }

  private AuditEvent map(ResultSet resultSet, int rowNumber) throws SQLException {
    return new AuditEvent(
        resultSet.getObject("id", UUID.class),
        resultSet.getTimestamp("occurred_at").toInstant(),
        resultSet.getString("actor_type"),
        resultSet.getString("actor_id"),
        resultSet.getString("action"),
        resultSet.getString("resource_type"),
        resultSet.getString("resource_id"),
        resultSet.getString("correlation_id"),
        parseDetail(resultSet.getString("detail")));
  }

  private JsonNode parseDetail(String detail) {
    try {
      return objectMapper.readTree(detail);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Stored audit detail is not valid JSON", exception);
    }
  }

  private String json(Map<String, Object> detail) {
    try {
      return objectMapper.writeValueAsString(Map.copyOf(detail));
    } catch (JacksonException exception) {
      throw new IllegalArgumentException("Audit detail cannot be serialized", exception);
    }
  }

  private static String correlationId() {
    var requestId = MDC.get("request_id");
    return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
  }

  private static String requireText(String value, String field) {
    if (value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value;
  }

  public record AuditEvent(
      UUID id,
      Instant occurredAt,
      String actorType,
      String actorId,
      String action,
      String resourceType,
      String resourceId,
      String correlationId,
      JsonNode detail) {}

  public record AuditEntry(
      String actorType,
      String actorId,
      String action,
      String resourceType,
      String resourceId,
      Map<String, Object> detail) {

    public AuditEntry {
      detail = Map.copyOf(detail);
    }
  }
}
