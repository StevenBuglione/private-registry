package com.stevenbuglione.registry.model;

import org.jspecify.annotations.Nullable;

public record Symbol(
    String kind,
    String name,
    @Nullable String description,
    String path,
    @Nullable String type,
    @Nullable String defaultValue,
    boolean required,
    boolean sensitive) {

  public Symbol(String kind, String name, String description, String path) {
    this(kind, name, description, path, null, null, false, false);
  }
}
