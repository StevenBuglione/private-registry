package com.stevenbuglione.registry.seed;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Resolves pinned release and source locations from curated catalog templates. */
final class UpstreamSourceResolver {

  private UpstreamSourceResolver() {}

  static List<SeedSource> sources(
      CuratedSeedCatalog catalog, CuratedSeedCatalog.SeedEntry entry, String version) {
    if (!entry.provider()) {
      return List.of(
          new SeedSource(
              "archive", null, URI.create(expand(entry.downloadTemplate(), version, null))));
    }
    var result = new ArrayList<SeedSource>();
    catalog
        .providerPlatforms()
        .forEach(
            platform ->
                result.add(
                    new SeedSource(
                        platform,
                        platform,
                        URI.create(expand(entry.downloadTemplate(), version, platform)))));
    return List.copyOf(result);
  }

  static String sourceRepository(CuratedSeedCatalog.SeedEntry entry) {
    var template = entry.downloadTemplate();
    var githubMarker = "github.com/";
    var githubIndex = template.indexOf(githubMarker);
    if (githubIndex >= 0) {
      var repositoryPath = template.substring(githubIndex + githubMarker.length());
      var segments = repositoryPath.split("/", -1);
      if (segments.length >= 2 && !segments[0].isBlank() && !segments[1].isBlank()) {
        return "https://github.com/" + segments[0] + "/" + segments[1];
      }
    }
    if (template.startsWith("https://releases.hashicorp.com/terraform-provider-")) {
      return "https://github.com/hashicorp/terraform-provider-" + entry.name();
    }
    throw new IllegalStateException(
        "Unable to derive an upstream repository for " + packageId(entry));
  }

  static URI sourceArchive(CuratedSeedCatalog.SeedEntry entry, String version) {
    return URI.create(sourceRepository(entry) + "/archive/refs/tags/v" + version + ".zip");
  }

  private static String expand(String template, String version, @Nullable String platform) {
    var value = template.replace("{version}", version);
    if (platform != null) {
      var separator = platform.indexOf('_');
      value =
          value
              .replace("{os}", platform.substring(0, separator))
              .replace("{arch}", platform.substring(separator + 1));
    }
    return value;
  }

  private static String packageId(CuratedSeedCatalog.SeedEntry entry) {
    return entry.provider()
        ? "provider/%s/%s".formatted(entry.namespace(), entry.name())
        : "module/%s/%s/%s".formatted(entry.namespace(), entry.name(), entry.target());
  }

  record SeedSource(String key, @Nullable String platform, URI url) {}
}
