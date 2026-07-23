package com.stevenbuglione.registry.seed;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Enforces official and curated digest locks for downloaded upstream artifacts. */
@Component
@ConditionalOnProperty(prefix = "registry.seed", name = "enabled", havingValue = "true")
final class UpstreamArtifactVerifier {

  private static final Logger LOGGER = LoggerFactory.getLogger(UpstreamArtifactVerifier.class);

  private final SeedProperties properties;

  UpstreamArtifactVerifier(SeedProperties properties) {
    this.properties = properties;
  }

  String verifiedDigest(
      CuratedSeedCatalog.SeedEntry entry,
      String version,
      UpstreamSourceResolver.SeedSource source,
      Path content,
      Function<URI, byte[]> downloader) {
    var digest = sha256(content);
    if (entry.provider()) {
      verifyProviderChecksum(entry, version, source, digest, downloader);
    }
    verifyPinnedDigest(entry, version, source.key(), digest);
    return digest;
  }

  static String prefixDigest(String digest) {
    return digest.startsWith("sha256:") ? digest : "sha256:" + digest;
  }

  static String sha256(byte[] content) {
    try {
      return "sha256:"
          + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  private void verifyProviderChecksum(
      CuratedSeedCatalog.SeedEntry entry,
      String version,
      UpstreamSourceResolver.SeedSource source,
      String actualDigest,
      Function<URI, byte[]> downloader) {
    var filename = source.url().getPath().substring(source.url().getPath().lastIndexOf('/') + 1);
    var checksumFilename = "terraform-provider-%s_%s_SHA256SUMS".formatted(entry.name(), version);
    var checksumUri = source.url().resolve(checksumFilename);
    var checksumText = new String(downloader.apply(checksumUri), StandardCharsets.UTF_8);
    var expected =
        checksumText
            .lines()
            .map(String::trim)
            .filter(line -> line.endsWith("  " + filename) || line.endsWith(" *" + filename))
            .map(line -> line.substring(0, line.indexOf(' ')))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException("Official checksum file does not list " + filename));
    if (!actualDigest.equals(prefixDigest(expected))) {
      throw new IllegalStateException("Official provider checksum does not match " + source.url());
    }
  }

  private void verifyPinnedDigest(
      CuratedSeedCatalog.SeedEntry entry, String version, String key, String actualDigest) {
    var lockKey = version + ":" + key;
    var expected = entry.expectedSha256().get(lockKey);
    if (expected == null || expected.isBlank()) {
      if (properties.allowUnpinnedDigests()) {
        LOGGER.warn("Allowing unpinned curated seed digest {} for bootstrap only", lockKey);
        return;
      }
      throw new IllegalStateException(
          "Curated seed digest lock is missing " + lockKey + " for " + packageId(entry));
    }
    if (!prefixDigest(expected).equals(actualDigest)) {
      throw new IllegalStateException(
          "Pinned checksum does not match for " + entry.namespace() + "/" + entry.name());
    }
  }

  private static String sha256(Path content) {
    try (var input = Files.newInputStream(content)) {
      var messageDigest = MessageDigest.getInstance("SHA-256");
      var buffer = new byte[64 * 1024];
      int read;
      while ((read = input.read(buffer)) != -1) {
        messageDigest.update(buffer, 0, read);
      }
      return "sha256:" + HexFormat.of().formatHex(messageDigest.digest());
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to checksum " + content, exception);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  private static String packageId(CuratedSeedCatalog.SeedEntry entry) {
    return entry.provider()
        ? "provider/%s/%s".formatted(entry.namespace(), entry.name())
        : "module/%s/%s/%s".formatted(entry.namespace(), entry.name(), entry.target());
  }
}
