package com.stevenbuglione.registry.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.stevenbuglione.registry.model.CatalogPackage;
import java.util.List;
import org.springframework.boot.jackson.JacksonMixin;

@JacksonMixin(CatalogPackage.class)
public abstract class CatalogPackageJsonMixin {

  @JsonIgnore
  abstract List<String> owners();

  @JsonIgnore
  abstract String supportLevel();

  @JsonIgnore
  abstract String lifecycle();

  @JsonIgnore
  abstract String riskTier();
}
