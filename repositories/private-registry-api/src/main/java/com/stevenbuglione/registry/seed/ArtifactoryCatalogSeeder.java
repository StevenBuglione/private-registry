package com.stevenbuglione.registry.seed;

import com.stevenbuglione.registry.artifactory.ArtifactoryGateway;
import com.stevenbuglione.registry.config.ArtifactoryProperties;
import com.stevenbuglione.registry.ingestion.CatalogManifestV1;
import com.stevenbuglione.registry.ingestion.S3DocumentStore;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.http.client.HttpResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(prefix = "registry.seed", name = "enabled", havingValue = "true")
public class ArtifactoryCatalogSeeder implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactoryCatalogSeeder.class);

    private final ArtifactoryGateway artifactory;
    private final ArtifactoryProperties artifactoryProperties;
    private final SeedProperties properties;
    private final ResourceLoader resources;
    private final ObjectMapper objectMapper;
    private final HttpClient http;

    public ArtifactoryCatalogSeeder(
            ArtifactoryGateway artifactory,
            ArtifactoryProperties artifactoryProperties,
            SeedProperties properties,
            ResourceLoader resources,
            ObjectMapper objectMapper) {
        this.artifactory = artifactory;
        this.artifactoryProperties = artifactoryProperties;
        this.properties = properties;
        this.resources = resources;
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(properties.connectionTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public void run(ApplicationArguments arguments) {
        createCacheDirectory();
        ensureRepositories();
        var catalog = readCatalog();
        if (catalog.schemaVersion() != 1 || catalog.entries().size() != 42) {
            throw new IllegalStateException("Curated seed catalog must contain schema 1 and exactly 42 packages");
        }
        var selectedEntries = catalog.entries().stream().filter(this::selected).toList();
        if (selectedEntries.isEmpty()) {
            throw new IllegalStateException("No curated packages matched registry.seed.packages");
        }
        var selectedReleaseCount = selectedEntries.stream()
                .flatMap(entry -> entry.versions().stream())
                .filter(this::selectedVersion)
                .count();
        if (selectedReleaseCount == 0) {
            throw new IllegalStateException("No curated releases matched registry.seed.versions");
        }
        selectedEntries.forEach(entry -> {
            var versions = new ArrayList<>(entry.versions());
            java.util.Collections.reverse(versions);
            versions.stream().filter(this::selectedVersion).forEach(version -> seed(
                    catalog,
                    entry,
                    version,
                    Instant.parse("2026-07-01T00:00:00Z")
                    .minusSeconds((long) entry.versions().indexOf(version) * 86_400)));
        });
        if (properties.packages().isEmpty() && properties.versions().isEmpty()) {
            verifyCompleteMirror(catalog);
        }
        LOGGER.info("Curated seeding completed for {} packages", selectedEntries.size());
    }

    private boolean selected(CuratedSeedCatalog.SeedEntry entry) {
        return properties.packages().isEmpty() || properties.packages().contains(packageId(entry));
    }

    private boolean selectedVersion(String version) {
        return properties.versions().isEmpty() || properties.versions().contains(version);
    }

    private static String packageId(CuratedSeedCatalog.SeedEntry entry) {
        return entry.provider()
                ? "provider/%s/%s".formatted(entry.namespace(), entry.name())
                : "module/%s/%s/%s".formatted(entry.namespace(), entry.name(), entry.target());
    }

    private void createCacheDirectory() {
        try {
            Files.createDirectories(properties.cacheDirectory());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create the seed cache directory", exception);
        }
    }

    private void ensureRepositories() {
        artifactory.ensureLocalRepository(properties.providerRepository(), "Governed Registry provider releases");
        artifactory.ensureLocalRepository(properties.moduleRepository(), "Governed Registry module releases");
        artifactory.ensureLocalRepository(properties.catalogRepository(), "Governed Registry manifests and documentation");
    }

    private void verifyCompleteMirror(CuratedSeedCatalog catalog) {
        var providerArtifacts = artifactory.searchByProperty(
                List.of(properties.providerRepository()), "registry.kind", "provider");
        var moduleArtifacts = artifactory.searchByProperty(
                List.of(properties.moduleRepository()), "registry.kind", "module");
        var readyManifests = artifactory.searchByProperty(
                        List.of(properties.catalogRepository()), "registry.catalog.ready", "true")
                .stream()
                .filter(location -> location.path().endsWith("catalog-manifest.json"))
                .toList();
        var expectedProviderArtifacts = catalog.entries().stream()
                .filter(CuratedSeedCatalog.SeedEntry::provider)
                .mapToInt(entry -> entry.versions().size() * catalog.providerPlatforms().size())
                .sum();
        var expectedModuleArtifacts = catalog.entries().stream()
                .filter(entry -> !entry.provider())
                .mapToInt(entry -> entry.versions().size())
                .sum();
        var expectedManifests = catalog.entries().stream().mapToInt(entry -> entry.versions().size()).sum();
        if (providerArtifacts.size() < expectedProviderArtifacts
                || moduleArtifacts.size() < expectedModuleArtifacts
                || readyManifests.size() < expectedManifests) {
            throw new IllegalStateException("JFrog mirror verification did not find the complete curated catalog");
        }
        LOGGER.info(
                "Verified JFrog mirror: provider_artifacts={}, module_artifacts={}, ready_manifests={}",
                providerArtifacts.size(),
                moduleArtifacts.size(),
                readyManifests.size());
    }

    private CuratedSeedCatalog readCatalog() {
        try (var input = resources.getResource(properties.manifestResource()).getInputStream()) {
            return objectMapper.readValue(input, CuratedSeedCatalog.class);
        } catch (IOException | JacksonException exception) {
            throw new IllegalStateException("Unable to read curated seed catalog", exception);
        }
    }

    private void seed(
            CuratedSeedCatalog catalog,
            CuratedSeedCatalog.SeedEntry entry,
            String version,
            Instant publishedAt) {
        LOGGER.info("Seeding {} {}/{} version {}", entry.kind(), entry.namespace(), entry.name(), version);
        var artifacts = sources(catalog, entry, version);
        if (artifacts.isEmpty()) {
            throw new IllegalStateException("No artifacts are configured for " + entry.namespace() + "/" + entry.name());
        }
        var repository = entry.provider() ? properties.providerRepository() : properties.moduleRepository();
        String primaryPath = null;
        String primaryDigest = null;
        for (var source : artifacts) {
            var content = downloadToCache(source.url());
            var digest = digest(content, "SHA-256", "sha256:");
            if (entry.provider()) {
                verifyProviderChecksum(entry, version, source, digest);
            }
            verifyPinnedDigest(entry, version, source.key(), digest);
            var path = artifactPath(entry, version, source.platform());
            var uploadProperties = new java.util.LinkedHashMap<String, Object>(
                    artifactProperties(entry, version, source.platform()));
            uploadProperties.put("registry.sha256", digest);
            var uploaded = uploadImmutable(repository, path, content, digest, uploadProperties);
            if (uploaded.sha256() != null && !digest.equals(prefixDigest(uploaded.sha256()))) {
                throw new IllegalStateException("Artifactory checksum verification failed for " + repository + "/" + path);
            }
            if (primaryPath == null) {
                primaryPath = path;
                primaryDigest = digest;
            }
        }
        publishDocumentationAndManifest(entry, version, publishedAt, repository, primaryPath, primaryDigest);
    }

    private void publishDocumentationAndManifest(
            CuratedSeedCatalog.SeedEntry entry,
            String version,
            Instant publishedAt,
            String artifactRepository,
            String primaryPath,
            String primaryDigest) {
        var basePath = catalogBasePath(entry, version);
        var documentationPath = basePath + "/README.md";
        var documentation = documentation(entry, version);
        var documentationDigest = S3DocumentStore.sha256(documentation);
        var documentationProperties = new java.util.LinkedHashMap<String, Object>(
                artifactProperties(entry, version, "documentation"));
        documentationProperties.put("registry.sha256", documentationDigest);
        uploadImmutable(properties.catalogRepository(), documentationPath, documentation, documentationProperties);

        var sourceUrl = sourceRepository(entry);
        var manifest = new CatalogManifestV1(
                1,
                entry.kind(),
                new CatalogManifestV1.Identity(entry.namespace(), entry.name(), entry.target(), version),
                new CatalogManifestV1.Display(
                        entry.title(),
                        entry.description(),
                        List.of(entry.kind(), entry.target() == null ? "provider" : entry.target()),
                        List.of(entry.owner()),
                        null,
                        "supported",
                        "enterprise-verified",
                        "approved",
                        entry.riskTier(),
                        "restricted"),
                new CatalogManifestV1.RegistryLocation(
                        artifactoryProperties.url().getHost(),
                        artifactRepository,
                        sourceUrl,
                        primaryPath,
                        null),
                new CatalogManifestV1.Compatibility(">= 1.8, < 2.0"),
                new CatalogManifestV1.Source(sourceUrl, "seed-" + version, "v" + version),
                new CatalogManifestV1.Release(
                        publishedAt,
                        primaryDigest,
                        documentationPath,
                        documentationDigest,
                        null,
                        null,
                        null,
                        false,
                        false,
                        false),
                new CatalogManifestV1.Access(entry.apmIds()),
                List.of(new CatalogManifestV1.Document(
                        "README.md",
                        entry.title(),
                        "text/markdown",
                        documentationPath,
                        documentationDigest,
                        documentation.length)));
        manifest.validate();
        var manifestPath = basePath + "/catalog-manifest.json";
        try {
            var bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
            var completionProperties = new java.util.LinkedHashMap<String, Object>(
                    artifactProperties(entry, version, "manifest"));
            completionProperties.put("registry.catalog.ready", "true");
            completionProperties.put("registry.sha256", S3DocumentStore.sha256(bytes));
            uploadManifestImmutable(manifestPath, bytes, manifest, completionProperties);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize catalog manifest", exception);
        }
    }

    private ArtifactoryGateway.ArtifactMetadata uploadManifestImmutable(
            String path,
            byte[] content,
            CatalogManifestV1 expectedManifest,
            Map<String, ?> artifactProperties) {
        try {
            return uploadImmutable(properties.catalogRepository(), path, content, artifactProperties);
        } catch (ImmutableSeedConflictException conflict) {
            var existingMetadata = artifactory.metadata(properties.catalogRepository(), path);
            var existingBytes = artifactory.download(
                    properties.catalogRepository(), path, properties.maximumDownloadBytes());
            try {
                var existingManifest = objectMapper.readValue(existingBytes, CatalogManifestV1.class);
                existingManifest.validate();
                if (equivalentImmutableRelease(existingManifest, expectedManifest)
                        && existingMetadata.properties()
                                .getOrDefault("registry.catalog.ready", List.of())
                                .contains("true")) {
                    LOGGER.info(
                            "Skipping semantically equivalent immutable catalog manifest {}",
                            properties.catalogRepository() + "/" + path);
                    return existingMetadata;
                }
            } catch (JacksonException exception) {
                conflict.addSuppressed(exception);
            }
            throw conflict;
        }
    }

    private static boolean equivalentImmutableRelease(
            CatalogManifestV1 existing, CatalogManifestV1 expected) {
        return existing.schemaVersion() == expected.schemaVersion()
                && existing.publicId().equals(expected.publicId())
                && existing.identity().version().equals(expected.identity().version())
                && existing.registry().repository().equals(expected.registry().repository())
                && existing.registry().artifactPath().equals(expected.registry().artifactPath())
                && existing.release().packageDigest().equals(expected.release().packageDigest())
                && existing.release().documentationDigest().equals(expected.release().documentationDigest())
                && new java.util.HashSet<>(existing.access().apmIds())
                        .equals(new java.util.HashSet<>(expected.access().apmIds()))
                && immutableDocuments(existing.documents()).equals(immutableDocuments(expected.documents()));
    }

    private static java.util.Set<String> immutableDocuments(List<CatalogManifestV1.Document> documents) {
        if (documents == null) {
            return java.util.Set.of();
        }
        return documents.stream()
                .map(document -> document.path() + ":" + document.artifactPath() + ":" + document.digest())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private byte[] download(URI uri) {
        try {
            var request = HttpRequest.newBuilder(uri)
                    .timeout(properties.requestTimeout())
                    .header("User-Agent", "registry-curated-seeder/1")
                    .GET()
                    .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Upstream returned " + response.statusCode() + " for " + uri);
            }
            if (response.body().length > properties.maximumDownloadBytes()) {
                throw new IllegalStateException("Upstream artifact exceeds the seed download limit: " + uri);
            }
            return response.body();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to download " + uri, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while downloading " + uri, exception);
        }
    }

    private Path downloadToCache(URI uri) {
        var target = cachePath(uri);
        try {
            if (Files.isRegularFile(target) && Files.size(target) > 0) {
                if (Files.size(target) > properties.maximumDownloadBytes()) {
                    throw new IllegalStateException("Cached artifact exceeds the seed download limit: " + target);
                }
                LOGGER.info("Using cached upstream artifact {}", target.getFileName());
                return target;
            }
            var temporary = target.resolveSibling(target.getFileName() + ".part-" + UUID.randomUUID());
            var request = HttpRequest.newBuilder(uri)
                    .timeout(properties.requestTimeout())
                    .header("User-Agent", "registry-curated-seeder/1")
                    .GET()
                    .build();
            LOGGER.info("Downloading {} to the persistent seed cache", uri);
            var response = http.send(request, HttpResponse.BodyHandlers.ofFile(temporary));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                Files.deleteIfExists(temporary);
                throw new IllegalStateException("Upstream returned " + response.statusCode() + " for " + uri);
            }
            if (Files.size(temporary) > properties.maximumDownloadBytes()) {
                Files.deleteIfExists(temporary);
                throw new IllegalStateException("Upstream artifact exceeds the seed download limit: " + uri);
            }
            moveCompletedDownload(temporary, target);
            return target;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to cache " + uri, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while downloading " + uri, exception);
        }
    }

    private Path cachePath(URI uri) {
        var filename = Path.of(uri.getPath()).getFileName().toString();
        var urlDigest = digest(uri.toString().getBytes(StandardCharsets.UTF_8), "SHA-256", "").substring(0, 16);
        return properties.cacheDirectory().resolve(urlDigest + "-" + filename);
    }

    private static void moveCompletedDownload(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static List<SeedSource> sources(
            CuratedSeedCatalog catalog,
            CuratedSeedCatalog.SeedEntry entry,
            String version) {
        if (!entry.provider()) {
            var url = expand(entry.downloadTemplate(), version, null);
            return List.of(new SeedSource("archive", null, URI.create(url)));
        }
        var result = new ArrayList<SeedSource>();
        catalog.providerPlatforms().forEach(platform -> result.add(new SeedSource(
                platform,
                platform,
                URI.create(expand(entry.downloadTemplate(), version, platform)))));
        return List.copyOf(result);
    }

    private void verifyProviderChecksum(
            CuratedSeedCatalog.SeedEntry entry,
            String version,
            SeedSource source,
            String actualDigest) {
        var filename = source.url().getPath().substring(source.url().getPath().lastIndexOf('/') + 1);
        var checksumFilename = "terraform-provider-%s_%s_SHA256SUMS".formatted(entry.name(), version);
        var checksumUri = source.url().resolve(checksumFilename);
        var checksumText = new String(download(checksumUri), StandardCharsets.UTF_8);
        var expected = checksumText.lines()
                .map(String::trim)
                .filter(line -> line.endsWith("  " + filename) || line.endsWith(" *" + filename))
                .map(line -> line.substring(0, line.indexOf(' ')))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Official checksum file does not list " + filename));
        if (!actualDigest.equals(prefixDigest(expected))) {
            throw new IllegalStateException("Official provider checksum does not match " + source.url());
        }
    }

    private static String expand(String template, String version, String platform) {
        var value = template.replace("{version}", version);
        if (platform != null) {
            var separator = platform.indexOf('_');
            value = value.replace("{os}", platform.substring(0, separator))
                    .replace("{arch}", platform.substring(separator + 1));
        }
        return value;
    }

    static String sourceRepository(CuratedSeedCatalog.SeedEntry entry) {
        var template = entry.downloadTemplate();
        var githubMarker = "github.com/";
        var githubIndex = template.indexOf(githubMarker);
        if (githubIndex >= 0) {
            var repositoryPath = template.substring(githubIndex + githubMarker.length());
            var segments = repositoryPath.split("/");
            if (segments.length >= 2 && !segments[0].isBlank() && !segments[1].isBlank()) {
                return "https://github.com/" + segments[0] + "/" + segments[1];
            }
        }
        if (template.startsWith("https://releases.hashicorp.com/terraform-provider-")) {
            return "https://github.com/hashicorp/terraform-provider-" + entry.name();
        }
        throw new IllegalStateException("Unable to derive an upstream repository for " + packageId(entry));
    }

    private static String artifactPath(
            CuratedSeedCatalog.SeedEntry entry, String version, String platform) {
        if (entry.provider()) {
            return "%s/%s/%s/terraform-provider-%s_%s_%s.zip"
                    .formatted(entry.namespace(), entry.name(), version, entry.name(), version, platform);
        }
        return "%s/%s/%s/%s.zip".formatted(entry.namespace(), entry.name(), entry.target(), version);
    }

    private static String catalogBasePath(CuratedSeedCatalog.SeedEntry entry, String version) {
        return entry.provider()
                ? "v1/providers/%s/%s/%s".formatted(entry.namespace(), entry.name(), version)
                : "v1/modules/%s/%s/%s/%s".formatted(entry.namespace(), entry.name(), entry.target(), version);
    }

    static Map<String, Object> artifactProperties(
            CuratedSeedCatalog.SeedEntry entry, String version, String platform) {
        return Map.of(
                "apm.id", entry.apmIds(),
                "registry.kind", entry.kind(),
                "registry.namespace", entry.namespace(),
                "registry.name", entry.name(),
                "registry.version", version,
                "registry.platform", platform == null ? "archive" : platform,
                "registry.lifecycle", "approved",
                "registry.verification", "enterprise-verified",
                "registry.risk", entry.riskTier());
    }

    private static byte[] documentation(CuratedSeedCatalog.SeedEntry entry, String version) {
        return ("# " + entry.title() + "\n\n" + entry.description() + "\n\n"
                        + "Version: `" + version + "`\n\n"
                        + "This mirrored release is governed by " + entry.owner() + ".\n")
                .getBytes(StandardCharsets.UTF_8);
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
            throw new IllegalStateException("Pinned checksum does not match for " + entry.namespace() + "/" + entry.name());
        }
    }

    private static String prefixDigest(String digest) {
        return digest.startsWith("sha256:") ? digest : "sha256:" + digest;
    }

    private ArtifactoryGateway.ArtifactMetadata uploadImmutable(
            String repository,
            String path,
            Path content,
            String sha256,
            Map<String, ?> artifactProperties) {
        var existing = existingArtifact(repository, path, sha256);
        if (existing != null) {
            return existing;
        }
        var sha1 = digest(content, "SHA-1", "");
        RuntimeException lastFailure = null;
        for (var attempt = 1; attempt <= properties.uploadAttempts(); attempt++) {
            try {
                LOGGER.info(
                        "Uploading {} (attempt {}/{}, {} bytes)",
                        repository + "/" + path,
                        attempt,
                        properties.uploadAttempts(),
                        Files.size(content));
                var uploaded = artifactory.upload(repository, path, content, sha1, artifactProperties);
                var verified = existingArtifact(repository, path, sha256);
                if (verified == null) {
                    throw new IllegalStateException("Artifactory did not expose the uploaded artifact for verification");
                }
                return uploaded.sha256() == null ? verified : uploaded;
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to inspect cached upload content", exception);
            } catch (RuntimeException exception) {
                lastFailure = exception;
                try {
                    var committed = existingArtifact(repository, path, sha256);
                    if (committed != null) {
                        LOGGER.info("Upload response failed, but Artifactory committed {}", repository + "/" + path);
                        return committed;
                    }
                } catch (RuntimeException probeFailure) {
                    exception.addSuppressed(probeFailure);
                    LOGGER.warn("Post-failure verification also failed for {}", repository + "/" + path);
                }
                if (attempt < properties.uploadAttempts()) {
                    var delay = properties.uploadRetryBackoff().multipliedBy(attempt);
                    LOGGER.warn(
                            "Upload attempt {}/{} failed for {}; retrying in {}: {}",
                            attempt,
                            properties.uploadAttempts(),
                            repository + "/" + path,
                            delay,
                            exception.toString());
                    sleep(delay);
                }
            }
        }
        throw new IllegalStateException(
                "Unable to upload " + repository + "/" + path + " after " + properties.uploadAttempts() + " attempts",
                lastFailure);
    }

    private ArtifactoryGateway.ArtifactMetadata uploadImmutable(
            String repository, String path, byte[] content, Map<String, ?> artifactProperties) {
        var digest = S3DocumentStore.sha256(content);
        var existing = existingArtifact(repository, path, digest);
        if (existing != null) {
            return existing;
        }
        RuntimeException lastFailure = null;
        for (var attempt = 1; attempt <= properties.uploadAttempts(); attempt++) {
            try {
                var uploaded = artifactory.upload(repository, path, content, artifactProperties);
                var verified = existingArtifact(repository, path, digest);
                return verified == null ? uploaded : verified;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                try {
                    var committed = existingArtifact(repository, path, digest);
                    if (committed != null) {
                        return committed;
                    }
                } catch (RuntimeException probeFailure) {
                    exception.addSuppressed(probeFailure);
                }
                if (attempt < properties.uploadAttempts()) {
                    var delay = properties.uploadRetryBackoff().multipliedBy(attempt);
                    LOGGER.warn(
                            "Small upload attempt {}/{} failed for {}; retrying in {}",
                            attempt,
                            properties.uploadAttempts(),
                            repository + "/" + path,
                            delay);
                    sleep(delay);
                }
            }
        }
        throw new IllegalStateException(
                "Unable to upload " + repository + "/" + path + " after " + properties.uploadAttempts() + " attempts",
                lastFailure);
    }

    private ArtifactoryGateway.ArtifactMetadata existingArtifact(
            String repository, String path, String expectedSha256) {
        try {
            var existing = artifactory.metadata(repository, path);
            if (existing.sha256() != null && expectedSha256.equals(prefixDigest(existing.sha256()))) {
                LOGGER.info("Skipping verified immutable artifact {}", repository + "/" + path);
                return existing;
            }
            throw new ImmutableSeedConflictException(repository + "/" + path);
        } catch (Exception exception) {
            // The JFrog client exposes an undeclared checked exception for a missing item.
            if (!(exception instanceof HttpResponseException response) || response.getStatusCode() != 404) {
                if (exception instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException(
                        "Unable to inspect immutable artifact " + repository + "/" + path,
                        exception);
            }
            return null;
        }
    }

    private static void sleep(java.time.Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry an Artifactory upload", exception);
        }
    }

    private static String digest(Path content, String algorithm, String prefix) {
        try (var input = Files.newInputStream(content)) {
            var messageDigest = MessageDigest.getInstance(algorithm);
            var buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                messageDigest.update(buffer, 0, read);
            }
            return prefix + HexFormat.of().formatHex(messageDigest.digest());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to checksum " + content, exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(algorithm + " is not available", exception);
        }
    }

    private static String digest(byte[] content, String algorithm, String prefix) {
        try {
            return prefix + HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(algorithm + " is not available", exception);
        }
    }

    private record SeedSource(String key, String platform, URI url) {}

    private static final class ImmutableSeedConflictException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        private ImmutableSeedConflictException(String artifact) {
            super("Refusing to replace immutable artifact " + artifact);
        }
    }
}
