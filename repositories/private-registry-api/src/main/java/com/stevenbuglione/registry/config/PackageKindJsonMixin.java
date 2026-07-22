package com.stevenbuglione.registry.config;

import com.fasterxml.jackson.annotation.JsonValue;
import com.stevenbuglione.registry.model.PackageKind;
import org.springframework.boot.jackson.JacksonMixin;

@JacksonMixin(PackageKind.class)
public abstract class PackageKindJsonMixin {

  @JsonValue
  abstract String jsonValue();
}
