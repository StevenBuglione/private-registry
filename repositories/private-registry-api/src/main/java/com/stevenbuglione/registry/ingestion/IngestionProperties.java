package com.stevenbuglione.registry.ingestion;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("registry.ingestion")
public record IngestionProperties(
        boolean enabled,
        List<String> governedRepositories,
        String catalogRepository,
        String manifestSuffix,
        long maximumManifestBytes,
        long maximumArtifactBytes,
        long maximumDocumentBytes,
        int outboxBatchSize,
        int outboxMaximumAttempts,
        int documentIngestionConcurrency) {

    private static final List<String> DEFAULT_REPOSITORIES = List.of(
            "iac-provider-release-local", "iac-module-release-local", "iac-catalog-release-local");

    public IngestionProperties {
        governedRepositories = governedRepositories == null || governedRepositories.isEmpty()
                ? DEFAULT_REPOSITORIES
                : List.copyOf(governedRepositories);
        if (catalogRepository == null || catalogRepository.isBlank()) {
            catalogRepository = "iac-catalog-release-local";
        }
        if (manifestSuffix == null || manifestSuffix.isBlank()) {
            manifestSuffix = "catalog-manifest.json";
        }
        if (maximumManifestBytes < 1) {
            maximumManifestBytes = 8_388_608;
        }
        if (maximumArtifactBytes < 1) {
            maximumArtifactBytes = 536_870_912;
        }
        if (maximumDocumentBytes < 1) {
            maximumDocumentBytes = 16_777_216;
        }
        if (outboxBatchSize < 1) {
            outboxBatchSize = 25;
        }
        if (outboxMaximumAttempts < 1) {
            outboxMaximumAttempts = 10;
        }
        if (documentIngestionConcurrency < 1 || documentIngestionConcurrency > 64) {
            documentIngestionConcurrency = 24;
        }
    }
}
