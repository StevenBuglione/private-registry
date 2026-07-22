package com.stevenbuglione.registry.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record CatalogPage<T>(
    List<T> items, @JsonProperty("next_cursor") @Nullable String nextCursor, long total) {
  public CatalogPage {
    items = List.copyOf(items);
  }
}
