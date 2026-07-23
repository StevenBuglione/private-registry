package com.stevenbuglione.registry.eventing;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(prefix = "registry.eventing", name = "enabled", havingValue = "true")
public class PostgresCatalogEventPublisher implements CatalogEventPublisher {

  private final JdbcClient jdbc;
  private final ObjectMapper objectMapper;

  public PostgresCatalogEventPublisher(JdbcClient jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public PublishReceipt publish(CatalogArtifactChanged event) {
    var semanticKey = event.semanticKey();
    var publicationId =
        jdbc.sql(
                """
                INSERT INTO catalog_event_queue (event_id, semantic_key, payload)
                VALUES (:eventId, :semanticKey, CAST(:payload AS jsonb))
                ON CONFLICT DO NOTHING
                RETURNING id
                """)
            .param("eventId", event.eventId())
            .param("semanticKey", semanticKey)
            .param("payload", json(event))
            .query(UUID.class)
            .optional()
            .orElseGet(() -> existingPublication(event.eventId(), semanticKey));
    return new PublishReceipt(publicationId.toString());
  }

  private UUID existingPublication(String eventId, String semanticKey) {
    var transportPublication =
        jdbc.sql(
                """
                SELECT id, semantic_key
                  FROM catalog_event_queue
                 WHERE event_id = :eventId
                """)
            .param("eventId", eventId)
            .query(
                (resultSet, rowNumber) ->
                    new ExistingPublication(
                        resultSet.getObject("id", UUID.class), resultSet.getString("semantic_key")))
            .optional();
    if (transportPublication.isPresent()) {
      var existing = transportPublication.orElseThrow();
      if (!existing.semanticKey().equals(semanticKey)) {
        throw new CatalogEventIdentityCollisionException(eventId);
      }
      return existing.id();
    }
    return jdbc.sql(
            """
            SELECT id
              FROM catalog_event_queue
             WHERE semantic_key = :semanticKey
            """)
        .param("semanticKey", semanticKey)
        .query(UUID.class)
        .single();
  }

  private String json(CatalogArtifactChanged event) {
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JacksonException exception) {
      throw new EventPublicationException("Unable to serialize catalog event", exception);
    }
  }

  public static final class EventPublicationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    EventPublicationException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private record ExistingPublication(UUID id, String semanticKey) {}
}
