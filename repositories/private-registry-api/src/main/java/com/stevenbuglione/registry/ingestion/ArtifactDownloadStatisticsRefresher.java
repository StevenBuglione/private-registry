package com.stevenbuglione.registry.ingestion;

import com.stevenbuglione.registry.artifactory.ArtifactoryGateway;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Periodically snapshots artifact download counters without coupling catalog reads to JFrog. */
@Service
@ConditionalOnProperty(prefix = "registry.ingestion", name = "enabled", havingValue = "true")
public class ArtifactDownloadStatisticsRefresher {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ArtifactDownloadStatisticsRefresher.class);

  private final JdbcClient jdbc;
  private final ArtifactoryGateway artifactory;

  public ArtifactDownloadStatisticsRefresher(JdbcClient jdbc, ArtifactoryGateway artifactory) {
    this.jdbc = jdbc;
    this.artifactory = artifactory;
  }

  @Scheduled(
      fixedDelayString = "${registry.ingestion.download-statistics-delay:15m}",
      initialDelayString = "${registry.ingestion.download-statistics-initial-delay:30s}")
  public void refresh() {
    activeArtifacts().forEach(this::refreshArtifact);
  }

  private java.util.List<ArtifactVersion> activeArtifacts() {
    return jdbc.sql(
            """
            SELECT id, artifact_repository, artifact_path
              FROM package_versions
             WHERE active AND NOT revoked
             ORDER BY published_at DESC
            """)
        .query(
            (resultSet, rowNumber) ->
                new ArtifactVersion(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getString("artifact_repository"),
                    resultSet.getString("artifact_path")))
        .list();
  }

  private void refreshArtifact(ArtifactVersion artifact) {
    try {
      var statistics =
          artifactory.downloadStatistics(artifact.repository(), artifact.artifactPath());
      jdbc.sql(
              """
              INSERT INTO artifact_download_statistics (
                  package_version_id, observed_on, observed_at,
                  download_count, last_downloaded_at
              ) VALUES (
                  :versionId, :observedOn, :observedAt,
                  :downloadCount, :lastDownloadedAt
              )
              ON CONFLICT (package_version_id, observed_on) DO UPDATE SET
                  observed_at = EXCLUDED.observed_at,
                  download_count = EXCLUDED.download_count,
                  last_downloaded_at = EXCLUDED.last_downloaded_at
              """)
          .param("versionId", artifact.versionId())
          .param("observedOn", LocalDate.ofInstant(statistics.observedAt(), ZoneOffset.UTC))
          .param("observedAt", java.sql.Timestamp.from(statistics.observedAt()))
          .param("downloadCount", statistics.downloadCount())
          .param(
              "lastDownloadedAt",
              statistics.lastDownloadedAt() == null
                  ? null
                  : java.sql.Timestamp.from(statistics.lastDownloadedAt()))
          .update();
    } catch (RuntimeException exception) {
      LOGGER.warn(
          "Unable to refresh Artifactory download statistics for {}/{}",
          artifact.repository(),
          artifact.artifactPath(),
          exception);
    }
  }

  private record ArtifactVersion(UUID versionId, String repository, String artifactPath) {}
}
