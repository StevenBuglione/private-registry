package com.stevenbuglione.registry.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Symbol(
        String kind,
        String name,
        String description,
        String path,
        String type,
        @JsonProperty("default_value") String defaultValue,
        boolean required,
        boolean sensitive) {

    public Symbol(String kind, String name, String description, String path) {
        this(kind, name, description, path, null, null, false, false);
    }
}
