package com.stevenbuglione.registry.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CatalogPage<T>(List<T> items, @JsonProperty("next_cursor") String nextCursor, long total) {
    public CatalogPage {
        items = List.copyOf(items);
    }
}
