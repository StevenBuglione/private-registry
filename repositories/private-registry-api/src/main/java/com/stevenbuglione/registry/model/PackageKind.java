package com.stevenbuglione.registry.model;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

public enum PackageKind {
  MODULE,
  PROVIDER;

  public static @Nullable PackageKind from(@Nullable String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return valueOf(value.trim().toUpperCase(Locale.ROOT));
  }

  public String jsonValue() {
    return name().toLowerCase(Locale.ROOT);
  }
}
