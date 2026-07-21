package com.stevenbuglione.registry.model;

import java.time.Instant;
import java.util.List;

public record CatalogPackage(
        String id,
        PackageKind kind,
        String namespace,
        String name,
        String target,
        String title,
        String description,
        String latestVersion,
        List<String> owners,
        String supportLevel,
        String lifecycle,
        String verification,
        String riskTier,
        String sourceAddress,
        Instant updatedAt,
        List<PackageVersion> versions,
        List<Symbol> symbols) {}
