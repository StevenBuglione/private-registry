package com.stevenbuglione.registry.ingestion;

import java.util.List;
import org.jspecify.annotations.Nullable;
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

  private static final List<String> DEFAULT_REPOSITORIES =
      List.of(
          "iac-provider-release-local", "iac-module-release-local", "iac-catalog-release-local");

  public IngestionProperties(
      boolean enabled,
      @Nullable List<String> governedRepositories,
      @Nullable String catalogRepository,
      @Nullable String manifestSuffix,
      long maximumManifestBytes,
      long maximumArtifactBytes,
      long maximumDocumentBytes,
      int outboxBatchSize,
      int outboxMaximumAttempts,
      int documentIngestionConcurrency) {
    this.enabled = enabled;
    this.governedRepositories =
        governedRepositories == null || governedRepositories.isEmpty()
            ? DEFAULT_REPOSITORIES
            : List.copyOf(governedRepositories);
    if (catalogRepository == null || catalogRepository.isBlank()) {
      catalogRepository = "iac-catalog-release-local";
    }
    this.catalogRepository = catalogRepository;
    if (manifestSuffix == null || manifestSuffix.isBlank()) {
      manifestSuffix = "catalog-manifest.json";
    }
    this.manifestSuffix = manifestSuffix;
    if (maximumManifestBytes < 1) {
      maximumManifestBytes = 8_388_608;
    }
    this.maximumManifestBytes = maximumManifestBytes;
    if (maximumArtifactBytes < 1) {
      maximumArtifactBytes = 536_870_912;
    }
    this.maximumArtifactBytes = maximumArtifactBytes;
    if (maximumDocumentBytes < 1) {
      maximumDocumentBytes = 16_777_216;
    }
    this.maximumDocumentBytes = maximumDocumentBytes;
    if (outboxBatchSize < 1) {
      outboxBatchSize = 25;
    }
    this.outboxBatchSize = outboxBatchSize;
    if (outboxMaximumAttempts < 1) {
      outboxMaximumAttempts = 10;
    }
    this.outboxMaximumAttempts = outboxMaximumAttempts;
    if (documentIngestionConcurrency < 1 || documentIngestionConcurrency > 64) {
      documentIngestionConcurrency = 24;
    }
    this.documentIngestionConcurrency = documentIngestionConcurrency;
  }
}
