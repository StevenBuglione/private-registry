package com.stevenbuglione.registry.eventing;

import java.time.Instant;
import java.util.Map;

public record CatalogArtifactChanged(
    int schemaVersion,
    String eventId,
    Action action,
    String origin,
    String subscriptionId,
    String repository,
    String path,
    Instant occurredAt,
    String correlationId,
    Map<String, String> properties) {

  public CatalogArtifactChanged {
    if (schemaVersion != 1) {
      throw new IllegalArgumentException("Unsupported catalog artifact event schema");
    }
    eventId = requireText(eventId, "eventId");
    java.util.Objects.requireNonNull(action, "action");
    origin = requireText(origin, "origin");
    subscriptionId = requireText(subscriptionId, "subscriptionId");
    repository = requireText(repository, "repository");
    path = requireSafePath(path);
    java.util.Objects.requireNonNull(occurredAt, "occurredAt");
    correlationId = requireText(correlationId, "correlationId");
    properties = properties == null ? Map.of() : Map.copyOf(properties);
  }

  public String idempotencyKey() {
    return repository + ":" + path + ":" + eventId;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value;
  }

  private static String requireSafePath(String value) {
    var path = requireText(value, "path");
    if (path.startsWith("/") || path.contains("..") || path.contains("\\")) {
      throw new IllegalArgumentException("Unsafe artifact path");
    }
    return path;
  }

  public enum Action {
    DEPLOYED,
    PROPERTIES_CHANGED,
    DELETED,
    MOVED,
    COPIED
  }
}
