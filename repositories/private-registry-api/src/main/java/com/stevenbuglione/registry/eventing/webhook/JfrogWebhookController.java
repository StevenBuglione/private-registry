package com.stevenbuglione.registry.eventing.webhook;

import com.stevenbuglione.registry.eventing.CatalogEventPublisher;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/webhooks/jfrog")
@ConditionalOnProperty(prefix = "registry.eventing.webhook", name = "enabled", havingValue = "true")
public class JfrogWebhookController {

  private final JfrogWebhookProperties properties;
  private final JfrogWebhookSignatureVerifier signatureVerifier;
  private final JfrogWebhookParser parser;
  private final CatalogEventPublisher publisher;

  public JfrogWebhookController(
      JfrogWebhookProperties properties,
      JfrogWebhookSignatureVerifier signatureVerifier,
      JfrogWebhookParser parser,
      CatalogEventPublisher publisher) {
    this.properties = properties;
    this.signatureVerifier = signatureVerifier;
    this.parser = parser;
    this.publisher = publisher;
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, String>> receive(
      @RequestBody byte[] payload,
      @RequestHeader("X-JFrog-Signature") String signature,
      @RequestHeader("X-JFrog-Origin") String origin,
      @RequestHeader("X-JFrog-Subscription-Id") String subscriptionId,
      @RequestHeader(value = "X-JFrog-Event-Id", required = false) @Nullable String eventId,
      @RequestHeader(value = "X-Correlation-Id", required = false) @Nullable String correlationId) {
    validateEnvelope(payload, signature, origin, subscriptionId);
    var headers =
        new JfrogWebhookParser.WebhookHeaders(
            eventId,
            origin,
            subscriptionId,
            correlationId == null || correlationId.isBlank() ? eventId : correlationId);
    var event = parser.parse(payload, headers);
    if (!properties.allowedRepositories().contains(event.repository())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repository is not allowed");
    }
    if (properties.allowedPathPrefixes().stream().noneMatch(event.path()::startsWith)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Artifact path is not allowed");
    }
    var receipt = publisher.publish(event);
    return ResponseEntity.accepted()
        .body(Map.of("event_id", event.eventId(), "publication_id", receipt.eventId()));
  }

  private void validateEnvelope(
      byte[] payload, String signature, String origin, String subscriptionId) {
    if (payload.length == 0 || payload.length > properties.maximumPayloadBytes()) {
      throw new ResponseStatusException(
          org.springframework.http.HttpStatusCode.valueOf(413), "Webhook payload is too large");
    }
    if (properties.secret().isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Webhook signing secret is not configured");
    }
    if (properties.allowedOrigins().isEmpty() || properties.subscriptionId().isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Webhook origin and subscription allowlists are not configured");
    }
    if (!properties.allowedOrigins().contains(origin)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook origin is not allowed");
    }
    if (!properties.subscriptionId().equals(subscriptionId)) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Webhook subscription is not allowed");
    }
    if (!signatureVerifier.isValid(payload, signature, properties.secret())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook signature is invalid");
    }
  }
}
