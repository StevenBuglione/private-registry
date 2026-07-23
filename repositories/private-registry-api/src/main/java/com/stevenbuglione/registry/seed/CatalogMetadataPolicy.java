package com.stevenbuglione.registry.seed;

import com.stevenbuglione.registry.artifactory.ArtifactoryGateway;
import com.stevenbuglione.registry.ingestion.CatalogManifestV1;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Pure validation and equivalence policy for governed catalog metadata. */
final class CatalogMetadataPolicy {

  private static final java.util.regex.Pattern ALLOWED_PATH =
      java.util.regex.Pattern.compile(
          "^v1/(?:providers/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+"
              + "|modules/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+)"
              + "/(?:README\\.md|catalog-manifest\\.json|docs/(?:[A-Za-z0-9._-]+/)*[A-Za-z0-9._-]+\\.md)$");

  private CatalogMetadataPolicy() {}

  static void validatePath(String path) {
    if (path == null || path.contains("..") || !ALLOWED_PATH.matcher(path).matches()) {
      throw new IllegalArgumentException("Catalog metadata repair path is not allowed");
    }
  }

  static boolean equivalentGovernedRelease(CatalogManifestV1 existing, CatalogManifestV1 expected) {
    return existing.schemaVersion() == expected.schemaVersion()
        && existing.publicId().equals(expected.publicId())
        && existing.identity().version().equals(expected.identity().version())
        && existing.registry().repository().equals(expected.registry().repository())
        && existing.registry().artifactPath().equals(expected.registry().artifactPath())
        && existing.release().packageDigest().equals(expected.release().packageDigest())
        && new HashSet<>(existing.access().apmIds())
            .equals(new HashSet<>(expected.access().apmIds()));
  }

  static boolean containsProperties(
      ArtifactoryGateway.ArtifactMetadata metadata, Map<String, ?> expected) {
    for (var entry : expected.entrySet()) {
      var actual = metadata.properties().getOrDefault(entry.getKey(), List.of());
      if (!containsValue(actual, entry.getValue())) {
        return false;
      }
    }
    return true;
  }

  private static boolean containsValue(List<String> actual, Object expected) {
    if (expected instanceof Iterable<?> iterable) {
      for (var value : iterable) {
        if (!actual.contains(String.valueOf(value))) {
          return false;
        }
      }
      return true;
    }
    return actual.contains(String.valueOf(expected));
  }
}
