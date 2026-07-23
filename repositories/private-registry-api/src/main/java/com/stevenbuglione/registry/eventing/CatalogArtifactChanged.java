package com.stevenbuglione.registry.eventing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
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
    return semanticKey();
  }

  public String semanticKey() {
    var canonical = new StringBuilder();
    appendCanonical(canonical, Integer.toString(schemaVersion));
    appendCanonical(canonical, action.name());
    appendCanonical(canonical, repository);
    appendCanonical(canonical, path);
    appendCanonical(canonical, occurredAt.toString());
    properties.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              appendCanonical(canonical, entry.getKey());
              appendCanonical(canonical, entry.getValue());
            });
    try {
      var digest =
          MessageDigest.getInstance("SHA-256")
              .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
      return "sha256:" + HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static void appendCanonical(StringBuilder target, String value) {
    target.append(value.length()).append(':').append(value).append(';');
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
