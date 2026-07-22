package com.stevenbuglione.registry.model;

import org.jspecify.annotations.Nullable;

public record SearchResult(
    String id,
    String type,
    String name,
    String description,
    String path,
    double score,
    @Nullable CatalogPackage packageDetails,
    @Nullable Symbol symbol) {}
