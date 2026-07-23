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
    var publicationId =
        jdbc.sql(
                """
                INSERT INTO catalog_event_queue (event_id, payload)
                VALUES (:eventId, CAST(:payload AS jsonb))
                ON CONFLICT (event_id) DO UPDATE
                    SET event_id = EXCLUDED.event_id
                RETURNING id
                """)
            .param("eventId", event.eventId())
            .param("payload", json(event))
            .query(UUID.class)
            .single();
    jdbc.sql("SELECT pg_notify('registry_catalog_work', :eventId)")
        .param("eventId", event.eventId())
        .query(String.class)
        .optional();
    return new PublishReceipt(publicationId.toString());
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
}
