package com.stevenbuglione.registry.eventing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(prefix = "registry.eventing", name = "enabled", havingValue = "true")
public class EventBridgeCatalogEventPublisher implements CatalogEventPublisher {

  private final EventBridgeClient eventBridge;
  private final ObjectMapper objectMapper;
  private final EventingProperties properties;

  public EventBridgeCatalogEventPublisher(
      EventBridgeClient eventBridge, ObjectMapper objectMapper, EventingProperties properties) {
    this.eventBridge = eventBridge;
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  @Override
  public PublishReceipt publish(CatalogArtifactChanged event) {
    var entry =
        PutEventsRequestEntry.builder()
            .eventBusName(properties.eventBusName())
            .source("registry.jfrog")
            .detailType("CatalogArtifactChanged")
            .time(event.occurredAt())
            .detail(json(event))
            .build();
    var response = eventBridge.putEvents(PutEventsRequest.builder().entries(entry).build());
    if (response.failedEntryCount() != null && response.failedEntryCount() > 0) {
      var failure =
          response.entries().stream()
              .filter(result -> result.errorCode() != null)
              .findFirst()
              .map(result -> result.errorCode() + ": " + result.errorMessage())
              .orElse("unknown EventBridge error");
      throw new EventPublicationException(failure);
    }
    var publishedId = response.entries().getFirst().eventId();
    return new PublishReceipt(publishedId == null ? event.eventId() : publishedId);
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

    EventPublicationException(String message) {
      super(message);
    }

    EventPublicationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
