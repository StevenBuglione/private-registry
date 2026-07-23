package com.stevenbuglione.registry.eventing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogArtifactChangedTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-07-23T12:00:00Z");

  @Test
  void transportMetadataDoesNotChangeSemanticIdentity() {
    var original = event("event-1", "correlation-1", OCCURRED_AT, properties("a", "1", "b", "2"));
    var retry = event("event-2", "correlation-2", OCCURRED_AT, properties("b", "2", "a", "1"));

    assertThat(retry.semanticKey())
        .isEqualTo(original.semanticKey())
        .matches("^sha256:[0-9a-f]{64}$");
  }

  @Test
  void aLaterArtifactChangeHasASeparateSemanticIdentity() {
    var original = event("event-1", "correlation-1", OCCURRED_AT, Map.of());
    var later = event("event-2", "correlation-2", OCCURRED_AT.plusSeconds(1), Map.of());

    assertThat(later.semanticKey()).isNotEqualTo(original.semanticKey());
  }

  @Test
  void governedPropertyChangesHaveSeparateSemanticIdentities() {
    var original = event("event-1", "correlation-1", OCCURRED_AT, Map.of("apm.id", "APM0000001"));
    var changed = event("event-2", "correlation-2", OCCURRED_AT, Map.of("apm.id", "APM0000002"));

    assertThat(changed.semanticKey()).isNotEqualTo(original.semanticKey());
  }

  private static CatalogArtifactChanged event(
      String eventId, String correlationId, Instant occurredAt, Map<String, String> properties) {
    return new CatalogArtifactChanged(
        1,
        eventId,
        CatalogArtifactChanged.Action.PROPERTIES_CHANGED,
        "jfrog.example",
        "registry-events",
        "iac-catalog-release-local",
        "v1/providers/hashicorp/null/3.2.4/catalog-manifest.json",
        occurredAt,
        correlationId,
        properties);
  }

  private static Map<String, String> properties(
      String firstKey, String firstValue, String secondKey, String secondValue) {
    var properties = new LinkedHashMap<String, String>();
    properties.put(firstKey, firstValue);
    properties.put(secondKey, secondValue);
    return properties;
  }
}
