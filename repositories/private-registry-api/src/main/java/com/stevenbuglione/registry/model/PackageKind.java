package com.stevenbuglione.registry.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum PackageKind {
    MODULE,
    PROVIDER;

    public static PackageKind from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String jsonValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
