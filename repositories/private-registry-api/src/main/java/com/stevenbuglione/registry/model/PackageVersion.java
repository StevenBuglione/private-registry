package com.stevenbuglione.registry.model;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record PackageVersion(
    String version,
    Instant publishedAt,
    String packageDigest,
    String documentationDigest,
    String documentationRoot,
    String artifactRepository,
    String artifactPath,
    String sourceRepository,
    String sourceCommit,
    String sourceTag,
    boolean prerelease,
    boolean deprecated,
    boolean revoked,
    @Nullable DownloadStatistics downloadStatistics) {}
