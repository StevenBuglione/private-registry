package com.stevenbuglione.registry.ingestion;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.stevenbuglione.registry.artifactory.ArtifactoryGateway;
import com.stevenbuglione.registry.eventing.CatalogArtifactChanged;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.zip.ZipInputStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "registry.ingestion", name = "enabled", havingValue = "true")
public class CatalogIngestionService {

    private final ArtifactoryGateway artifactory;
    private final ObjectMapper objectMapper;
    private final IngestionEventRepository events;
    private final DocumentStore documents;
    private final CatalogWriteRepository catalog;
    private final IngestionProperties properties;

    public CatalogIngestionService(
            ArtifactoryGateway artifactory,
            ObjectMapper objectMapper,
            IngestionEventRepository events,
            DocumentStore documents,
            CatalogWriteRepository catalog,
            IngestionProperties properties) {
        this.artifactory = artifactory;
        this.objectMapper = objectMapper;
        this.events = events;
        this.documents = documents;
        this.catalog = catalog;
        this.properties = properties;
    }

    public Outcome accept(CatalogArtifactChanged event) {
        if (!events.claim(event)) {
            return Outcome.DUPLICATE;
        }
        try {
            var digest = ingest(event);
            events.complete(event, digest);
            return Outcome.COMPLETED;
        } catch (QuarantineException exception) {
            events.quarantine(event, exception);
            return Outcome.QUARANTINED;
        } catch (RuntimeException exception) {
            events.fail(event, exception);
            throw exception;
        }
    }

    private String ingest(CatalogArtifactChanged event) {
        if (!properties.governedRepositories().contains(event.repository())) {
            throw new QuarantineException("repository_not_governed", "Event repository is not governed");
        }
        var manifestLocation = manifestLocation(event);
        if (event.action() == CatalogArtifactChanged.Action.DELETED) {
            throw new QuarantineException("governed_artifact_deleted", "A governed catalog artifact was deleted");
        }
        var metadata = artifactory.metadata(manifestLocation.repository(), manifestLocation.path());
        if (!hasProperty(metadata, "registry.catalog.ready", "true")) {
            throw new QuarantineException("manifest_not_ready", "Catalog manifest completion property is missing");
        }
        var bytes = artifactory.download(
                manifestLocation.repository(), manifestLocation.path(), properties.maximumManifestBytes());
        var actualManifestDigest = S3DocumentStore.sha256(bytes);
        if (metadata.sha256() != null
                && !metadata.sha256().isBlank()
                && !actualManifestDigest.equals(prefixDigest(metadata.sha256()))) {
            throw new QuarantineException("manifest_digest_mismatch", "Manifest bytes do not match Artifactory metadata");
        }
        var manifest = parse(bytes);
        manifest.validate();
        verifyApmProperties(metadata, manifest);
        verifyArtifact(manifest);
        var storedDocuments = storeDocuments(manifestLocation.repository(), manifest);
        catalog.stage(manifest, storedDocuments);
        return manifest.release().packageDigest();
    }

    private ManifestLocation manifestLocation(CatalogArtifactChanged event) {
        if (event.path().endsWith(properties.manifestSuffix())) {
            return new ManifestLocation(event.repository(), event.path());
        }
        if (event.repository().equals(properties.catalogRepository())) {
            var separator = event.path().lastIndexOf('/');
            if (separator < 0) {
                throw unsupportedHint(event);
            }
            return new ManifestLocation(
                    properties.catalogRepository(), event.path().substring(0, separator + 1) + properties.manifestSuffix());
        }
        var segments = event.path().split("/");
        if (event.repository().contains("provider") && segments.length >= 4) {
            return new ManifestLocation(
                    properties.catalogRepository(),
                    "v1/providers/%s/%s/%s/%s"
                            .formatted(segments[0], segments[1], segments[2], properties.manifestSuffix()));
        }
        if (event.repository().contains("module") && segments.length >= 4) {
            var version = segments[3].endsWith(".zip")
                    ? segments[3].substring(0, segments[3].length() - 4)
                    : segments[3];
            return new ManifestLocation(
                    properties.catalogRepository(),
                    "v1/modules/%s/%s/%s/%s/%s"
                            .formatted(segments[0], segments[1], segments[2], version, properties.manifestSuffix()));
        }
        throw unsupportedHint(event);
    }

    private static QuarantineException unsupportedHint(CatalogArtifactChanged event) {
        return new QuarantineException(
                "unsupported_artifact_path",
                "Unable to resolve the catalog manifest for " + event.repository() + "/" + event.path());
    }

    private record ManifestLocation(String repository, String path) {}

    private CatalogManifestV1 parse(byte[] content) {
        try {
            return objectMapper.readValue(content, CatalogManifestV1.class);
        } catch (JacksonException exception) {
            throw new QuarantineException("invalid_manifest_json", "Catalog manifest is not valid JSON", exception);
        }
    }

    private void verifyArtifact(CatalogManifestV1 manifest) {
        var metadata = artifactory.metadata(manifest.registry().repository(), manifest.registry().artifactPath());
        if (metadata.size() > properties.maximumArtifactBytes()) {
            throw new QuarantineException("artifact_too_large", "Package artifact exceeds the ingestion limit");
        }
        var artifact = artifactory.download(
                manifest.registry().repository(),
                manifest.registry().artifactPath(),
                properties.maximumArtifactBytes());
        var actualDigest = S3DocumentStore.sha256(artifact);
        if (metadata.sha256() != null
                && !metadata.sha256().isBlank()
                && !actualDigest.equals(prefixDigest(metadata.sha256()))) {
            throw new QuarantineException("artifact_metadata_digest_mismatch", "Artifact bytes do not match metadata");
        }
        if (!manifest.release().packageDigest().equals(actualDigest)) {
            throw new QuarantineException("package_digest_mismatch", "Package artifact does not match its manifest");
        }
        verifySafeArchive(artifact);
    }

    private void verifySafeArchive(byte[] artifact) {
        if (artifact.length < 4 || artifact[0] != 'P' || artifact[1] != 'K') {
            throw new QuarantineException("unsupported_archive", "Package artifact is not a ZIP archive");
        }
        var maximumExpandedBytes = Math.min(properties.maximumArtifactBytes() * 4, 1_073_741_824L);
        var expandedBytes = 0L;
        var entries = 0;
        var buffer = new byte[16_384];
        try (var archive = new ZipInputStream(new ByteArrayInputStream(artifact))) {
            for (var entry = archive.getNextEntry(); entry != null; entry = archive.getNextEntry()) {
                entries++;
                if (entries > 10_000) {
                    throw new QuarantineException("archive_entry_limit", "Package archive contains too many entries");
                }
                var normalized = entry.getName().replace('\\', '/');
                if (normalized.isBlank()
                        || normalized.startsWith("/")
                        || normalized.matches("^[A-Za-z]:.*")
                        || java.nio.file.Path.of(normalized).normalize().startsWith("..")) {
                    throw new QuarantineException("unsafe_archive_path", "Package archive contains an unsafe path");
                }
                for (var read = archive.read(buffer); read >= 0; read = archive.read(buffer)) {
                    expandedBytes += read;
                    if (expandedBytes > maximumExpandedBytes) {
                        throw new QuarantineException(
                                "archive_expansion_limit", "Package archive exceeds the expansion limit");
                    }
                }
            }
        } catch (IOException exception) {
            throw new QuarantineException("invalid_archive", "Package archive cannot be read", exception);
        }
        if (entries == 0) {
            throw new QuarantineException("empty_archive", "Package archive has no entries");
        }
    }

    private java.util.List<CatalogWriteRepository.StoredManifestDocument> storeDocuments(
            String manifestRepository, CatalogManifestV1 manifest) {
        var declared = manifest.documents() == null ? java.util.List.<CatalogManifestV1.Document>of() : manifest.documents();
        var stored = new ArrayList<CatalogWriteRepository.StoredManifestDocument>();
        for (var document : declared) {
            var content = artifactory.download(
                    manifestRepository, document.artifactPath(), properties.maximumDocumentBytes());
            var key = "v1/%s/%s/%s".formatted(
                    manifest.publicId(), manifest.identity().version(), document.path());
            var result = documents.putImmutable(
                    key,
                    content,
                    document.contentType() == null ? "text/markdown" : document.contentType(),
                    document.digest());
            stored.add(new CatalogWriteRepository.StoredManifestDocument(
                    document.path(), document.title(), result));
        }
        if (!stored.isEmpty()
                && stored.stream().noneMatch(document -> document.stored()
                        .digest()
                        .equals(manifest.release().documentationDigest()))) {
            throw new QuarantineException(
                    "documentation_bundle_missing", "No document matches the release documentation digest");
        }
        return java.util.List.copyOf(stored);
    }

    private static void verifyApmProperties(
            ArtifactoryGateway.ArtifactMetadata metadata, CatalogManifestV1 manifest) {
        var propertyValues = metadata.properties().getOrDefault("apm.id", java.util.List.of());
        var actual = new HashSet<>(propertyValues);
        var required = new HashSet<>(manifest.access().apmIds());
        if (!actual.containsAll(required)) {
            throw new QuarantineException("apm_property_mismatch", "Manifest APM assignments are not present on the artifact");
        }
    }

    private static boolean hasProperty(
            ArtifactoryGateway.ArtifactMetadata metadata, String name, String value) {
        return metadata.properties().getOrDefault(name, java.util.List.of()).stream()
                .anyMatch(candidate -> value.equals(candidate.toLowerCase(Locale.ROOT)));
    }

    private static String prefixDigest(String digest) {
        return digest.startsWith("sha256:") ? digest : "sha256:" + digest;
    }

    public enum Outcome {
        COMPLETED,
        DUPLICATE,
        QUARANTINED
    }
}
