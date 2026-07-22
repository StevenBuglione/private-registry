package com.stevenbuglione.registry.ingestion;

import com.stevenbuglione.registry.artifactory.ArtifactoryGateway;
import com.stevenbuglione.registry.eventing.CatalogArtifactChanged;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

    @Scheduled(fixedDelayString = "${registry.ingestion.incremental-reconciliation-delay:15m}")
    public void incremental() {
        reconcile("repair", "changed-ready-manifests", runs.checkpoint(INCREMENTAL_CHECKPOINT));
    }

    @Scheduled(cron = "${registry.ingestion.full-reconciliation-cron:0 17 2 * * *}", zone = "UTC")
    public void full() {
        reconcile("repair", "all-ready-manifests", null);
    }

    private void reconcile(String mode, String scope, Instant changedAfter) {
        var runId = runs.start(mode, scope);
        var discrepancies = 0;
        var repaired = 0;
        var newestModifiedAt = changedAfter == null ? Instant.EPOCH : changedAfter;
        try {
            var manifests = artifactory.searchByProperty(
                    properties.governedRepositories(), "registry.catalog.ready", "true");
            for (var location : manifests) {
                if (!location.path().endsWith(properties.manifestSuffix())) {
                    continue;
                }
                var metadata = artifactory.metadata(location.repository(), location.path());
                var modifiedAt = metadata.modifiedAt() == null ? Instant.EPOCH : metadata.modifiedAt();
                if (changedAfter != null && !modifiedAt.isAfter(changedAfter)) {
                    continue;
                }
                if (modifiedAt.isAfter(newestModifiedAt)) {
                    newestModifiedAt = modifiedAt;
                }
                var event = new CatalogArtifactChanged(
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
                var outcome = ingestion.accept(event);
                if (outcome != CatalogIngestionService.Outcome.DUPLICATE) {
                    discrepancies++;
                }
                if (outcome == CatalogIngestionService.Outcome.COMPLETED) {
                    repaired++;
                }
            }
            runs.complete(runId, discrepancies, repaired);
            if (changedAfter != null) {
                runs.saveCheckpoint(INCREMENTAL_CHECKPOINT, newestModifiedAt);
            }
        } catch (RuntimeException exception) {
            runs.fail(runId);
            throw exception;
        }
    }

    private static String eventId(
            ArtifactoryGateway.ArtifactLocation location,
            ArtifactoryGateway.ArtifactMetadata metadata,
            java.util.UUID runId) {
        var input = runId + ":" + location.repository() + ":" + location.path() + ":" + metadata.sha256()
                + ":" + metadata.modifiedAt();
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return "reconcile-" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
