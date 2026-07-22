package com.stevenbuglione.registry.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArtifactoryCatalogSeederTest {

    @Test
    void derivesHashicorpRepositoryFromReleaseDownload() {
        var entry = entry(
                "hashicorp",
                "aws",
                "https://releases.hashicorp.com/terraform-provider-aws/{version}/terraform-provider-aws_{version}_{os}_{arch}.zip");

        assertThat(ArtifactoryCatalogSeeder.sourceRepository(entry))
                .isEqualTo("https://github.com/hashicorp/terraform-provider-aws");
    }

    @Test
    void derivesProviderRepositoryFromGithubReleaseDownload() {
        var entry = entry(
                "datadog",
                "datadog",
                "https://github.com/DataDog/terraform-provider-datadog/releases/download/v{version}/provider.zip");

        assertThat(ArtifactoryCatalogSeeder.sourceRepository(entry))
                .isEqualTo("https://github.com/DataDog/terraform-provider-datadog");
    }

    @Test
    void derivesModuleRepositoryFromGithubTagArchive() {
        var entry = new CuratedSeedCatalog.SeedEntry(
                "module",
                "terraform-aws-modules",
                "vpc",
                "aws",
                "VPC",
                "VPC",
                "platform",
                "medium",
                List.of("APM0000001"),
                List.of("5.21.0"),
                "https://github.com/terraform-aws-modules/terraform-aws-vpc/archive/refs/tags/v{version}.zip",
                Map.of());

        assertThat(ArtifactoryCatalogSeeder.sourceRepository(entry))
                .isEqualTo("https://github.com/terraform-aws-modules/terraform-aws-vpc");
    }

    @Test
    void normalizesAPlatformlessArtifactToArchive() {
        var entry = entry(
                "terraform-aws-modules",
                "vpc",
                "https://github.com/terraform-aws-modules/terraform-aws-vpc/archive/refs/tags/v{version}.zip");

        assertThat(ArtifactoryCatalogSeeder.artifactProperties(entry, "5.21.0", null))
                .containsEntry("registry.platform", "archive");
    }

    private static CuratedSeedCatalog.SeedEntry entry(String namespace, String name, String template) {
        return new CuratedSeedCatalog.SeedEntry(
                "provider",
                namespace,
                name,
                null,
                name,
                name,
                "platform",
                "medium",
                List.of("APM0000001"),
                List.of("1.0.0"),
                template,
                Map.of());
    }
}
