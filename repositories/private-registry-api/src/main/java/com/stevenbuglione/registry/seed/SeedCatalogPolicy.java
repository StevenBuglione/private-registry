package com.stevenbuglione.registry.seed;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Pure package identity, path, property, and catalog-shape rules for curated seeding. */
final class SeedCatalogPolicy {

  private SeedCatalogPolicy() {}

  static void validate(CuratedSeedCatalog catalog) {
    if (catalog.schemaVersion() != 1 || catalog.entries().size() != 42) {
      throw new IllegalStateException(
          "Curated seed catalog must contain schema 1 and exactly 42 packages");
    }
  }

  static String packageId(CuratedSeedCatalog.SeedEntry entry) {
    return entry.provider()
        ? "provider/%s/%s".formatted(entry.namespace(), entry.name())
        : "module/%s/%s/%s".formatted(entry.namespace(), entry.name(), entry.target());
  }

  static String artifactPath(
      CuratedSeedCatalog.SeedEntry entry, String version, @Nullable String platform) {
    if (entry.provider()) {
      var providerPlatform =
          Objects.requireNonNull(platform, "A provider release must declare a platform");
      return "%s/%s/%s/terraform-provider-%s_%s_%s.zip"
          .formatted(
              entry.namespace(), entry.name(), version, entry.name(), version, providerPlatform);
    }
    return "%s/%s/%s/%s.zip".formatted(entry.namespace(), entry.name(), entry.target(), version);
  }

  static String catalogBasePath(CuratedSeedCatalog.SeedEntry entry, String version) {
    return entry.provider()
        ? "v1/providers/%s/%s/%s".formatted(entry.namespace(), entry.name(), version)
        : "v1/modules/%s/%s/%s/%s"
            .formatted(entry.namespace(), entry.name(), entry.target(), version);
  }

  static Map<String, Object> artifactProperties(
      CuratedSeedCatalog.SeedEntry entry, String version, @Nullable String platform) {
    return Map.of(
        "apm.id", entry.apmIds(),
        "registry.kind", entry.kind(),
        "registry.namespace", entry.namespace(),
        "registry.name", entry.name(),
        "registry.version", version,
        "registry.platform", platform == null ? "archive" : platform,
        "registry.lifecycle", "approved",
        "registry.verification", "enterprise-verified",
        "registry.risk", entry.riskTier());
  }
}
