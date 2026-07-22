package com.stevenbuglione.registry.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.stevenbuglione.registry.model.CatalogPackage;
import com.stevenbuglione.registry.model.PackageKind;
import com.stevenbuglione.registry.model.SearchResult;
import com.stevenbuglione.registry.model.Symbol;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@JsonTest
class CatalogJsonMixinTest {

  private final JsonMapper json;

  CatalogJsonMixinTest(@Autowired JsonMapper json) {
    this.json = json;
  }

  @Test
  void keepsSerializationNamesAtTheJsonAdapterBoundary() throws JacksonException {
    var symbol =
        new Symbol(
            "input",
            "region",
            "Deployment region",
            "variables/region",
            "string",
            "us-east-1",
            false,
            false);
    var catalogPackage =
        new CatalogPackage(
            "module/platform/network/aws",
            PackageKind.MODULE,
            "platform",
            "network",
            "aws",
            "Network",
            "Network module",
            "1.0.0",
            List.of("platform-team"),
            "supported",
            "approved",
            "enterprise-verified",
            "low",
            "registry.example/network",
            Instant.EPOCH,
            List.of(),
            List.of(symbol));
    var searchResult =
        new SearchResult(
            "result-1",
            "module",
            "network",
            "Network module",
            "/modules/platform/network/aws",
            1.0,
            catalogPackage,
            symbol);

    assertThat(json.writeValueAsString(PackageKind.PROVIDER)).isEqualTo("\"provider\"");
    assertThat(json.writeValueAsString(symbol))
        .contains("\"default_value\":\"us-east-1\"")
        .doesNotContain("defaultValue");
    assertThat(json.writeValueAsString(searchResult))
        .contains("\"package\":")
        .doesNotContain("packageDetails");
  }
}
