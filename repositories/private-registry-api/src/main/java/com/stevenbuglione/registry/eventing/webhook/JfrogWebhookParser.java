package com.stevenbuglione.registry.eventing.webhook;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.stevenbuglione.registry.eventing.CatalogArtifactChanged;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class JfrogWebhookParser {

    private final ObjectMapper objectMapper;

    public JfrogWebhookParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    CatalogArtifactChanged parse(byte[] payload, WebhookHeaders headers) {
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Invalid JFrog webhook JSON", exception);
        }
        var data = root.path("data");
        var eventId = firstText(headers.eventId(), root.path("id"), root.path("event_id"));
        var resolvedEventId = eventId == null ? deterministicEventId(payload, headers) : eventId;
        var repository = firstText(null, data.path("repo_key"), data.path("repository"), root.path("repo_key"));
        var path = firstText(null, data.path("path"), data.path("item_path"), root.path("path"));
        var eventType = firstText(null, root.path("event_type"), root.path("type"), data.path("event_type"));
        var occurredAt = firstText(null, root.path("occurred_at"), root.path("timestamp"), data.path("timestamp"));
        var properties = new LinkedHashMap<String, String>();
        data.path("properties").forEachEntry((key, value) -> properties.put(
                key, value.isValueNode() ? value.asString() : value.toString()));
        return new CatalogArtifactChanged(
                1,
                resolvedEventId,
                action(eventType),
                headers.origin(),
                headers.subscriptionId(),
                repository,
                path,
                instant(occurredAt),
                firstText(
                        headers.correlationId() == null ? resolvedEventId : headers.correlationId(),
                        root.path("correlation_id")),
                properties);
    }

    private static CatalogArtifactChanged.Action action(String eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("JFrog webhook event type is required");
        }
        var normalized = eventType.toLowerCase(Locale.ROOT);
        if (normalized.contains("propert")) {
            return CatalogArtifactChanged.Action.PROPERTIES_CHANGED;
        }
        if (normalized.contains("delete")) {
            return CatalogArtifactChanged.Action.DELETED;
        }
        if (normalized.contains("move")) {
            return CatalogArtifactChanged.Action.MOVED;
        }
        if (normalized.contains("copy")) {
            return CatalogArtifactChanged.Action.COPIED;
        }
        if (normalized.contains("deploy") || normalized.contains("upload") || normalized.contains("create")) {
            return CatalogArtifactChanged.Action.DEPLOYED;
        }
        throw new IllegalArgumentException("Unsupported JFrog webhook event type");
    }

    private static Instant instant(String value) {
        if (value == null) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid JFrog event timestamp", exception);
        }
    }

    private static String deterministicEventId(byte[] payload, WebhookHeaders headers) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(payload);
            digest.update((byte) 0);
            digest.update(headers.origin().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(headers.subscriptionId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "jfrog-" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String firstText(String fallback, JsonNode... candidates) {
        for (var candidate : candidates) {
            if (candidate != null && candidate.isValueNode() && !candidate.asString().isBlank()) {
                return candidate.asString();
            }
        }
        return fallback;
    }

    record WebhookHeaders(
            String eventId,
            String origin,
            String subscriptionId,
            String correlationId) {}
}
