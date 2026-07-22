package com.stevenbuglione.registry.eventing.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.stevenbuglione.registry.eventing.CatalogArtifactChanged;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class JfrogWebhookParserTest {

  private final JfrogWebhookParser parser = new JfrogWebhookParser(new ObjectMapper());

  @Test
  void normalizesADeployedArtifactWebhook() {
    var payload =
        """
                {
                  "event_type": "deployed",
                  "timestamp": "2026-07-21T12:00:00Z",
                  "data": {
                    "repo_key": "iac-catalog-release-local",
                    "path": "v1/providers/hashicorp/aws/5.100.0/catalog-manifest.json",
                    "properties": {"registry.catalog.ready": "true"}
                  }
                }
                """
            .getBytes(StandardCharsets.UTF_8);
    var headers =
        new JfrogWebhookParser.WebhookHeaders(
            "event-123", "jfrog.example", "registry-events", "correlation-123");

    var event = parser.parse(payload, headers);

    assertThat(event.action()).isEqualTo(CatalogArtifactChanged.Action.DEPLOYED);
    assertThat(event.repository()).isEqualTo("iac-catalog-release-local");
    assertThat(event.path()).endsWith("catalog-manifest.json");
    assertThat(event.properties()).containsEntry("registry.catalog.ready", "true");
    assertThat(event.correlationId()).isEqualTo("correlation-123");
  }

  @Test
  void derivesAStableIdWhenJfrogOmitsOne() {
    var payload =
        """
                {"event_type":"deployed","timestamp":"2026-07-21T12:00:00Z","data":{
                  "repo_key":"iac-provider-release-local",
                  "path":"hashicorp/null/3.2.4/terraform-provider-null_3.2.4_linux_amd64.zip"}}
                """
            .getBytes(StandardCharsets.UTF_8);
    var headers =
        new JfrogWebhookParser.WebhookHeaders(null, "jfrog.example", "registry-events", null);

    var first = parser.parse(payload, headers);
    var retry = parser.parse(payload, headers);

    assertThat(first.eventId()).startsWith("jfrog-").isEqualTo(retry.eventId());
    assertThat(first.correlationId()).isEqualTo(first.eventId());
  }
}
