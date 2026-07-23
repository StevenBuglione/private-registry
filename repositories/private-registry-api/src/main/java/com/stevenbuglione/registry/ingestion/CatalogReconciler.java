package com.stevenbuglione.registry.ingestion;

import com.stevenbuglione.registry.artifactory.ArtifactoryGateway;
import com.stevenbuglione.registry.eventing.CatalogArtifactChanged;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "registry.ingestion", name = "enabled", havingValue = "true")
public class CatalogReconciler {

  private static final String INCREMENTAL_CHECKPOINT = "catalog-manifest-modified-at";

  private final ArtifactoryGateway artifactory;
  private final CatalogIngestionService ingestion;
  private final IngestionProperties properties;
  private final ReconciliationRunRepository runs;

  public CatalogReconciler(
      ArtifactoryGateway artifactory,
      CatalogIngestionService ingestion,
      IngestionProperties properties,
      ReconciliationRunRepository runs) {
    this.artifactory = artifactory;
    this.ingestion = ingestion;
    this.properties = properties;
    this.runs = runs;
  }

  @Scheduled(
      fixedDelayString = "${registry.ingestion.incremental-reconciliation-delay:15m}",
      initialDelayString = "${registry.ingestion.incremental-reconciliation-initial-delay:15m}")
  public void incremental() {
    reconcile("repair", "changed-ready-manifests", runs.checkpoint(INCREMENTAL_CHECKPOINT));
  }

  @Scheduled(cron = "${registry.ingestion.full-reconciliation-cron:0 17 2 * * *}", zone = "UTC")
  public void full() {
    reconcile("repair", "all-ready-manifests", null);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void reconcileAfterStartup() {
    if (properties.reconcileOnStartup()) {
      full();
    }
  }

  private void reconcile(String mode, String scope, @Nullable Instant changedAfter) {
    var lease = runs.tryAcquireLease();
    if (lease.isEmpty()) {
      return;
    }
    try (var reconciliationLease = lease.orElseThrow()) {
      reconciliationLease.execute(() -> reconcileWithLease(mode, scope, changedAfter));
    }
  }

  private void reconcileWithLease(String mode, String scope, @Nullable Instant changedAfter) {
    runs.failAbandonedRuns();
    var runId = runs.start(mode, scope);
    try {
      var result = reconcileManifests(scope, changedAfter, runId);
      runs.complete(runId, result.discrepancies(), result.repaired());
      saveIncrementalCheckpoint(changedAfter, result.newestModifiedAt());
    } catch (RuntimeException exception) {
      runs.fail(runId);
      throw exception;
    }
  }

  private ReconciliationResult reconcileManifests(
      String scope, @Nullable Instant changedAfter, UUID runId) {
    var result = new ReconciliationAccumulator(changedAfter == null ? Instant.EPOCH : changedAfter);
    var manifests =
        artifactory.searchByProperty(
            properties.governedRepositories(), "registry.catalog.ready", "true");
    manifests.stream()
        .map(location -> reconcileManifest(location, scope, changedAfter, runId))
        .flatMap(Optional::stream)
        .forEach(result::record);
    return result.snapshot();
  }

  private Optional<ManifestOutcome> reconcileManifest(
      ArtifactoryGateway.ArtifactLocation location,
      String scope,
      @Nullable Instant changedAfter,
      UUID runId) {
    if (!location.path().endsWith(properties.manifestSuffix())) {
      return Optional.empty();
    }
    var metadata = artifactory.metadata(location.repository(), location.path());
    var modifiedAt = metadata.modifiedAt() == null ? Instant.EPOCH : metadata.modifiedAt();
    if (changedAfter != null && !modifiedAt.isAfter(changedAfter)) {
      return Optional.empty();
    }
    return Optional.of(
        new ManifestOutcome(modifiedAt, ingestion.accept(event(location, metadata, runId, scope))));
  }

  private static CatalogArtifactChanged event(
      ArtifactoryGateway.ArtifactLocation location,
      ArtifactoryGateway.ArtifactMetadata metadata,
      UUID runId,
      String scope) {
    return new CatalogArtifactChanged(
        1,
        eventId(location, metadata, runId),
        CatalogArtifactChanged.Action.PROPERTIES_CHANGED,
        "registry-reconciler",
        "scheduled-reconciliation",
        location.repository(),
        location.path(),
        metadata.modifiedAt() == null ? Instant.now() : metadata.modifiedAt(),
        runId.toString(),
        java.util.Map.of("reconciliation_scope", scope));
  }

  private void saveIncrementalCheckpoint(@Nullable Instant changedAfter, Instant newestModifiedAt) {
    if (changedAfter != null) {
      runs.saveCheckpoint(INCREMENTAL_CHECKPOINT, newestModifiedAt);
    }
  }

  private static String eventId(
      ArtifactoryGateway.ArtifactLocation location,
      ArtifactoryGateway.ArtifactMetadata metadata,
      UUID runId) {
    var input =
        runId
            + ":"
            + location.repository()
            + ":"
            + location.path()
            + ":"
            + metadata.sha256()
            + ":"
            + metadata.modifiedAt();
    try {
      var digest =
          MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
      return "reconcile-" + HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  private record ManifestOutcome(
      Instant modifiedAt, CatalogIngestionService.Outcome ingestionOutcome) {}

  private record ReconciliationResult(int discrepancies, int repaired, Instant newestModifiedAt) {}

  private static final class ReconciliationAccumulator {

    private int discrepancies;
    private int repaired;
    private Instant newestModifiedAt;

    private ReconciliationAccumulator(Instant newestModifiedAt) {
      this.newestModifiedAt = newestModifiedAt;
    }

    private void record(ManifestOutcome outcome) {
      if (outcome.modifiedAt().isAfter(newestModifiedAt)) {
        newestModifiedAt = outcome.modifiedAt();
      }
      if (outcome.ingestionOutcome() != CatalogIngestionService.Outcome.DUPLICATE) {
        discrepancies++;
      }
      if (outcome.ingestionOutcome() == CatalogIngestionService.Outcome.COMPLETED) {
        repaired++;
      }
    }

    private ReconciliationResult snapshot() {
      return new ReconciliationResult(discrepancies, repaired, newestModifiedAt);
    }
  }
}
