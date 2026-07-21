package com.stevenbuglione.registry.model;

import java.time.Instant;

public record PackageVersion(
        String version,
        Instant publishedAt,
        String packageDigest,
        String documentationDigest,
        String documentationRoot,
        String sourceCommit,
        boolean prerelease,
        boolean deprecated,
        boolean revoked) {}
