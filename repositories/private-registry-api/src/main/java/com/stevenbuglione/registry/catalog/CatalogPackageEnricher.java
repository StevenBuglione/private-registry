package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.model.CatalogPackage;
import com.stevenbuglione.registry.model.DownloadStatistics;
import com.stevenbuglione.registry.model.PackageVersion;
import com.stevenbuglione.registry.model.Symbol;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
final class CatalogPackageEnricher {

  private final JdbcClient jdbc;

  CatalogPackageEnricher(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  List<CatalogPackage> enrich(List<CatalogReadRow> rows) {
    if (rows.isEmpty()) {
      return List.of();
    }
    var versions = versions(rows.stream().map(CatalogReadRow::databaseId).toList());
    return rows.stream()
        .map(row -> withVersions(row.item(), versions.getOrDefault(row.databaseId(), List.of())))
        .toList();
  }

  List<Symbol> symbolsForVersion(UUID packageId, String version) {
    return jdbc.sql(
            """
            SELECT s.kind,
                   s.name,
                   s.description,
                   s.document_path,
                   s.symbol_type,
                   s.default_value,
                   s.is_required,
                   s.sensitive
              FROM symbols s
              JOIN package_versions pv ON pv.id = s.package_version_id
             WHERE pv.package_id = :packageId
               AND pv.version = :version
               AND pv.active
               AND NOT pv.revoked
             ORDER BY s.kind, s.name
            """)
        .param("packageId", packageId)
        .param("version", version)
        .query(
            (resultSet, rowNumber) ->
                new Symbol(
                    resultSet.getString("kind"),
                    resultSet.getString("name"),
                    resultSet.getString("description"),
                    resultSet.getString("document_path"),
                    resultSet.getString("symbol_type"),
                    resultSet.getString("default_value"),
                    resultSet.getBoolean("is_required"),
                    resultSet.getBoolean("sensitive")))
        .list();
  }

  private Map<UUID, List<PackageVersion>> versions(List<UUID> packageIds) {
    var versions = new LinkedHashMap<UUID, List<PackageVersion>>();
    jdbc.sql(
            """
            SELECT package_id,
                   version,
                   published_at,
                   package_digest,
                   documentation_digest,
                   documentation_root,
                   artifact_repository,
                   artifact_path,
                   source_repository,
                   source_commit,
                   source_tag,
                   prerelease,
                   deprecated,
                   revoked,
                   latest.download_count,
                   latest.last_downloaded_at,
                   latest.observed_at,
                   CASE
                       WHEN week_baseline.download_count IS NULL THEN NULL
                       ELSE GREATEST(
                           latest.download_count - week_baseline.download_count,
                           0)
                   END AS week_downloads,
                   CASE
                       WHEN month_baseline.download_count IS NULL THEN NULL
                       ELSE GREATEST(
                           latest.download_count - month_baseline.download_count,
                           0)
                   END AS month_downloads,
                   CASE
                       WHEN year_baseline.download_count IS NULL THEN NULL
                       ELSE GREATEST(
                           latest.download_count - year_baseline.download_count,
                           0)
                   END AS year_downloads
              FROM package_versions pv
              LEFT JOIN LATERAL (
                  SELECT download_count, last_downloaded_at, observed_at
                    FROM artifact_download_statistics statistics
                   WHERE statistics.package_version_id = pv.id
                   ORDER BY observed_on DESC
                   LIMIT 1
              ) latest ON true
              LEFT JOIN LATERAL (
                  SELECT download_count
                    FROM artifact_download_statistics statistics
                   WHERE statistics.package_version_id = pv.id
                     AND observed_on <= CURRENT_DATE - 7
                   ORDER BY observed_on DESC
                   LIMIT 1
              ) week_baseline ON true
              LEFT JOIN LATERAL (
                  SELECT download_count
                    FROM artifact_download_statistics statistics
                   WHERE statistics.package_version_id = pv.id
                     AND observed_on <= CURRENT_DATE - 30
                   ORDER BY observed_on DESC
                   LIMIT 1
              ) month_baseline ON true
              LEFT JOIN LATERAL (
                  SELECT download_count
                    FROM artifact_download_statistics statistics
                   WHERE statistics.package_version_id = pv.id
                     AND observed_on <= CURRENT_DATE - 365
                   ORDER BY observed_on DESC
                   LIMIT 1
              ) year_baseline ON true
             WHERE package_id IN (:packageIds)
               AND active
             ORDER BY package_id, %s
            """
                .formatted(CatalogVersionOrdering.NEWEST_FIRST))
        .param("packageIds", packageIds)
        .query(
            (resultSet, rowNumber) ->
                new VersionRow(
                    resultSet.getObject("package_id", UUID.class), mapVersion(resultSet)))
        .list()
        .forEach(
            row ->
                versions
                    .computeIfAbsent(row.packageId(), ignored -> new ArrayList<>())
                    .add(row.version()));
    return versions;
  }

  private static CatalogPackage withVersions(
      CatalogPackage item, List<PackageVersion> packageVersions) {
    return new CatalogPackage(
        item.id(),
        item.kind(),
        item.namespace(),
        item.name(),
        item.target(),
        item.title(),
        item.description(),
        item.latestVersion(),
        item.owners(),
        item.supportLevel(),
        item.lifecycle(),
        item.verification(),
        item.registryTier(),
        item.riskTier(),
        item.sourceAddress(),
        item.updatedAt(),
        List.copyOf(packageVersions),
        List.of());
  }

  private static PackageVersion mapVersion(ResultSet resultSet) throws SQLException {
    var allTime = resultSet.getObject("download_count", Long.class);
    DownloadStatistics statistics = null;
    if (allTime != null) {
      var lastDownloadedAt = resultSet.getTimestamp("last_downloaded_at");
      statistics =
          new DownloadStatistics(
              allTime,
              resultSet.getObject("week_downloads", Long.class),
              resultSet.getObject("month_downloads", Long.class),
              resultSet.getObject("year_downloads", Long.class),
              lastDownloadedAt == null ? null : lastDownloadedAt.toInstant(),
              resultSet.getTimestamp("observed_at").toInstant());
    }
    return new PackageVersion(
        resultSet.getString("version"),
        resultSet.getTimestamp("published_at").toInstant(),
        resultSet.getString("package_digest"),
        resultSet.getString("documentation_digest"),
        resultSet.getString("documentation_root"),
        resultSet.getString("artifact_repository"),
        resultSet.getString("artifact_path"),
        resultSet.getString("source_repository"),
        resultSet.getString("source_commit"),
        resultSet.getString("source_tag"),
        resultSet.getBoolean("prerelease"),
        resultSet.getBoolean("deprecated"),
        resultSet.getBoolean("revoked"),
        statistics);
  }

  private record VersionRow(UUID packageId, PackageVersion version) {}
}
