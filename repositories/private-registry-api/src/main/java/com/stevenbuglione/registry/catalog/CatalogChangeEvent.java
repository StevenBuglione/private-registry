package com.stevenbuglione.registry.catalog;

import java.time.Instant;
import java.util.Set;

public record CatalogChangeEvent(String packageId, String changeType, Set<String> apmIds, Instant occurredAt) {
    public CatalogChangeEvent {
        apmIds = Set.copyOf(apmIds);
    }
}
