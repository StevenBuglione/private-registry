package com.stevenbuglione.registry.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stevenbuglione.registry.model.Symbol;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.jackson.JacksonMixin;

@JacksonMixin(Symbol.class)
public abstract class SymbolJsonMixin {

  @JsonProperty("default_value")
  abstract @Nullable String defaultValue();
}
