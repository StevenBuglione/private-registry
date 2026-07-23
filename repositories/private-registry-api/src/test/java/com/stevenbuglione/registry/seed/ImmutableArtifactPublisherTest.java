package com.stevenbuglione.registry.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stevenbuglione.registry.artifactory.ArtifactoryGateway;
import com.stevenbuglione.registry.config.ArtifactoryProperties;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class ImmutableArtifactPublisherTest {

  private static final String REPOSITORY = "providers";
  private static final String ARTIFACT_PATH = "hashicorp/aws/1.0.0/provider.zip";
  private static final String DIGEST = "sha256:" + "a".repeat(64);

  @TempDir Path temporaryDirectory;

  @Test
  void retriesARecoverableCatalogRepairAndVerifiesTheCommittedDigest() {
    var gateway = mock(ArtifactoryGateway.class);
    var content = "readme".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    var path = "v1/providers/hashicorp/aws/1.0.0/README.md";
    var expectedDigest = com.stevenbuglione.registry.ingestion.ContentDigest.sha256(content);
    var verified =
        new ArtifactoryGateway.ArtifactMetadata(
            "catalog", path, content.length, expectedDigest, null, Map.of());
    when(gateway.metadata("catalog", path))
        .thenReturn(
            new ArtifactoryGateway.ArtifactMetadata(
                "catalog", path, content.length, "sha256:" + "b".repeat(64), null, Map.of()))
        .thenReturn(verified);
    when(gateway.upload(eq("catalog"), eq(path), eq(content), any()))
        .thenThrow(new IllegalStateException("temporary outage"))
        .thenReturn(verified);
    var publisher = publisher(gateway);

    assertThat(publisher.publishCatalogDocument(path, content, Map.of("registry.kind", "provider")))
        .isEqualTo(verified);
    verify(gateway, org.mockito.Mockito.times(2))
        .upload(eq("catalog"), eq(path), eq(content), any());
  }

  @Test
  void refusesToReplaceAnExistingArtifactWithAnotherDigest() throws IOException {
    var gateway = mock(ArtifactoryGateway.class);
    var content = temporaryDirectory.resolve("provider.zip");
    Files.writeString(content, "provider");
    when(gateway.metadata(REPOSITORY, ARTIFACT_PATH))
        .thenReturn(metadata("sha256:" + "b".repeat(64)));
    var publisher = publisher(gateway);

    assertThatThrownBy(
            () ->
                publisher.publishImmutable(
                    REPOSITORY,
                    ARTIFACT_PATH,
                    content,
                    DIGEST,
                    Map.of("registry.kind", "provider")))
        .isInstanceOf(ImmutableSeedConflictException.class)
        .hasMessageContaining("Refusing to replace immutable artifact");
    verify(gateway, never()).upload(eq(REPOSITORY), eq(ARTIFACT_PATH), eq(content), any());
  }

  @Test
  void skipsAnExistingArtifactWhenItsDigestMatches() throws IOException {
    var gateway = mock(ArtifactoryGateway.class);
    var content = temporaryDirectory.resolve("provider.zip");
    Files.writeString(content, "provider");
    var existing = metadata(DIGEST);
    when(gateway.metadata(REPOSITORY, ARTIFACT_PATH)).thenReturn(existing);
    var publisher = publisher(gateway);

    assertThat(
            publisher.publishImmutable(
                REPOSITORY, ARTIFACT_PATH, content, DIGEST, Map.of("registry.kind", "provider")))
        .isSameAs(existing);
    verify(gateway, never()).upload(eq(REPOSITORY), eq(ARTIFACT_PATH), eq(content), any());
  }

  private ImmutableArtifactPublisher publisher(ArtifactoryGateway gateway) {
    var artifactoryProperties =
        new ArtifactoryProperties(
            URI.create("https://artifacts.example.test/artifactory"),
            "token",
            Duration.ofSeconds(2),
            Duration.ofSeconds(2),
            "modules",
            REPOSITORY);
    var seedProperties = properties();
    var lookup = new SeedArtifactLookup(gateway);
    var catalog =
        new CatalogMetadataPublication(
            gateway, artifactoryProperties, seedProperties, JsonMapper.builder().build(), lookup);
    return new ImmutableArtifactPublisher(
        gateway, seedProperties, lookup, catalog, new CatalogDocumentPublication(catalog));
  }

  private SeedProperties properties() {
    return new SeedProperties(
        true,
        "classpath:seed/curated-catalog-v1.json",
        REPOSITORY,
        "modules",
        "catalog",
        Duration.ofSeconds(2),
        Duration.ofSeconds(2),
        1024,
        temporaryDirectory,
        3,
        Duration.ZERO,
        List.of(),
        List.of(),
        false,
        false);
  }

  private static ArtifactoryGateway.ArtifactMetadata metadata(String digest) {
    return new ArtifactoryGateway.ArtifactMetadata(
        REPOSITORY, ARTIFACT_PATH, 8, digest, null, Map.of());
  }
}
