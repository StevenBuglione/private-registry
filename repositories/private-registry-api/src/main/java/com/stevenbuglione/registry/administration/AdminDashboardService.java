package com.stevenbuglione.registry.administration;

import com.stevenbuglione.registry.health.WorkerDependencyHealthService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardService {

  private final JdbcClient jdbc;
  private final ObjectProvider<WorkerDependencyHealthService> workerHealth;

  public AdminDashboardService(
      JdbcClient jdbc, ObjectProvider<WorkerDependencyHealthService> workerHealth) {
    this.jdbc = jdbc;
    this.workerHealth = workerHealth;
  }

  public Dashboard dashboard() {
    var catalog = catalogMetrics();
    var queue = queueMetrics();
    var ingestion = ingestionMetrics();
    var healthService = workerHealth.getIfAvailable();
    var dependencies = new LinkedHashMap<String, String>();
    dependencies.put("postgresql", "up");
    var workerEnabled = healthService != null;
    var workerReady = true;
    if (healthService == null) {
      dependencies.put("artifactory", "not_configured");
    } else {
      var report = healthService.check();
      dependencies.putAll(report.dependencies());
      workerReady = report.ready();
    }
    var degraded =
        !workerReady
            || queue.deadLetter() > 0
            || ingestion.failed() > 0
            || ingestion.quarantined() > 0;
    return new Dashboard(
        Instant.now(),
        degraded ? "degraded" : "healthy",
        workerEnabled,
        Map.copyOf(dependencies),
        catalog,
        queue,
        ingestion,
        latestReconciliation(),
        databaseSizeBytes());
  }

  private CatalogMetrics catalogMetrics() {
    return jdbc.sql(
            """
            SELECT COUNT(*) FILTER (WHERE kind = 'provider') AS providers,
                   COUNT(*) FILTER (WHERE kind = 'module') AS modules,
                   (SELECT COUNT(*) FROM package_versions WHERE active) AS active_versions,
                   (SELECT COUNT(*) FROM documentation_pages) AS documents,
                   (SELECT COALESCE(SUM(latest.download_count), 0)
                      FROM (
                          SELECT DISTINCT ON (package_version_id)
                                 package_version_id,
                                 download_count
                            FROM artifact_download_statistics
                           ORDER BY package_version_id, observed_on DESC) latest) AS downloads
              FROM packages
            """)
        .query(
            (resultSet, rowNumber) ->
                new CatalogMetrics(
                    resultSet.getLong("providers"),
                    resultSet.getLong("modules"),
                    resultSet.getLong("active_versions"),
                    resultSet.getLong("documents"),
                    resultSet.getLong("downloads")))
        .single();
  }

  private QueueMetrics queueMetrics() {
    return jdbc.sql(
            """
            SELECT COUNT(*) FILTER (WHERE status = 'queued') AS queued,
                   COUNT(*) FILTER (WHERE status = 'processing') AS processing,
                   COUNT(*) FILTER (WHERE status = 'retry') AS retry,
                   COUNT(*) FILTER (WHERE status = 'completed') AS completed,
                   COUNT(*) FILTER (WHERE status = 'dead_letter') AS dead_letter
              FROM catalog_event_queue
            """)
        .query(
            (resultSet, rowNumber) ->
                new QueueMetrics(
                    resultSet.getLong("queued"),
                    resultSet.getLong("processing"),
                    resultSet.getLong("retry"),
                    resultSet.getLong("completed"),
                    resultSet.getLong("dead_letter")))
        .single();
  }

  private IngestionMetrics ingestionMetrics() {
    return jdbc.sql(
            """
            WITH recent AS (
                SELECT *
                  FROM ingestion_events
                 WHERE received_at >= now() - interval '24 hours'
            ),
            latest_source_outcomes AS (
                SELECT DISTINCT ON (
                           COALESCE(source_repository, event_id),
                           COALESCE(source_path, event_id))
                       status
                  FROM recent
                 ORDER BY
                       COALESCE(source_repository, event_id),
                       COALESCE(source_path, event_id),
                       received_at DESC,
                       id DESC
            )
            SELECT COUNT(*) FILTER (
                       WHERE status = 'completed'
                   ) AS completed,
                   (SELECT COUNT(*) FROM latest_source_outcomes
                     WHERE status = 'failed') AS failed,
                   (SELECT COUNT(*) FROM latest_source_outcomes
                     WHERE status = 'quarantined') AS quarantined,
                   COALESCE(
                       EXTRACT(EPOCH FROM percentile_cont(0.95) WITHIN GROUP (
                           ORDER BY completed_at - received_at)
                       FILTER (
                           WHERE status = 'completed'
                             AND completed_at IS NOT NULL)) * 1000,
                       0)::bigint AS latency_p95_ms,
                   MAX(completed_at) FILTER (WHERE status = 'completed') AS last_completed_at
              FROM recent
            """)
        .query(
            (resultSet, rowNumber) ->
                new IngestionMetrics(
                    resultSet.getLong("completed"),
                    resultSet.getLong("failed"),
                    resultSet.getLong("quarantined"),
                    resultSet.getLong("latency_p95_ms"),
                    nullableInstant(resultSet, "last_completed_at")))
        .single();
  }

  private @Nullable ReconciliationSummary latestReconciliation() {
    return jdbc.sql(
            """
            SELECT id::text AS id,
                   mode,
                   scope,
                   status,
                   discrepancies,
                   repaired,
                   started_at,
                   completed_at
              FROM reconciliation_runs
             ORDER BY started_at DESC
             LIMIT 1
            """)
        .query(
            (resultSet, rowNumber) ->
                new ReconciliationSummary(
                    resultSet.getString("id"),
                    resultSet.getString("mode"),
                    resultSet.getString("scope"),
                    resultSet.getString("status"),
                    resultSet.getInt("discrepancies"),
                    resultSet.getInt("repaired"),
                    resultSet.getTimestamp("started_at").toInstant(),
                    nullableInstant(resultSet, "completed_at")))
        .optional()
        .orElse(null);
  }

  private long databaseSizeBytes() {
    return jdbc.sql("SELECT pg_database_size(current_database())").query(Long.class).single();
  }

  private static @Nullable Instant nullableInstant(ResultSet resultSet, String column)
      throws SQLException {
    var timestamp = resultSet.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }

  public record Dashboard(
      Instant generatedAt,
      String status,
      boolean workerEnabled,
      Map<String, String> dependencies,
      CatalogMetrics catalog,
      QueueMetrics queue,
      IngestionMetrics ingestion,
      @Nullable ReconciliationSummary reconciliation,
      long databaseSizeBytes) {}

  public record CatalogMetrics(
      long providers, long modules, long activeVersions, long documents, long downloads) {}

  public record QueueMetrics(
      long queued, long processing, long retry, long completed, long deadLetter) {}

  public record IngestionMetrics(
      long completed,
      long failed,
      long quarantined,
      long latencyP95Ms,
      @Nullable Instant lastCompletedAt) {}

  public record ReconciliationSummary(
      String id,
      String mode,
      String scope,
      String status,
      int discrepancies,
      int repaired,
      Instant startedAt,
      @Nullable Instant completedAt) {}
}
