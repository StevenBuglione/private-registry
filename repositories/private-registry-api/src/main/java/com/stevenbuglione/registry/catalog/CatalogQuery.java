package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.model.PackageKind;
import java.util.Set;

public record CatalogQuery(
        String q,
        PackageKind kind,
        String provider,
        String apmId,
        String lifecycle,
        String approval,
        String risk,
        String sort,
        String cursor,
        int limit) {

    private static final Set<String> SORTS = Set.of("updated", "name", "risk", "relevance");

    public CatalogQuery {
        q = normalize(q);
        provider = normalize(provider);
        apmId = normalize(apmId);
        lifecycle = normalize(lifecycle);
        approval = normalize(approval);
        risk = normalize(risk);
        sort = normalize(sort);
        cursor = normalize(cursor);
        sort = sort == null ? "updated" : sort;
        if ("relevance".equals(sort) && q == null) {
            sort = "updated";
        }
        if (!SORTS.contains(sort)) {
            throw new IllegalArgumentException("sort must be one of relevance, updated, name, or risk");
        }
        limit = limit <= 0 ? 25 : Math.min(limit, 100);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
