package com.stevenbuglione.registry.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stevenbuglione.registry.model.CatalogPackage;
import com.stevenbuglione.registry.model.SearchResult;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.jackson.JacksonMixin;

@JacksonMixin(SearchResult.class)
public abstract class SearchResultJsonMixin {

  @JsonProperty("package")
  abstract @Nullable CatalogPackage packageDetails();
}
