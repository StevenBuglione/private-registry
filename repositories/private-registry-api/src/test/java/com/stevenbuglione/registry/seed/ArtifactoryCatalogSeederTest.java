package com.stevenbuglione.registry.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stevenbuglione.registry.ingestion.CatalogManifestV1;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArtifactoryCatalogSeederTest {

  @Test
  void derivesHashicorpRepositoryFromReleaseDownload() {
    var entry =
        entry(
            "hashicorp",
            "aws",
            "https://releases.hashicorp.com/terraform-provider-aws/{version}/terraform-provider-aws_{version}_{os}_{arch}.zip");

    assertThat(UpstreamSourceResolver.sourceRepository(entry))
        .isEqualTo("https://github.com/hashicorp/terraform-provider-aws");
  }

  @Test
  void derivesProviderRepositoryFromGithubReleaseDownload() {
    var entry =
        entry(
            "datadog",
            "datadog",
            "https://github.com/DataDog/terraform-provider-datadog/releases/download/v{version}/provider.zip");

    assertThat(UpstreamSourceResolver.sourceRepository(entry))
        .isEqualTo("https://github.com/DataDog/terraform-provider-datadog");
  }

  @Test
  void derivesModuleRepositoryFromGithubTagArchive() {
    var entry =
        new CuratedSeedCatalog.SeedEntry(
            "module",
            "terraform-aws-modules",
            "vpc",
            "aws",
            "VPC",
            "VPC",
            "platform",
            "medium",
            null,
            List.of(),
            List.of("APM0000001"),
            List.of("5.21.0"),
            Map.of(),
            "https://github.com/terraform-aws-modules/terraform-aws-vpc/archive/refs/tags/v{version}.zip",
            Map.of());

    assertThat(UpstreamSourceResolver.sourceRepository(entry))
        .isEqualTo("https://github.com/terraform-aws-modules/terraform-aws-vpc");
  }

  @Test
  void normalizesAPlatformlessArtifactToArchive() {
    var entry =
        entry(
            "terraform-aws-modules",
            "vpc",
            "https://github.com/terraform-aws-modules/terraform-aws-vpc/archive/refs/tags/v{version}.zip");

    assertThat(SeedCatalogPolicy.artifactProperties(entry, "5.21.0", null))
        .containsEntry("registry.platform", "archive");
  }

  @Test
  void derivesPinnedGithubSourceArchive() {
    var entry =
        entry(
            "hashicorp",
            "aws",
            "https://releases.hashicorp.com/terraform-provider-aws/{version}/terraform-provider-aws_{version}_{os}_{arch}.zip");

    assertThat(UpstreamSourceResolver.sourceArchive(entry, "5.100.0"))
        .hasToString(
            "https://github.com/hashicorp/terraform-provider-aws/archive/refs/tags/v5.100.0.zip");
  }

  @Test
  void catalogRepairAllowsOnlyVersionedMarkdownAndManifestPaths() {
    assertThatCode(
            () ->
                CatalogMetadataPolicy.validatePath(
                    "v1/providers/hashicorp/aws/5.100.0/docs/resources/vpc.md"))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                CatalogMetadataPolicy.validatePath(
                    "v1/modules/terraform-aws-modules/vpc/aws/6.0.1/catalog-manifest.json"))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                CatalogMetadataPolicy.validatePath("v1/providers/hashicorp/aws/5.100.0/README.md"))
        .doesNotThrowAnyException();

    assertThatThrownBy(
            () -> CatalogMetadataPolicy.validatePath("hashicorp/aws/5.100.0/provider.zip"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                CatalogMetadataPolicy.validatePath(
                    "v1/providers/hashicorp/aws/5.100.0/release.zip"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                CatalogMetadataPolicy.validatePath(
                    "v1/providers/hashicorp/aws/5.100.0/docs/../release.md"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                CatalogMetadataPolicy.validatePath("v2/providers/hashicorp/aws/5.100.0/README.md"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void governedReleaseBoundaryAllowsMetadataRefreshOnly() {
    var existing =
        manifest("iac-provider-release-local", digest('a'), List.of("APM0000001"), "old.md");
    var refreshed =
        manifest("iac-provider-release-local", digest('a'), List.of("APM0000001"), "new.md");

    assertThat(CatalogMetadataPolicy.equivalentGovernedRelease(existing, refreshed)).isTrue();
    assertThat(
            CatalogMetadataPolicy.equivalentGovernedRelease(
                existing,
                manifest("other-release-local", digest('a'), List.of("APM0000001"), "new.md")))
        .isFalse();
    assertThat(
            CatalogMetadataPolicy.equivalentGovernedRelease(
                existing,
                manifest(
                    "iac-provider-release-local", digest('b'), List.of("APM0000001"), "new.md")))
        .isFalse();
    assertThat(
            CatalogMetadataPolicy.equivalentGovernedRelease(
                existing,
                manifest(
                    "iac-provider-release-local", digest('a'), List.of("APM0000002"), "new.md")))
        .isFalse();
  }

  private static CatalogManifestV1 manifest(
      String repository, String packageDigest, List<String> apmIds, String documentPath) {
    return new CatalogManifestV1(
        1,
        "provider",
        new CatalogManifestV1.Identity("hashicorp", "aws", null, "5.100.0"),
        new CatalogManifestV1.Display(
            "AWS",
            "AWS",
            List.of("provider"),
            List.of("cloud"),
            null,
            "supported",
            "enterprise-verified",
            "approved",
            "high",
            "restricted",
            null,
            null),
        new CatalogManifestV1.RegistryLocation(
            "jfrog.example",
            repository,
            "hashicorp/aws",
            "hashicorp/aws/5.100.0/provider.zip",
            null),
        new CatalogManifestV1.Compatibility(">= 1.8"),
        new CatalogManifestV1.Source(
            "https://github.com/hashicorp/terraform-provider-aws", digest('c'), "v5.100.0"),
        new CatalogManifestV1.Release(
            Instant.parse("2026-07-01T00:00:00Z"),
            packageDigest,
            documentPath,
            digest('d'),
            null,
            null,
            null,
            false,
            false,
            false),
        new CatalogManifestV1.Access(apmIds),
        List.of(
            new CatalogManifestV1.Document(
                documentPath,
                "Docs",
                "text/markdown",
                "v1/providers/hashicorp/aws/5.100.0/" + documentPath,
                digest('d'),
                1)),
        List.of(
            new CatalogManifestV1.Symbol(
                "resource", "aws_vpc", "VPC", documentPath, "resource", null, false, false)));
  }

  private static String digest(char value) {
    return "sha256:" + String.valueOf(value).repeat(64);
  }

  private static CuratedSeedCatalog.SeedEntry entry(
      String namespace, String name, String template) {
    return new CuratedSeedCatalog.SeedEntry(
        "provider",
        namespace,
        name,
        null,
        name,
        name,
        "platform",
        "medium",
        null,
        List.of(),
        List.of("APM0000001"),
        List.of("1.0.0"),
        Map.of(),
        template,
        Map.of());
  }
}
