package com.stevenbuglione.registry.seed;

import com.stevenbuglione.registry.artifactory.ArtifactoryGateway;
import org.apache.http.client.HttpResponseException;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Normalizes JFrog lookup behavior and enforces expected immutable checksums. */
@Component
@ConditionalOnProperty(prefix = "registry.seed", name = "enabled", havingValue = "true")
final class SeedArtifactLookup {

  private final ArtifactoryGateway artifactory;

  SeedArtifactLookup(ArtifactoryGateway artifactory) {
    this.artifactory = artifactory;
  }

  ArtifactoryGateway.@Nullable ArtifactMetadata matching(
      String repository, String path, String expectedSha256) {
    final ArtifactoryGateway.ArtifactMetadata existing;
    try {
      existing = artifactory.metadata(repository, path);
    } catch (Exception exception) {
      // The JFrog client exposes an undeclared checked exception for a missing item.
      if (exception instanceof HttpResponseException response && response.getStatusCode() == 404) {
        return null;
      }
      throw new IllegalStateException(
          "Unable to inspect immutable artifact " + repository + "/" + path, exception);
    }
    if (existing.sha256() != null
        && expectedSha256.equals(UpstreamArtifactVerifier.prefixDigest(existing.sha256()))) {
      return existing;
    }
    throw new ImmutableSeedConflictException(repository + "/" + path);
  }
}
