package com.stevenbuglione.registry.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CuratedSeedCatalog(
    int schemaVersion, List<String> providerPlatforms, List<SeedEntry> entries) {

  public CuratedSeedCatalog {
    providerPlatforms = providerPlatforms == null ? List.of() : List.copyOf(providerPlatforms);
    entries = entries == null ? List.of() : List.copyOf(entries);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SeedEntry(
      String kind,
      String namespace,
      String name,
      @Nullable String target,
      String title,
      String description,
      String owner,
      String riskTier,
      @Nullable String tier,
      List<String> categories,
      List<String> apmIds,
      List<String> versions,
      Map<String, Instant> versionPublishedAt,
      String downloadTemplate,
      Map<String, String> expectedSha256) {

    public SeedEntry {
      categories = categories == null ? List.of() : List.copyOf(categories);
      apmIds = apmIds == null ? List.of() : List.copyOf(apmIds);
      versions = versions == null ? List.of() : List.copyOf(versions);
      versionPublishedAt = versionPublishedAt == null ? Map.of() : Map.copyOf(versionPublishedAt);
      expectedSha256 = expectedSha256 == null ? Map.of() : Map.copyOf(expectedSha256);
    }

    public boolean provider() {
      return "provider".equals(kind);
    }

    public Instant publishedAt(String version, Instant fallback) {
      return versionPublishedAt.getOrDefault(version, fallback);
    }
  }
}
