package com.stevenbuglione.registry.eventing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;
import tools.jackson.databind.ObjectMapper;

class EventBridgeCatalogEventPublisherTest {

    @Test
    void emitsTheDetailTypeMatchedByTheLocalStackRule() {
        var client = mock(EventBridgeClient.class);
        var request = ArgumentCaptor.forClass(PutEventsRequest.class);
        when(client.putEvents(request.capture())).thenReturn(PutEventsResponse.builder()
                .failedEntryCount(0)
                .entries(PutEventsResultEntry.builder().eventId("published-1").build())
                .build());
        var properties = new EventingProperties(
                true,
                "us-east-1",
                null,
                "registry-catalog",
                "queue",
                "dlq",
                "documents",
                Duration.ofSeconds(1),
                10,
                5);
        var publisher = new EventBridgeCatalogEventPublisher(client, new ObjectMapper(), properties);
        var event = new CatalogArtifactChanged(
                1,
                "event-1",
                CatalogArtifactChanged.Action.DEPLOYED,
                "jfrog.example",
                "registry-events",
                "iac-catalog-release-local",
                "providers/hashicorp/null/3.2.4/catalog-manifest.json",
                Instant.parse("2026-07-22T12:00:00Z"),
                "correlation-1",
                Map.of());

        publisher.publish(event);

        assertThat(request.getValue().entries()).singleElement().satisfies(entry -> {
            assertThat(entry.source()).isEqualTo("registry.jfrog");
            assertThat(entry.detailType()).isEqualTo("CatalogArtifactChanged");
            assertThat(entry.eventBusName()).isEqualTo("registry-catalog");
        });
    }
}
