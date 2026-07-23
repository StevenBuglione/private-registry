package com.stevenbuglione.registry.ingestion;

import com.stevenbuglione.registry.model.PackageKind;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class RegistryTaxonomy {

  private static final Map<String, List<String>> PROVIDER_CATEGORIES =
      Map.ofEntries(
          Map.entry(
              "aws",
              List.of(
                  "public-cloud",
                  "infrastructure",
                  "networking",
                  "security-authentication",
                  "logging-monitoring",
                  "database",
                  "container-orchestration",
                  "data-management",
                  "web-services")),
          Map.entry(
              "azurerm",
              List.of(
                  "public-cloud",
                  "infrastructure",
                  "networking",
                  "security-authentication",
                  "logging-monitoring",
                  "database",
                  "container-orchestration",
                  "data-management",
                  "web-services")),
          Map.entry("azuread", List.of("security-authentication")),
          Map.entry("google", List.of("public-cloud", "infrastructure", "networking", "database")),
          Map.entry("kubernetes", List.of("container-orchestration")),
          Map.entry("helm", List.of("cloud-automation", "container-orchestration")),
          Map.entry("random", List.of("utility")),
          Map.entry("null", List.of("utility")),
          Map.entry("tls", List.of("security-authentication", "utility")),
          Map.entry("time", List.of("utility")),
          Map.entry("datadog", List.of("logging-monitoring")),
          Map.entry("grafana", List.of("logging-monitoring")));

  private RegistryTaxonomy() {}

  static String tier(CatalogManifestV1 manifest) {
    var configured = manifest.display().tier();
    if (configured != null && !configured.isBlank()) {
      return configured;
    }
    if (manifest.packageKind() == PackageKind.PROVIDER) {
      return "hashicorp".equalsIgnoreCase(manifest.identity().namespace()) ? "official" : "partner";
    }
    return "Azure".equalsIgnoreCase(manifest.identity().namespace()) ? "partner" : "community";
  }

  static String[] categories(CatalogManifestV1 manifest) {
    var configured = manifest.display().categories();
    if (configured != null && !configured.isEmpty()) {
      return configured.toArray(String[]::new);
    }
    return PROVIDER_CATEGORIES
        .getOrDefault(manifest.identity().name().toLowerCase(Locale.ROOT), List.of())
        .toArray(String[]::new);
  }
}
