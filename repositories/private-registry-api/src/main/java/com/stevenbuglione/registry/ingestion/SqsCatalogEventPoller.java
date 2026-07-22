package com.stevenbuglione.registry.ingestion;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.stevenbuglione.registry.eventing.CatalogArtifactChanged;
import com.stevenbuglione.registry.eventing.EventingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Service
@ConditionalOnProperty(prefix = "registry.ingestion", name = "enabled", havingValue = "true")
public class SqsCatalogEventPoller {

    private final SqsClient sqs;
    private final ObjectMapper objectMapper;
    private final EventingProperties eventing;
    private final CatalogIngestionService ingestion;

    public SqsCatalogEventPoller(
            SqsClient sqs,
            ObjectMapper objectMapper,
            EventingProperties eventing,
            CatalogIngestionService ingestion) {
        this.sqs = sqs;
        this.objectMapper = objectMapper;
        this.eventing = eventing;
        this.ingestion = ingestion;
    }

    @Scheduled(fixedDelayString = "${registry.eventing.poll-interval:1s}")
    public void poll() {
        if (eventing.queueUrl().isBlank()) {
            return;
        }
        var response = sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(eventing.queueUrl())
                .maxNumberOfMessages(eventing.pollBatchSize())
                .waitTimeSeconds(1)
                .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)
                .build());
        response.messages().forEach(this::handle);
    }

    private void handle(Message message) {
        try {
            var event = event(message.body());
            ingestion.accept(event);
            delete(message);
        } catch (QuarantineException exception) {
            if (deadLetter(message, exception)) {
                delete(message);
            }
        } catch (RuntimeException exception) {
            if (receiveCount(message) >= eventing.maximumAttempts()) {
                if (deadLetter(message, exception)) {
                    delete(message);
                }
            }
        }
    }

    private CatalogArtifactChanged event(String body) {
        try {
            var root = objectMapper.readTree(body);
            JsonNode detail = root.has("detail") ? root.path("detail") : root;
            if (detail.isString()) {
                detail = objectMapper.readTree(detail.asString());
            }
            return objectMapper.treeToValue(detail, CatalogArtifactChanged.class);
        } catch (JacksonException exception) {
            throw new QuarantineException("invalid_queue_event", "SQS message does not contain a catalog event", exception);
        }
    }

    private boolean deadLetter(Message message, RuntimeException failure) {
        if (eventing.deadLetterQueueUrl().isBlank()) {
            return false;
        }
        sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(eventing.deadLetterQueueUrl())
                .messageBody(message.body())
                .messageAttributes(java.util.Map.of("registryFailure", software.amazon.awssdk.services.sqs.model.MessageAttributeValue
                        .builder()
                        .dataType("String")
                        .stringValue(failure.getClass().getSimpleName())
                        .build()))
                .build());
        return true;
    }

    private void delete(Message message) {
        sqs.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(eventing.queueUrl())
                .receiptHandle(message.receiptHandle())
                .build());
    }

    private static int receiveCount(Message message) {
        var value = message.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
        if (value == null) {
            return 1;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 1;
        }
    }
}
