package com.stevenbuglione.registry.web;

import com.stevenbuglione.registry.model.CatalogPackage;
import com.stevenbuglione.registry.model.PackageKind;
import com.stevenbuglione.registry.model.PackageVersion;
import java.time.Instant;
import java.util.List;

final class TestCatalogFixtures {

    private TestCatalogFixtures() {}

    static CatalogPackage module() {
        var published = Instant.parse("2026-07-21T14:00:00Z");
        var version = new PackageVersion(
                "2.4.1", published, "sha256:fixture", "sha256:fixture",
                "fixtures/modules/vpc/2.4.1", "iac-module-release-local", "cloud-platform/vpc/aws/2.4.1.zip",
                "8d08f7f", false, false, false);
        return new CatalogPackage(
                "module/cloud-platform/vpc/aws", PackageKind.MODULE, "cloud-platform", "vpc", "aws",
                "AWS VPC", "Approved VPC module", "2.4.1", List.of("cloud-network-engineering"),
                "supported", "approved", "enterprise-verified", "medium",
                "artifacts.example.invalid/iac-modules-virtual__cloud-platform/vpc/aws",
                published, List.of(version), List.of());
    }

    static CatalogPackage provider() {
        var published = Instant.parse("2026-07-20T14:00:00Z");
        var version = new PackageVersion(
                "3.8.0", published, "sha256:provider", "sha256:provider-docs",
                "fixtures/providers/cloud/3.8.0", "iac-provider-release-local",
                "platform/cloud/3.8.0/terraform-provider-cloud_3.8.0_linux_amd64.zip",
                "7f5aa91", false, false, false);
        return new CatalogPackage(
                "provider/platform/cloud", PackageKind.PROVIDER, "platform", "cloud", "",
                "Platform Cloud", "Approved cloud provider", "3.8.0", List.of("platform-engineering"),
                "supported", "approved", "enterprise-verified", "low",
                "artifacts.example.invalid/iac-providers-virtual/platform/cloud",
                published, List.of(version), List.of());
    }
}
