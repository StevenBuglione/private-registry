package com.stevenbuglione.registry.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stevenbuglione.registry.eventing.CatalogArtifactChanged;
import com.stevenbuglione.registry.eventing.EventingProperties;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class SqsCatalogEventPollerTest {

    @Mock
    private SqsClient sqs;

    @Mock
    private CatalogIngestionService ingestion;

    @Test
    void movesExhaustedFailuresToTheDlqBeforeDeletingTheSourceMessage() {
        var message = Message.builder()
                .messageId("message-1")
                .receiptHandle("receipt-1")
                .body(validEventJson())
                .attributes(java.util.Map.of(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, "3"))
                .build();
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
        when(ingestion.accept(any(CatalogArtifactChanged.class)))
                .thenThrow(new IllegalStateException("transient database failure"));
        var poller = new SqsCatalogEventPoller(sqs, new ObjectMapper(), eventing(3), ingestion);

        poller.poll();

        var deadLetter = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqs).sendMessage(deadLetter.capture());
        assertThat(deadLetter.getValue().queueUrl()).isEqualTo("http://localstack/queue/registry-dlq");
        assertThat(deadLetter.getValue().messageBody()).isEqualTo(message.body());
        assertThat(deadLetter.getValue().messageAttributes()).containsKey("registryFailure");
        verify(sqs).deleteMessage(DeleteMessageRequest.builder()
                .queueUrl("http://localstack/queue/registry-events")
                .receiptHandle("receipt-1")
                .build());
    }

    @Test
    void leavesTransientFailuresOnTheSourceQueueUntilAttemptsAreExhausted() {
        var message = Message.builder()
                .receiptHandle("receipt-2")
                .body(validEventJson())
                .attributes(java.util.Map.of(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, "2"))
                .build();
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
        when(ingestion.accept(any(CatalogArtifactChanged.class)))
                .thenThrow(new IllegalStateException("transient Artifactory failure"));
        var poller = new SqsCatalogEventPoller(sqs, new ObjectMapper(), eventing(3), ingestion);

        poller.poll();

        verify(sqs, never()).sendMessage(any(SendMessageRequest.class));
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void deadLettersMalformedEventsBeforeDeletingThem() {
        var message = Message.builder()
                .receiptHandle("receipt-invalid")
                .body("not-json")
                .build();
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
        var poller = new SqsCatalogEventPoller(sqs, new ObjectMapper(), eventing(3), ingestion);

        poller.poll();

        verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
        var deadLetter = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqs).sendMessage(deadLetter.capture());
        assertThat(deadLetter.getValue().queueUrl()).isEqualTo("http://localstack/queue/registry-dlq");
        assertThat(deadLetter.getValue().messageBody()).isEqualTo("not-json");
        assertThat(deadLetter.getValue().messageAttributes()).containsKey("registryFailure");
        verify(ingestion, never()).accept(any());
    }

    private static EventingProperties eventing(int maximumAttempts) {
        return new EventingProperties(
                true,
                "us-east-1",
                URI.create("http://localstack:4566"),
                "registry-catalog",
                "http://localstack/queue/registry-events",
                "http://localstack/queue/registry-dlq",
                "registry-documents",
                Duration.ofSeconds(1),
                10,
                maximumAttempts);
    }

    private static String validEventJson() {
        return """
                {
                  "schemaVersion": 1,
                  "eventId": "event-1",
                  "action": "DEPLOYED",
                  "origin": "jfrog.example",
                  "subscriptionId": "registry-events",
                  "repository": "iac-catalog-release-local",
                  "path": "v1/providers/hashicorp/null/3.2.4/catalog-manifest.json",
                  "occurredAt": "2026-07-21T12:00:00Z",
                  "correlationId": "correlation-1",
                  "properties": {}
                }
                """;
    }
}
