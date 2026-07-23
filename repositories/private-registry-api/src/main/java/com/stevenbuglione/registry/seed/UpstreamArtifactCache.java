package com.stevenbuglione.registry.seed;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Downloads upstream release content into a bounded persistent cache. */
@Service
@ConditionalOnProperty(prefix = "registry.seed", name = "enabled", havingValue = "true")
final class UpstreamArtifactCache {

  private static final Logger LOGGER = LoggerFactory.getLogger(UpstreamArtifactCache.class);
  private final SeedProperties properties;
  private final UpstreamArtifactVerifier verifier;
  private final UpstreamContentClient upstream;

  @Autowired
  UpstreamArtifactCache(SeedProperties properties, UpstreamArtifactVerifier verifier) {
    this(properties, verifier, new JdkUpstreamContentClient(properties));
  }

  UpstreamArtifactCache(
      SeedProperties properties,
      UpstreamArtifactVerifier verifier,
      UpstreamContentClient upstream) {
    this.properties = properties;
    this.verifier = verifier;
    this.upstream = upstream;
  }

  void initialize() {
    try {
      Files.createDirectories(properties.cacheDirectory());
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to create the seed cache directory", exception);
    }
  }

  Path downloadToCache(URI uri) {
    var target = cachePath(uri);
    try {
      if (validCachedFile(target)) {
        LOGGER.info("Using cached upstream artifact {}", target.getFileName());
        return target;
      }
      var temporary = target.resolveSibling(target.getFileName() + ".part-" + UUID.randomUUID());
      try {
        downloadTo(uri, temporary);
        moveCompletedDownload(temporary, target);
        return target;
      } finally {
        Files.deleteIfExists(temporary);
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to cache " + uri, exception);
    }
  }

  byte[] downloadBytes(URI uri) {
    var content = upstream.downloadBytes(uri);
    if (content.length > properties.maximumDownloadBytes()) {
      throw tooLarge(uri);
    }
    return content;
  }

  String verifiedDigest(
      CuratedSeedCatalog.SeedEntry entry,
      String version,
      UpstreamSourceResolver.SeedSource source,
      Path content) {
    return verifier.verifiedDigest(entry, version, source, content, this::downloadBytes);
  }

  Path cachePath(URI uri) {
    var filename = Path.of(uri.getPath()).getFileName().toString();
    var prefixLength = "sha256:".length();
    var urlDigest =
        UpstreamArtifactVerifier.sha256(uri.toString().getBytes(StandardCharsets.UTF_8))
            .substring(prefixLength, prefixLength + 16);
    return properties.cacheDirectory().resolve(urlDigest + "-" + filename);
  }

  private void downloadTo(URI uri, Path temporary) {
    LOGGER.info("Downloading {} to the persistent seed cache", uri);
    upstream.downloadTo(uri, temporary);
    try {
      if (Files.size(temporary) > properties.maximumDownloadBytes()) {
        throw tooLarge(uri);
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to inspect downloaded content " + uri, exception);
    }
  }

  private boolean validCachedFile(Path target) throws IOException {
    if (!Files.isRegularFile(target) || Files.size(target) == 0) {
      return false;
    }
    if (Files.size(target) > properties.maximumDownloadBytes()) {
      throw new IllegalStateException("Cached artifact exceeds the seed download limit: " + target);
    }
    return true;
  }

  private static IllegalStateException tooLarge(URI uri) {
    return new IllegalStateException("Upstream artifact exceeds the seed download limit: " + uri);
  }

  private static void moveCompletedDownload(Path temporary, Path target) throws IOException {
    try {
      Files.move(
          temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
