package com.stevenbuglione.registry.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SearchResult(
        String id,
        String type,
        String name,
        String description,
        String path,
        double score,
        @JsonProperty("package") CatalogPackage packageDetails,
        Symbol symbol) {}
