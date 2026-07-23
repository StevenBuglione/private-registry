CREATE TABLE artifact_download_statistics (
    package_version_id uuid NOT NULL REFERENCES package_versions(id) ON DELETE CASCADE,
    observed_on date NOT NULL,
    observed_at timestamptz NOT NULL,
    download_count bigint NOT NULL CHECK (download_count >= 0),
    last_downloaded_at timestamptz,
    PRIMARY KEY (package_version_id, observed_on)
);

CREATE INDEX artifact_download_statistics_latest_idx
    ON artifact_download_statistics(package_version_id, observed_on DESC);

COMMENT ON TABLE artifact_download_statistics IS
    'Daily snapshots of JFrog Artifactory file statistics. Rolling periods are calculated from these snapshots.';
