package com.stevenbuglione.registry.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stevenbuglione.registry.ingestion.ContentDigest;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class UpstreamArtifactCacheTest {

  @TempDir Path temporaryDirectory;

  @Test
  void springSelectsTheProductionTransportConstructor() {
    try (var context = new AnnotationConfigApplicationContext()) {
      TestPropertyValues.of("registry.seed.enabled=true").applyTo(context);
      context.registerBean(SeedProperties.class, () -> properties(1024, false));
      context.register(UpstreamArtifactVerifier.class, UpstreamArtifactCache.class);
      context.refresh();

      assertThat(context.getBean(UpstreamArtifactCache.class)).isNotNull();
    }
  }

  @Test
  void downloadsOnceAndReusesThePersistentCache() throws IOException {
    var requests = new AtomicInteger();
    var content = "governed-archive".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    var seedProperties = properties(1024, false);
    var upstream = new RecordingUpstreamContentClient(content, requests);
    var cache =
        new UpstreamArtifactCache(
            seedProperties, new UpstreamArtifactVerifier(seedProperties), upstream);
    cache.initialize();
    var uri = URI.create("https://example.test/artifact.zip");

    var first = cache.downloadToCache(uri);
    var second = cache.downloadToCache(uri);

    assertThat(second).isEqualTo(first);
    assertThat(Files.readAllBytes(first)).isEqualTo(content);
    assertThat(requests).hasValue(1);
  }

  @Test
  void verifiesPinnedDigestsAndRejectsContentDrift() throws IOException {
    var content = "pinned-module".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    var archive = temporaryDirectory.resolve("module.zip");
    Files.write(archive, content);
    var source =
        new UpstreamSourceResolver.SeedSource(
            "archive", null, java.net.URI.create("https://example.test/module.zip"));
    var expected = ContentDigest.sha256(content);
    var seedProperties = properties(1024, false);
    var cache =
        new UpstreamArtifactCache(seedProperties, new UpstreamArtifactVerifier(seedProperties));

    assertThat(cache.verifiedDigest(moduleEntry(expected), "1.0.0", source, archive))
        .isEqualTo(expected);
    assertThatThrownBy(
            () ->
                cache.verifiedDigest(
                    moduleEntry("sha256:" + "0".repeat(64)), "1.0.0", source, archive))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Pinned checksum does not match");
  }

  @Test
  void rejectsCachedArtifactsOverTheConfiguredLimit() throws IOException {
    var seedProperties = properties(4, true);
    var cache =
        new UpstreamArtifactCache(seedProperties, new UpstreamArtifactVerifier(seedProperties));
    cache.initialize();
    var uri = java.net.URI.create("https://example.test/artifact.zip");
    Files.write(cache.cachePath(uri), new byte[5]);

    assertThatThrownBy(() -> cache.downloadToCache(uri))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cached artifact exceeds");
  }

  private SeedProperties properties(long maximumBytes, boolean allowUnpinnedDigests) {
    return new SeedProperties(
        true,
        "classpath:seed/curated-catalog-v1.json",
        "providers",
        "modules",
        "catalog",
        Duration.ofSeconds(2),
        Duration.ofSeconds(2),
        maximumBytes,
        temporaryDirectory,
        3,
        Duration.ZERO,
        List.of(),
        List.of(),
        allowUnpinnedDigests,
        false);
  }

  private static CuratedSeedCatalog.SeedEntry moduleEntry(String digest) {
    return new CuratedSeedCatalog.SeedEntry(
        "module",
        "example",
        "network",
        "aws",
        "Network",
        "Network module",
        "platform",
        "medium",
        null,
        List.of(),
        List.of("APM0000001"),
        List.of("1.0.0"),
        Map.of(),
        "https://example.test/module.zip",
        Map.of("1.0.0:archive", digest));
  }

  private static final class RecordingUpstreamContentClient implements UpstreamContentClient {

    private final byte[] content;
    private final AtomicInteger requests;

    private RecordingUpstreamContentClient(byte[] content, AtomicInteger requests) {
      this.content = content.clone();
      this.requests = requests;
    }

    @Override
    public void downloadTo(URI uri, Path destination) {
      requests.incrementAndGet();
      try {
        Files.write(destination, content);
      } catch (IOException exception) {
        throw new IllegalStateException("Unable to write test content for " + uri, exception);
      }
    }

    @Override
    public byte[] downloadBytes(URI uri) {
      requests.incrementAndGet();
      return content.clone();
    }
  }
}
