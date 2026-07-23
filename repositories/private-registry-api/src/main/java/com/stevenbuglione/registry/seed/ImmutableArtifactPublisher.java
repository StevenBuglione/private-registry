package com.stevenbuglione.registry.seed;

import com.stevenbuglione.registry.artifactory.ArtifactoryGateway;
import com.stevenbuglione.registry.ingestion.CatalogManifestV1;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Publishes immutable release content and exposes governed catalog publication operations. */
@Service
@ConditionalOnProperty(prefix = "registry.seed", name = "enabled", havingValue = "true")
final class ImmutableArtifactPublisher {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImmutableArtifactPublisher.class);

  private final ArtifactoryGateway artifactory;
  private final SeedProperties properties;
  private final SeedArtifactLookup lookup;
  private final CatalogMetadataPublication catalog;
  private final CatalogDocumentPublication documents;

  ImmutableArtifactPublisher(
      ArtifactoryGateway artifactory,
      SeedProperties properties,
      SeedArtifactLookup lookup,
      CatalogMetadataPublication catalog,
      CatalogDocumentPublication documents) {
    this.artifactory = artifactory;
    this.properties = properties;
    this.lookup = lookup;
    this.catalog = catalog;
    this.documents = documents;
  }

  String artifactoryHost() {
    return catalog.artifactoryHost();
  }

  void ensureRepositories() {
    catalog.ensureRepositories();
  }

  ArtifactoryGateway.ArtifactMetadata publishImmutable(
      String repository,
      String path,
      Path content,
      String sha256,
      Map<String, ?> artifactProperties) {
    var existing = lookup.matching(repository, path, sha256);
    if (existing != null) {
      LOGGER.info("Skipping verified immutable artifact {}", repository + "/" + path);
      return existing;
    }
    var contentSize = contentSize(content);
    @Nullable RuntimeException lastFailure = null;
    for (var attempt = 1; attempt <= properties.uploadAttempts(); attempt++) {
      try {
        LOGGER.info(
            "Uploading {} (attempt {}/{}, {} bytes)",
            repository + "/" + path,
            attempt,
            properties.uploadAttempts(),
            contentSize);
        var uploaded = artifactory.upload(repository, path, content, artifactProperties);
        var verified = requireCommitted(repository, path, sha256);
        var result = uploaded.sha256() == null ? verified : uploaded;
        verifyChecksum(repository, path, sha256, result);
        return result;
      } catch (RuntimeException exception) {
        lastFailure = exception;
        var committed = committedAfterFailure(repository, path, sha256, exception);
        if (committed != null) {
          return committed;
        }
        retryAfterFailure(
            attempt,
            repository + "/" + path,
            properties.uploadRetryBackoff().multipliedBy(attempt),
            exception);
      }
    }
    throw new IllegalStateException(
        "Unable to upload "
            + repository
            + "/"
            + path
            + " after "
            + properties.uploadAttempts()
            + " attempts",
        lastFailure);
  }

  ArtifactoryGateway.ArtifactMetadata publishCatalogDocument(
      String path, byte[] content, Map<String, ?> artifactProperties) {
    return catalog.publishDocument(path, content, artifactProperties);
  }

  List<CatalogManifestV1.Document> publishCatalogDocuments(
      CuratedSeedCatalog.SeedEntry entry,
      String version,
      String basePath,
      List<TerraformMetadataExtractor.ExtractedDocument> extractedDocuments) {
    return documents.publish(entry, version, basePath, extractedDocuments);
  }

  ArtifactoryGateway.ArtifactMetadata publishCatalogManifest(
      String path,
      byte[] content,
      CatalogManifestV1 expectedManifest,
      Map<String, ?> artifactProperties) {
    return catalog.publishManifest(path, content, expectedManifest, artifactProperties);
  }

  void verifyCompleteMirror(CuratedSeedCatalog curatedCatalog) {
    catalog.verifyCompleteMirror(curatedCatalog);
  }

  private ArtifactoryGateway.@Nullable ArtifactMetadata committedAfterFailure(
      String repository, String path, String sha256, RuntimeException uploadFailure) {
    try {
      var committed = lookup.matching(repository, path, sha256);
      if (committed != null) {
        LOGGER.info(
            "Upload response failed, but Artifactory committed {}", repository + "/" + path);
      }
      return committed;
    } catch (RuntimeException probeFailure) {
      uploadFailure.addSuppressed(probeFailure);
      LOGGER.warn("Post-failure verification also failed for {}", repository + "/" + path);
      return null;
    }
  }

  private ArtifactoryGateway.ArtifactMetadata requireCommitted(
      String repository, String path, String sha256) {
    var verified = lookup.matching(repository, path, sha256);
    if (verified == null) {
      throw new IllegalStateException(
          "Artifactory did not expose the uploaded artifact for verification");
    }
    return verified;
  }

  private void retryAfterFailure(
      int attempt, String location, Duration delay, RuntimeException failure) {
    if (attempt >= properties.uploadAttempts()) {
      return;
    }
    LOGGER.warn(
        "Upload attempt {}/{} failed for {}; retrying in {}: {}",
        attempt,
        properties.uploadAttempts(),
        location,
        delay,
        failure.toString());
    sleep(delay);
  }

  private static void verifyChecksum(
      String repository,
      String path,
      String expectedSha256,
      ArtifactoryGateway.ArtifactMetadata metadata) {
    if (metadata.sha256() != null
        && !expectedSha256.equals(UpstreamArtifactVerifier.prefixDigest(metadata.sha256()))) {
      throw new IllegalStateException(
          "Artifactory checksum verification failed for " + repository + "/" + path);
    }
  }

  private static long contentSize(Path content) {
    try {
      return Files.size(content);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to inspect cached upload content", exception);
    }
  }

  private static void sleep(Duration delay) {
    try {
      Thread.sleep(delay);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while waiting to retry an Artifactory upload", exception);
    }
  }
}
