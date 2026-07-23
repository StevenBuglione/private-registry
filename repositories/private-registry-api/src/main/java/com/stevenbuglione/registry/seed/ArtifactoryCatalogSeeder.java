package com.stevenbuglione.registry.seed;

import com.stevenbuglione.registry.ingestion.CatalogManifestV1;
import com.stevenbuglione.registry.ingestion.ContentDigest;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Orchestrates curated catalog selection, metadata extraction, and governed publication. */
@Service
@ConditionalOnProperty(prefix = "registry.seed", name = "enabled", havingValue = "true")
public class ArtifactoryCatalogSeeder implements ApplicationRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactoryCatalogSeeder.class);

  private final SeedProperties properties;
  private final ResourceLoader resources;
  private final ObjectMapper objectMapper;
  private final UpstreamArtifactCache upstream;
  private final ImmutableArtifactPublisher publisher;

  public ArtifactoryCatalogSeeder(
      SeedProperties properties,
      ResourceLoader resources,
      ObjectMapper objectMapper,
      UpstreamArtifactCache upstream,
      ImmutableArtifactPublisher publisher) {
    this.properties = properties;
    this.resources = resources;
    this.objectMapper = objectMapper;
    this.upstream = upstream;
    this.publisher = publisher;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    upstream.initialize();
    publisher.ensureRepositories();
    var catalog = readCatalog();
    SeedCatalogPolicy.validate(catalog);
    var selectedEntries = catalog.entries().stream().filter(this::selected).toList();
    if (selectedEntries.isEmpty()) {
      throw new IllegalStateException("No curated packages matched registry.seed.packages");
    }
    if (selectedEntries.stream()
        .flatMap(entry -> entry.versions().stream())
        .noneMatch(this::selectedVersion)) {
      throw new IllegalStateException("No curated releases matched registry.seed.versions");
    }
    selectedEntries.forEach(entry -> seedSelectedVersions(catalog, entry));
    if (properties.packages().isEmpty() && properties.versions().isEmpty()) {
      publisher.verifyCompleteMirror(catalog);
    }
    LOGGER.info("Curated seeding completed for {} packages", selectedEntries.size());
  }

  private void seedSelectedVersions(
      CuratedSeedCatalog catalog, CuratedSeedCatalog.SeedEntry entry) {
    var versions = new ArrayList<>(entry.versions());
    java.util.Collections.reverse(versions);
    versions.stream()
        .filter(this::selectedVersion)
        .forEach(
            version ->
                seed(
                    catalog,
                    entry,
                    version,
                    entry.publishedAt(
                        version,
                        Instant.parse("2026-07-01T00:00:00Z")
                            .minusSeconds((long) entry.versions().indexOf(version) * 86_400))));
  }

  private boolean selected(CuratedSeedCatalog.SeedEntry entry) {
    return properties.packages().isEmpty()
        || properties.packages().contains(SeedCatalogPolicy.packageId(entry));
  }

  private boolean selectedVersion(String version) {
    return properties.versions().isEmpty() || properties.versions().contains(version);
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
    LOGGER.info(
        "Seeding {} {}/{} version {}", entry.kind(), entry.namespace(), entry.name(), version);
    var sources = UpstreamSourceResolver.sources(catalog, entry, version);
    if (sources.isEmpty()) {
      throw new IllegalStateException(
          "No artifacts are configured for " + entry.namespace() + "/" + entry.name());
    }
    var repository =
        entry.provider() ? properties.providerRepository() : properties.moduleRepository();
    @Nullable String primaryPath = null;
    @Nullable String primaryDigest = null;
    @Nullable Path primaryContent = null;
    for (var source : sources) {
      var content = upstream.downloadToCache(source.url());
      var digest = upstream.verifiedDigest(entry, version, source, content);
      var path = SeedCatalogPolicy.artifactPath(entry, version, source.platform());
      var uploadProperties =
          new java.util.LinkedHashMap<String, Object>(
              SeedCatalogPolicy.artifactProperties(entry, version, source.platform()));
      uploadProperties.put("registry.sha256", digest);
      publisher.publishImmutable(repository, path, content, digest, uploadProperties);
      if (primaryPath == null) {
        primaryPath = path;
        primaryDigest = digest;
        primaryContent = content;
      }
    }
    var sourceArchive =
        entry.provider()
            ? upstream.downloadToCache(UpstreamSourceResolver.sourceArchive(entry, version))
            : primaryContent;
    if (sourceArchive == null) {
      throw new IllegalStateException(
          "No upstream source archive was resolved for " + SeedCatalogPolicy.packageId(entry));
    }
    var metadata = TerraformMetadataExtractor.extract(sourceArchive, entry.provider());
    publishDocumentationAndManifest(
        entry,
        new SeedRelease(
            version,
            publishedAt,
            repository,
            Objects.requireNonNull(primaryPath, "Primary artifact path must be resolved"),
            Objects.requireNonNull(primaryDigest, "Primary artifact digest must be resolved")),
        metadata);
  }

  private void publishDocumentationAndManifest(
      CuratedSeedCatalog.SeedEntry entry,
      SeedRelease release,
      TerraformMetadataExtractor.Extraction metadata) {
    var basePath = SeedCatalogPolicy.catalogBasePath(entry, release.version());
    var manifestDocuments =
        publisher.publishCatalogDocuments(entry, release.version(), basePath, metadata.documents());
    var readme =
        manifestDocuments.stream()
            .filter(document -> document.path().equals("README.md"))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Extracted upstream metadata does not contain README.md"));
    var documentationPath =
        Objects.requireNonNull(
            readme.artifactPath(), "Extracted upstream metadata does not contain README.md");
    var documentationDigest =
        Objects.requireNonNull(
            readme.digest(), "Extracted upstream metadata does not contain README.md");
    var sourceUrl = UpstreamSourceResolver.sourceRepository(entry);
    var symbols =
        metadata.symbols().stream()
            .map(
                symbol ->
                    new CatalogManifestV1.Symbol(
                        symbol.kind(),
                        symbol.name(),
                        symbol.description(),
                        symbol.path(),
                        symbol.type(),
                        symbol.defaultValue(),
                        symbol.required(),
                        symbol.sensitive()))
            .toList();
    var parts =
        new ManifestParts(
            new CatalogManifestV1.RegistryLocation(
                publisher.artifactoryHost(),
                release.artifactRepository(),
                sourceUrl,
                release.primaryPath(),
                null),
            new CatalogManifestV1.Source(
                sourceUrl, metadata.archiveDigest(), "v" + release.version()),
            new CatalogManifestV1.Release(
                release.publishedAt(),
                release.primaryDigest(),
                documentationPath,
                documentationDigest,
                null,
                null,
                null,
                false,
                false,
                false));
    var manifest = manifest(entry, release.version(), parts, manifestDocuments, symbols);
    var manifestPath = basePath + "/catalog-manifest.json";
    try {
      var bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
      var completionProperties =
          new java.util.LinkedHashMap<String, Object>(
              SeedCatalogPolicy.artifactProperties(entry, release.version(), "manifest"));
      completionProperties.put("registry.catalog.ready", "true");
      completionProperties.put("registry.sha256", ContentDigest.sha256(bytes));
      publisher.publishCatalogManifest(manifestPath, bytes, manifest, completionProperties);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to serialize catalog manifest", exception);
    }
  }

  private CatalogManifestV1 manifest(
      CuratedSeedCatalog.SeedEntry entry,
      String version,
      ManifestParts parts,
      List<CatalogManifestV1.Document> documents,
      List<CatalogManifestV1.Symbol> symbols) {
    var manifest =
        new CatalogManifestV1(
            1,
            entry.kind(),
            new CatalogManifestV1.Identity(
                entry.namespace(), entry.name(), entry.target(), version),
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
                "restricted",
                entry.tier(),
                entry.categories()),
            parts.location(),
            new CatalogManifestV1.Compatibility(">= 1.8, < 2.0"),
            parts.source(),
            parts.release(),
            new CatalogManifestV1.Access(entry.apmIds()),
            List.copyOf(documents),
            symbols);
    manifest.validate();
    return manifest;
  }

  private record ManifestParts(
      CatalogManifestV1.RegistryLocation location,
      CatalogManifestV1.Source source,
      CatalogManifestV1.Release release) {}

  private record SeedRelease(
      String version,
      Instant publishedAt,
      String artifactRepository,
      String primaryPath,
      String primaryDigest) {}
}
