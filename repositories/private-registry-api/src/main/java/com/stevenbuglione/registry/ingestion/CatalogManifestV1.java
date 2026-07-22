package com.stevenbuglione.registry.ingestion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.stevenbuglione.registry.model.PackageKind;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record CatalogManifestV1(
        int schemaVersion,
        String kind,
        Identity identity,
        Display display,
        RegistryLocation registry,
        Compatibility compatibility,
        Source source,
        Release release,
        Access access,
        List<Document> documents) {

    public void validate() {
        if (schemaVersion != 1) {
            throw new QuarantineException("unsupported_manifest_schema", "Manifest schema must be 1");
        }
        var packageKind = packageKind();
        require(identity, "identity");
        require(display, "display");
        require(registry, "registry");
        require(compatibility, "compatibility");
        require(source, "source");
        require(release, "release");
        require(access, "access");
        requireText(identity.namespace(), "identity.namespace");
        requireText(identity.name(), "identity.name");
        requireText(identity.version(), "identity.version");
        if (packageKind == PackageKind.MODULE) {
            requireText(identity.target(), "identity.target");
        } else if (identity.target() != null && !identity.target().isBlank()) {
            throw new QuarantineException("invalid_provider_target", "Providers cannot declare a target");
        }
        requireText(display.title(), "display.title");
        requireText(display.description(), "display.description");
        requireText(display.supportLevel(), "display.supportLevel");
        requireText(display.verification(), "display.verification");
        requireText(display.lifecycle(), "display.lifecycle");
        requireText(display.riskTier(), "display.riskTier");
        requireText(display.visibility(), "display.visibility");
        requireText(registry.repository(), "registry.repository");
        requireSafePath(registry.artifactPath(), "registry.artifactPath");
        requireText(source.repository(), "source.repository");
        requireText(source.commit(), "source.commit");
        requireText(source.tag(), "source.tag");
        requireText(release.packageDigest(), "release.packageDigest");
        requireDigest(release.packageDigest(), "release.packageDigest");
        requireText(release.documentationDigest(), "release.documentationDigest");
        requireDigest(release.documentationDigest(), "release.documentationDigest");
        require(release.publishedAt(), "release.publishedAt");
        if (access.apmIds() == null || access.apmIds().isEmpty()) {
            throw new QuarantineException("missing_apm_assignment", "At least one APM assignment is required");
        }
        access.apmIds().forEach(apmId -> requireText(apmId, "access.apmIds"));
        if (documents != null) {
            documents.forEach(document -> {
                requireSafePath(document.path(), "documents.path");
                requireSafePath(document.artifactPath(), "documents.artifactPath");
                requireDigest(document.digest(), "documents.digest");
            });
        }
    }

    public PackageKind packageKind() {
        try {
            var result = PackageKind.from(kind);
            if (result == null) {
                throw new IllegalArgumentException("empty kind");
            }
            return result;
        } catch (IllegalArgumentException exception) {
            throw new QuarantineException("invalid_package_kind", "Package kind must be module or provider", exception);
        }
    }

    public String publicId() {
        return packageKind() == PackageKind.MODULE
                ? "module/%s/%s/%s".formatted(identity.namespace(), identity.name(), identity.target())
                : "provider/%s/%s".formatted(identity.namespace(), identity.name());
    }

    public String targetOrEmpty() {
        return identity.target() == null ? "" : identity.target();
    }

    private static void require(Object value, String field) {
        if (value == null) {
            throw new QuarantineException("invalid_manifest", field + " is required");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new QuarantineException("invalid_manifest", field + " is required");
        }
    }

    private static void requireSafePath(String value, String field) {
        requireText(value, field);
        if (value.startsWith("/") || value.contains("..") || value.contains("\\")) {
            throw new QuarantineException("unsafe_artifact_path", field + " is unsafe");
        }
    }

    private static void requireDigest(String value, String field) {
        if (value == null || !value.matches("sha256:[0-9a-f]{64}")) {
            throw new QuarantineException("invalid_digest", field + " must be a SHA-256 digest");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public record Identity(String namespace, String name, String target, String version) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public record Display(
            String title,
            String description,
            List<String> keywords,
            List<String> owners,
            String supportChannel,
            String supportLevel,
            String verification,
            String lifecycle,
            String riskTier,
            String visibility) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public record RegistryLocation(
            String hostname,
            String repository,
            String source,
            String artifactPath,
            String consoleUrl) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public record Compatibility(String terraform) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public record Source(String repository, String commit, String tag) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public record Release(
            Instant publishedAt,
            String packageDigest,
            String documentationPath,
            String documentationDigest,
            String sbomPath,
            String provenancePath,
            String changelogPath,
            boolean prerelease,
            boolean deprecated,
            boolean revoked) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public record Access(List<String> apmIds) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public record Document(
            String path,
            String title,
            String contentType,
            String artifactPath,
            String digest,
            long sizeBytes) {}
}
