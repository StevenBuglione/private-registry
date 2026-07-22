package com.stevenbuglione.registry.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogManifestV1Test {

    @Test
    void acceptsARestrictedApmAssignedManifest() {
        var manifest = manifest(List.of("APM0000001"));

        manifest.validate();

        assertThat(manifest.publicId()).isEqualTo("provider/hashicorp/aws");
    }

    @Test
    void quarantinesManifestWithoutApmAssignments() {
        var manifest = manifest(List.of());

        assertThatThrownBy(manifest::validate)
                .isInstanceOf(QuarantineException.class)
                .extracting(exception -> ((QuarantineException) exception).code())
                .isEqualTo("missing_apm_assignment");
    }

    private static CatalogManifestV1 manifest(List<String> apmIds) {
        var digest = "sha256:" + "a".repeat(64);
        return new CatalogManifestV1(
                1,
                "provider",
                new CatalogManifestV1.Identity("hashicorp", "aws", null, "5.100.0"),
                new CatalogManifestV1.Display(
                        "AWS Provider",
                        "Manage AWS resources.",
                        List.of("aws"),
                        List.of("cloud-platform"),
                        null,
                        "supported",
                        "enterprise-verified",
                        "approved",
                        "high",
                        "restricted"),
                new CatalogManifestV1.RegistryLocation(
                        "artifacts.example.invalid",
                        "iac-provider-release-local",
                        "registry.example.invalid/hashicorp/aws",
                        "hashicorp/aws/5.100.0/provider.zip",
                        null),
                new CatalogManifestV1.Compatibility(">= 1.8"),
                new CatalogManifestV1.Source("https://example.invalid/source", "abc123", "v5.100.0"),
                new CatalogManifestV1.Release(
                        Instant.parse("2026-07-21T12:00:00Z"),
                        digest,
                        "docs/README.md",
                        digest,
                        null,
                        null,
                        null,
                        false,
                        false,
                        false),
                new CatalogManifestV1.Access(apmIds),
                List.of(new CatalogManifestV1.Document(
                        "README.md", "AWS Provider", "text/markdown", "docs/README.md", digest, 100)));
    }
}
