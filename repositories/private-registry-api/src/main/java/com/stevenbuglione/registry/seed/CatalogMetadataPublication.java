package com.stevenbuglione.registry.seed;

import com.stevenbuglione.registry.artifactory.ArtifactoryGateway;
import com.stevenbuglione.registry.config.ArtifactoryProperties;
import com.stevenbuglione.registry.ingestion.CatalogManifestV1;
import com.stevenbuglione.registry.ingestion.ContentDigest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Publishes repairable documentation and manifests under the governed catalog boundary. */
@Component
@ConditionalOnProperty(prefix = "registry.seed", name = "enabled", havingValue = "true")
final class CatalogMetadataPublication {

  private static final Logger LOGGER = LoggerFactory.getLogger(CatalogMetadataPublication.class);

  private final ArtifactoryGateway artifactory;
  private final ArtifactoryProperties artifactoryProperties;
  private final SeedProperties properties;
  private final ObjectMapper objectMapper;
  private final SeedArtifactLookup lookup;

  CatalogMetadataPublication(
      ArtifactoryGateway artifactory,
      ArtifactoryProperties artifactoryProperties,
      SeedProperties properties,
      ObjectMapper objectMapper,
      SeedArtifactLookup lookup) {
    this.artifactory = artifactory;
    this.artifactoryProperties = artifactoryProperties;
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.lookup = lookup;
  }

  String artifactoryHost() {
    return artifactoryProperties.url().getHost();
  }

  void ensureRepositories() {
    artifactory.ensureLocalRepository(
        properties.providerRepository(), "Governed Registry provider releases");
    artifactory.ensureLocalRepository(
        properties.moduleRepository(), "Governed Registry module releases");
    artifactory.ensureLocalRepository(
        properties.catalogRepository(), "Governed Registry manifests and documentation");
  }

  ArtifactoryGateway.ArtifactMetadata publishDocument(
      String path, byte[] content, Map<String, ?> artifactProperties) {
    CatalogMetadataPolicy.validatePath(path);
    var digest = ContentDigest.sha256(content);
    try {
      var existing = lookup.matching(properties.catalogRepository(), path, digest);
      if (existing != null) {
        if (!CatalogMetadataPolicy.containsProperties(existing, artifactProperties)) {
          artifactory.setProperties(properties.catalogRepository(), path, artifactProperties);
          return artifactory.metadata(properties.catalogRepository(), path);
        }
        return existing;
      }
    } catch (ImmutableSeedConflictException conflict) {
      LOGGER.info(
          "Repairing governed catalog metadata {}", properties.catalogRepository() + "/" + path);
    }
    return replace(path, content, digest, artifactProperties);
  }

  ArtifactoryGateway.ArtifactMetadata publishManifest(
      String path,
      byte[] content,
      CatalogManifestV1 expectedManifest,
      Map<String, ?> artifactProperties) {
    CatalogMetadataPolicy.validatePath(path);
    var digest = ContentDigest.sha256(content);
    try {
      var existing = lookup.matching(properties.catalogRepository(), path, digest);
      return existing == null ? replace(path, content, digest, artifactProperties) : existing;
    } catch (ImmutableSeedConflictException conflict) {
      return repairEquivalentManifest(
          new ManifestRepair(path, content, digest, expectedManifest, artifactProperties),
          conflict);
    }
  }

  void verifyCompleteMirror(CuratedSeedCatalog catalog) {
    var providerArtifacts =
        artifactory.searchByProperty(
            List.of(properties.providerRepository()), "registry.kind", "provider");
    var moduleArtifacts =
        artifactory.searchByProperty(
            List.of(properties.moduleRepository()), "registry.kind", "module");
    var readyManifests =
        artifactory
            .searchByProperty(
                List.of(properties.catalogRepository()), "registry.catalog.ready", "true")
            .stream()
            .filter(location -> location.path().endsWith("catalog-manifest.json"))
            .toList();
    var expected =
        new MirrorCounts(
            catalog.entries().stream()
                .filter(CuratedSeedCatalog.SeedEntry::provider)
                .mapToInt(entry -> entry.versions().size() * catalog.providerPlatforms().size())
                .sum(),
            catalog.entries().stream()
                .filter(entry -> !entry.provider())
                .mapToInt(entry -> entry.versions().size())
                .sum(),
            catalog.entries().stream().mapToInt(entry -> entry.versions().size()).sum());
    expected.verify(providerArtifacts.size(), moduleArtifacts.size(), readyManifests.size());
    LOGGER.info(
        "Verified JFrog mirror: provider_artifacts={}, module_artifacts={}, ready_manifests={}",
        providerArtifacts.size(),
        moduleArtifacts.size(),
        readyManifests.size());
  }

  private ArtifactoryGateway.ArtifactMetadata repairEquivalentManifest(
      ManifestRepair repair, ImmutableSeedConflictException conflict) {
    var existingMetadata = artifactory.metadata(properties.catalogRepository(), repair.path());
    var existingBytes =
        artifactory.download(
            properties.catalogRepository(), repair.path(), properties.maximumDownloadBytes());
    try {
      var existingManifest = objectMapper.readValue(existingBytes, CatalogManifestV1.class);
      existingManifest.validate();
      if (CatalogMetadataPolicy.equivalentGovernedRelease(
              existingManifest, repair.expectedManifest())
          && existingMetadata
              .properties()
              .getOrDefault("registry.catalog.ready", List.of())
              .contains("true")) {
        LOGGER.info(
            "Repairing catalog manifest metadata {}",
            properties.catalogRepository() + "/" + repair.path());
        return replace(
            repair.path(), repair.content(), repair.digest(), repair.artifactProperties());
      }
    } catch (JacksonException exception) {
      conflict.addSuppressed(exception);
    }
    throw conflict;
  }

  private ArtifactoryGateway.ArtifactMetadata replace(
      String path, byte[] content, String digest, Map<String, ?> artifactProperties) {
    @Nullable RuntimeException lastFailure = null;
    for (var attempt = 1; attempt <= properties.uploadAttempts(); attempt++) {
      try {
        artifactory.upload(properties.catalogRepository(), path, content, artifactProperties);
        var verified = lookup.matching(properties.catalogRepository(), path, digest);
        if (verified == null) {
          throw new IllegalStateException("Artifactory did not expose repaired catalog metadata");
        }
        return verified;
      } catch (RuntimeException exception) {
        lastFailure = exception;
        retry(attempt, path, exception);
      }
    }
    throw new IllegalStateException(
        "Unable to repair catalog metadata " + properties.catalogRepository() + "/" + path,
        lastFailure);
  }

  private void retry(int attempt, String path, RuntimeException failure) {
    if (attempt >= properties.uploadAttempts()) {
      return;
    }
    var delay = properties.uploadRetryBackoff().multipliedBy(attempt);
    LOGGER.warn(
        "Catalog metadata repair attempt {}/{} failed for {}; retrying in {}: {}",
        attempt,
        properties.uploadAttempts(),
        properties.catalogRepository() + "/" + path,
        delay,
        failure.toString());
    sleep(delay);
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

  private record MirrorCounts(int providers, int modules, int manifests) {
    void verify(int actualProviders, int actualModules, int actualManifests) {
      if (actualProviders < providers || actualModules < modules || actualManifests < manifests) {
        throw new IllegalStateException(
            "JFrog mirror verification did not find the complete curated catalog");
      }
    }
  }

  private static final class ManifestRepair {
    private final String path;
    private final byte[] content;
    private final String digest;
    private final CatalogManifestV1 expectedManifest;
    private final Map<String, ?> artifactProperties;

    private ManifestRepair(
        String path,
        byte[] content,
        String digest,
        CatalogManifestV1 expectedManifest,
        Map<String, ?> artifactProperties) {
      this.path = path;
      this.content = content.clone();
      this.digest = digest;
      this.expectedManifest = expectedManifest;
      this.artifactProperties = Map.copyOf(artifactProperties);
    }

    private String path() {
      return path;
    }

    private byte[] content() {
      return content.clone();
    }

    private String digest() {
      return digest;
    }

    private CatalogManifestV1 expectedManifest() {
      return expectedManifest;
    }

    private Map<String, ?> artifactProperties() {
      return artifactProperties;
    }
  }
}
