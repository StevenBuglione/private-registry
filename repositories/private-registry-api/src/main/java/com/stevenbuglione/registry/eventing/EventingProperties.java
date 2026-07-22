package com.stevenbuglione.registry.eventing;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("registry.eventing")
public record EventingProperties(
        boolean enabled,
        String region,
        URI endpoint,
        String eventBusName,
        String queueUrl,
        String deadLetterQueueUrl,
        String documentBucket,
        Duration pollInterval,
        int pollBatchSize,
        int maximumAttempts) {

    public EventingProperties {
        if (region == null || region.isBlank()) {
            region = "us-east-1";
        }
        if (eventBusName == null || eventBusName.isBlank()) {
            eventBusName = "registry-catalog";
        }
        if (queueUrl == null) {
            queueUrl = "";
        }
        if (deadLetterQueueUrl == null) {
            deadLetterQueueUrl = "";
        }
        if (documentBucket == null || documentBucket.isBlank()) {
            documentBucket = "registry-documents";
        }
        if (pollInterval == null) {
            pollInterval = Duration.ofSeconds(1);
        }
        if (pollBatchSize < 1 || pollBatchSize > 10) {
            pollBatchSize = 10;
        }
        if (maximumAttempts < 1) {
            maximumAttempts = 5;
        }
    }
}
