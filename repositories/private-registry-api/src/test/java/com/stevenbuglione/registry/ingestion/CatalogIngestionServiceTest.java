package com.stevenbuglione.registry.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stevenbuglione.registry.artifactory.ArtifactoryGateway;
import com.stevenbuglione.registry.eventing.CatalogArtifactChanged;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class CatalogIngestionServiceTest {

  private static final String CATALOG_REPOSITORY = "iac-catalog-release-local";
  private static final String PROVIDER_REPOSITORY = "iac-provider-release-local";
  private static final String MANIFEST_PATH =
      "v1/providers/hashicorp/null/3.2.4/catalog-manifest.json";
  private static final String ARTIFACT_PATH = "hashicorp/null/3.2.4/provider.zip";
  private static final byte[] PACKAGE_BYTES = packageBytes();
  private static final String PACKAGE_DIGEST = ContentDigest.sha256(PACKAGE_BYTES);
  private static final byte[] DOCUMENT_BYTES =
      "# Resource\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
  private static final String DOCUMENT_DIGEST = ContentDigest.sha256(DOCUMENT_BYTES);
  private static final String DOCUMENT_ARTIFACT_PATH =
      "v1/providers/hashicorp/null/3.2.4/docs/resources/example.md";

  @Mock private ArtifactoryGateway artifactory;

  @Mock private IngestionEventRepository events;

  @Mock private DocumentStore documents;

  @Mock private CatalogWriteRepository catalog;

  @Mock private ObjectMapper objectMapper;

  private CatalogIngestionService service;

  @BeforeEach
  void setUp() {
    var properties =
        new IngestionProperties(
            true,
            List.of(PROVIDER_REPOSITORY, CATALOG_REPOSITORY),
            CATALOG_REPOSITORY,
            "catalog-manifest.json",
            1_048_576,
            536_870_912,
            16_777_216,
            false,
            16);
    service =
        new CatalogIngestionService(
            artifactory, objectMapper, events, documents, catalog, properties);
  }

  @Test
  void duplicateEventDoesNotReadOrStageArtifactoryState() {
    var duplicate = event("event-1", Instant.parse("2026-07-21T12:00:00Z"));
    when(events.claim(duplicate)).thenReturn(false);

    assertThat(service.accept(duplicate)).isEqualTo(CatalogIngestionService.Outcome.DUPLICATE);

    verify(artifactory, never()).metadata(any(), any());
    verify(catalog, never()).stage(any(), any());
  }

  @Test
  void outOfOrderHintsAlwaysReReadAndStageCurrentArtifactoryState() {
    var manifest = manifestBytes();
    var manifestDigest = ContentDigest.sha256(manifest);
    when(events.claim(any())).thenReturn(true);
    when(artifactory.metadata(CATALOG_REPOSITORY, MANIFEST_PATH))
        .thenReturn(
            new ArtifactoryGateway.ArtifactMetadata(
                CATALOG_REPOSITORY,
                MANIFEST_PATH,
                manifest.length,
                manifestDigest,
                Instant.parse("2026-07-21T13:00:00Z"),
                Map.of(
                    "registry.catalog.ready", List.of("true"),
                    "apm.id", List.of("APM0000001"))));
    when(artifactory.download(eq(CATALOG_REPOSITORY), eq(MANIFEST_PATH), anyLong()))
        .thenReturn(manifest);
    when(objectMapper.readValue(any(byte[].class), eq(CatalogManifestV1.class)))
        .thenReturn(currentManifest());
    when(artifactory.metadata(PROVIDER_REPOSITORY, ARTIFACT_PATH))
        .thenReturn(
            new ArtifactoryGateway.ArtifactMetadata(
                PROVIDER_REPOSITORY,
                ARTIFACT_PATH,
                1024,
                PACKAGE_DIGEST,
                Instant.parse("2026-07-21T13:00:00Z"),
                Map.of()));
    when(artifactory.download(eq(PROVIDER_REPOSITORY), eq(ARTIFACT_PATH), anyLong()))
        .thenReturn(PACKAGE_BYTES);
    var newer = event("event-newer", Instant.parse("2026-07-21T13:00:00Z"));
    var delayedOlder = event("event-older", Instant.parse("2026-07-21T11:00:00Z"));

    assertThat(service.accept(newer)).isEqualTo(CatalogIngestionService.Outcome.COMPLETED);
    assertThat(service.accept(delayedOlder)).isEqualTo(CatalogIngestionService.Outcome.COMPLETED);

    var manifestCaptor = ArgumentCaptor.forClass(CatalogManifestV1.class);
    verify(catalog, org.mockito.Mockito.times(2)).stage(manifestCaptor.capture(), eq(List.of()));
    assertThat(manifestCaptor.getAllValues())
        .extracting(value -> value.identity().version())
        .containsExactly("3.2.4", "3.2.4");
    verify(events).complete(newer, PACKAGE_DIGEST);
    verify(events).complete(delayedOlder, PACKAGE_DIGEST);
  }

  @Test
  void providerArtifactHintReconcilesItsReadyCatalogManifest() {
    var manifest = manifestBytes();
    var manifestDigest = ContentDigest.sha256(manifest);
    var artifactEvent =
        new CatalogArtifactChanged(
            1,
            "event-artifact",
            CatalogArtifactChanged.Action.DEPLOYED,
            "jfrog.example",
            "registry-events",
            PROVIDER_REPOSITORY,
            "hashicorp/null/3.2.4/terraform-provider-null_3.2.4_linux_amd64.zip",
            Instant.parse("2026-07-21T13:00:00Z"),
            "correlation-artifact",
            Map.of());
    when(events.claim(artifactEvent)).thenReturn(true);
    when(artifactory.metadata(CATALOG_REPOSITORY, MANIFEST_PATH))
        .thenReturn(
            new ArtifactoryGateway.ArtifactMetadata(
                CATALOG_REPOSITORY,
                MANIFEST_PATH,
                manifest.length,
                manifestDigest,
                Instant.parse("2026-07-21T13:00:00Z"),
                Map.of(
                    "registry.catalog.ready", List.of("true"),
                    "apm.id", List.of("APM0000001"))));
    when(artifactory.download(eq(CATALOG_REPOSITORY), eq(MANIFEST_PATH), anyLong()))
        .thenReturn(manifest);
    when(objectMapper.readValue(any(byte[].class), eq(CatalogManifestV1.class)))
        .thenReturn(currentManifest());
    when(artifactory.metadata(PROVIDER_REPOSITORY, ARTIFACT_PATH))
        .thenReturn(
            new ArtifactoryGateway.ArtifactMetadata(
                PROVIDER_REPOSITORY,
                ARTIFACT_PATH,
                1024,
                PACKAGE_DIGEST,
                Instant.parse("2026-07-21T13:00:00Z"),
                Map.of()));
    when(artifactory.download(eq(PROVIDER_REPOSITORY), eq(ARTIFACT_PATH), anyLong()))
        .thenReturn(PACKAGE_BYTES);

    assertThat(service.accept(artifactEvent)).isEqualTo(CatalogIngestionService.Outcome.COMPLETED);

    verify(catalog).stage(currentManifest(), List.of());
    verify(events).complete(artifactEvent, PACKAGE_DIGEST);
  }

  @Test
  void documentationStorageKeyContainsTheDeclaredContentDigest() {
    var event = event("event-document-key", Instant.parse("2026-07-21T13:00:00Z"));
    var manifestBytes = manifestBytes();
    var manifest = currentManifestWithDocument();
    when(events.claim(event)).thenReturn(true);
    when(artifactory.metadata(CATALOG_REPOSITORY, MANIFEST_PATH))
        .thenReturn(
            new ArtifactoryGateway.ArtifactMetadata(
                CATALOG_REPOSITORY,
                MANIFEST_PATH,
                manifestBytes.length,
                ContentDigest.sha256(manifestBytes),
                Instant.parse("2026-07-21T13:00:00Z"),
                Map.of(
                    "registry.catalog.ready", List.of("true"),
                    "apm.id", List.of("APM0000001"))));
    when(artifactory.download(CATALOG_REPOSITORY, MANIFEST_PATH, 1_048_576L))
        .thenReturn(manifestBytes);
    when(objectMapper.readValue(any(byte[].class), eq(CatalogManifestV1.class)))
        .thenReturn(manifest);
    when(artifactory.metadata(PROVIDER_REPOSITORY, ARTIFACT_PATH))
        .thenReturn(
            new ArtifactoryGateway.ArtifactMetadata(
                PROVIDER_REPOSITORY,
                ARTIFACT_PATH,
                PACKAGE_BYTES.length,
                PACKAGE_DIGEST,
                Instant.parse("2026-07-21T13:00:00Z"),
                Map.of()));
    when(artifactory.download(PROVIDER_REPOSITORY, ARTIFACT_PATH, 536_870_912L))
        .thenReturn(PACKAGE_BYTES);
    when(artifactory.metadata(CATALOG_REPOSITORY, DOCUMENT_ARTIFACT_PATH))
        .thenReturn(
            new ArtifactoryGateway.ArtifactMetadata(
                CATALOG_REPOSITORY,
                DOCUMENT_ARTIFACT_PATH,
                DOCUMENT_BYTES.length,
                DOCUMENT_DIGEST,
                Instant.parse("2026-07-21T13:00:00Z"),
                Map.of()));
    when(artifactory.download(CATALOG_REPOSITORY, DOCUMENT_ARTIFACT_PATH, 16_777_216L))
        .thenReturn(DOCUMENT_BYTES);
    when(documents.putImmutable(
            any(), eq(DOCUMENT_BYTES), eq("text/markdown"), eq(DOCUMENT_DIGEST)))
        .thenAnswer(
            invocation ->
                new DocumentStore.StoredDocument(
                    invocation.<String>getArgument(0),
                    DOCUMENT_DIGEST,
                    DOCUMENT_BYTES.length,
                    "text/markdown",
                    new String(DOCUMENT_BYTES, StandardCharsets.UTF_8)));

    assertThat(service.accept(event)).isEqualTo(CatalogIngestionService.Outcome.COMPLETED);

    var key = ArgumentCaptor.forClass(String.class);
    verify(documents)
        .putImmutable(key.capture(), eq(DOCUMENT_BYTES), eq("text/markdown"), eq(DOCUMENT_DIGEST));
    assertThat(key.getValue())
        .isEqualTo(
            "v1/provider/hashicorp/null/3.2.4/"
                + DOCUMENT_DIGEST.substring("sha256:".length())
                + "/resources/example.md");
  }

  @Test
  void unchangedDocumentationIsValidatedWithoutDownloadingItsContentAgain() {
    var event = event("event-document-existing", Instant.parse("2026-07-21T13:00:00Z"));
    var manifestBytes = manifestBytes();
    var manifest = currentManifestWithDocument();
    var existing =
        new DocumentStore.StoredDocument(
            "v1/provider/hashicorp/null/3.2.4/"
                + DOCUMENT_DIGEST.substring("sha256:".length())
                + "/resources/example.md",
            DOCUMENT_DIGEST,
            DOCUMENT_BYTES.length,
            "text/markdown",
            new String(DOCUMENT_BYTES, StandardCharsets.UTF_8));
    when(events.claim(event)).thenReturn(true);
    when(artifactory.metadata(CATALOG_REPOSITORY, MANIFEST_PATH))
        .thenReturn(
            new ArtifactoryGateway.ArtifactMetadata(
                CATALOG_REPOSITORY,
                MANIFEST_PATH,
                manifestBytes.length,
                ContentDigest.sha256(manifestBytes),
                Instant.parse("2026-07-21T13:00:00Z"),
                Map.of(
                    "registry.catalog.ready", List.of("true"),
                    "apm.id", List.of("APM0000001"))));
    when(artifactory.download(CATALOG_REPOSITORY, MANIFEST_PATH, 1_048_576L))
        .thenReturn(manifestBytes);
    when(objectMapper.readValue(any(byte[].class), eq(CatalogManifestV1.class)))
        .thenReturn(manifest);
    when(artifactory.metadata(PROVIDER_REPOSITORY, ARTIFACT_PATH))
        .thenReturn(
            new ArtifactoryGateway.ArtifactMetadata(
                PROVIDER_REPOSITORY,
                ARTIFACT_PATH,
                PACKAGE_BYTES.length,
                PACKAGE_DIGEST,
                Instant.parse("2026-07-21T13:00:00Z"),
                Map.of()));
    when(artifactory.download(PROVIDER_REPOSITORY, ARTIFACT_PATH, 536_870_912L))
        .thenReturn(PACKAGE_BYTES);
    when(artifactory.metadata(CATALOG_REPOSITORY, DOCUMENT_ARTIFACT_PATH))
        .thenReturn(
            new ArtifactoryGateway.ArtifactMetadata(
                CATALOG_REPOSITORY,
                DOCUMENT_ARTIFACT_PATH,
                DOCUMENT_BYTES.length,
                DOCUMENT_DIGEST,
                Instant.parse("2026-07-21T13:00:00Z"),
                Map.of()));
    when(documents.existingImmutable(
            any(), eq(DOCUMENT_DIGEST), eq((long) DOCUMENT_BYTES.length), eq("text/markdown")))
        .thenReturn(existing);

    assertThat(service.accept(event)).isEqualTo(CatalogIngestionService.Outcome.COMPLETED);

    verify(artifactory).metadata(CATALOG_REPOSITORY, DOCUMENT_ARTIFACT_PATH);
    verify(artifactory, never()).download(CATALOG_REPOSITORY, DOCUMENT_ARTIFACT_PATH, 16_777_216L);
    verify(documents, never()).putImmutable(any(), any(), any(), any());
    verify(catalog)
        .stage(
            manifest,
            List.of(
                new CatalogWriteRepository.StoredManifestDocument(
                    "resources/example.md", "Example", existing)));
  }

  @Test
  void unsafeArchivePathIsDurablyQuarantined() {
    var event = event("event-unsafe-archive", Instant.parse("2026-07-21T13:00:00Z"));
    var manifest = manifestBytes();
    var unsafe = zipBytes("../escape", "unsafe");
    var unsafeDigest = ContentDigest.sha256(unsafe);
    var unsafeManifest = currentManifestWithDigest(unsafeDigest);
    when(events.claim(event)).thenReturn(true);
    when(artifactory.metadata(CATALOG_REPOSITORY, MANIFEST_PATH))
        .thenReturn(
            new ArtifactoryGateway.ArtifactMetadata(
                CATALOG_REPOSITORY,
                MANIFEST_PATH,
                manifest.length,
                ContentDigest.sha256(manifest),
                Instant.parse("2026-07-21T13:00:00Z"),
                Map.of(
                    "registry.catalog.ready", List.of("true"),
                    "apm.id", List.of("APM0000001"))));
    when(artifactory.download(eq(CATALOG_REPOSITORY), eq(MANIFEST_PATH), anyLong()))
        .thenReturn(manifest);
    when(objectMapper.readValue(any(byte[].class), eq(CatalogManifestV1.class)))
        .thenReturn(unsafeManifest);
    when(artifactory.metadata(PROVIDER_REPOSITORY, ARTIFACT_PATH))
        .thenReturn(
            new ArtifactoryGateway.ArtifactMetadata(
                PROVIDER_REPOSITORY,
                ARTIFACT_PATH,
                unsafe.length,
                unsafeDigest,
                Instant.parse("2026-07-21T13:00:00Z"),
                Map.of()));
    when(artifactory.download(eq(PROVIDER_REPOSITORY), eq(ARTIFACT_PATH), anyLong()))
        .thenReturn(unsafe);

    assertThat(service.accept(event)).isEqualTo(CatalogIngestionService.Outcome.QUARANTINED);

    var failure = ArgumentCaptor.forClass(QuarantineException.class);
    verify(events).quarantine(eq(event), failure.capture());
    assertThat(failure.getValue().code()).isEqualTo("unsafe_archive_path");
  }

  @Test
  void unsafeRepositoryIsDurablyQuarantined() {
    var unsafe =
        new CatalogArtifactChanged(
            1,
            "event-unsafe",
            CatalogArtifactChanged.Action.DEPLOYED,
            "jfrog.example",
            "registry-events",
            "untrusted-local",
            MANIFEST_PATH,
            Instant.parse("2026-07-21T12:00:00Z"),
            "correlation-unsafe",
            Map.of());
    when(events.claim(unsafe)).thenReturn(true);

    assertThat(service.accept(unsafe)).isEqualTo(CatalogIngestionService.Outcome.QUARANTINED);

    var failure = ArgumentCaptor.forClass(QuarantineException.class);
    verify(events).quarantine(eq(unsafe), failure.capture());
    assertThat(failure.getValue().code()).isEqualTo("repository_not_governed");
  }

  private static CatalogArtifactChanged event(String eventId, Instant occurredAt) {
    return new CatalogArtifactChanged(
        1,
        eventId,
        CatalogArtifactChanged.Action.PROPERTIES_CHANGED,
        "jfrog.example",
        "registry-events",
        CATALOG_REPOSITORY,
        MANIFEST_PATH,
        occurredAt,
        "correlation-" + eventId,
        Map.of());
  }

  private static byte[] manifestBytes() {
    return "current-artifactory-manifest".getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  private static byte[] packageBytes() {
    return zipBytes("terraform-provider-null", "provider");
  }

  private static byte[] zipBytes(String path, String content) {
    try {
      var output = new ByteArrayOutputStream();
      try (var archive = new ZipOutputStream(output)) {
        archive.putNextEntry(new ZipEntry(path));
        archive.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        archive.closeEntry();
      }
      return output.toByteArray();
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static CatalogManifestV1 currentManifest() {
    return currentManifestWithDigest(PACKAGE_DIGEST);
  }

  private static CatalogManifestV1 currentManifestWithDigest(String packageDigest) {
    return currentManifest(packageDigest, "sha256:" + "b".repeat(64), List.of());
  }

  private static CatalogManifestV1 currentManifestWithDocument() {
    return currentManifest(
        PACKAGE_DIGEST,
        DOCUMENT_DIGEST,
        List.of(
            new CatalogManifestV1.Document(
                "resources/example.md",
                "Example",
                "text/markdown",
                DOCUMENT_ARTIFACT_PATH,
                DOCUMENT_DIGEST,
                DOCUMENT_BYTES.length)));
  }

  private static CatalogManifestV1 currentManifest(
      String packageDigest,
      String documentationDigest,
      List<CatalogManifestV1.Document> manifestDocuments) {
    return new CatalogManifestV1(
        1,
        "provider",
        new CatalogManifestV1.Identity("hashicorp", "null", null, "3.2.4"),
        new CatalogManifestV1.Display(
            "Null Provider",
            "Test provider",
            List.of("null"),
            List.of("platform"),
            null,
            "supported",
            "enterprise-verified",
            "approved",
            "low",
            "restricted",
            null,
            null),
        new CatalogManifestV1.RegistryLocation(
            "jfrog.example", PROVIDER_REPOSITORY, "hashicorp/null", ARTIFACT_PATH, null),
        new CatalogManifestV1.Compatibility(">= 1.8"),
        new CatalogManifestV1.Source(
            "https://github.com/hashicorp/terraform-provider-null", "seed", "v3.2.4"),
        new CatalogManifestV1.Release(
            Instant.parse("2026-07-21T12:00:00Z"),
            packageDigest,
            null,
            documentationDigest,
            null,
            null,
            null,
            false,
            false,
            false),
        new CatalogManifestV1.Access(List.of("APM0000001")),
        manifestDocuments,
        List.of());
  }
}
