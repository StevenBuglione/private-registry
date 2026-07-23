package com.stevenbuglione.registry.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class CatalogManifestV1Test {

  @Test
  void acceptsARestrictedApmAssignedManifest() {
    var manifest = manifest(List.of("APM0000001"));

    manifest.validate();

    assertThat(manifest.publicId()).isEqualTo("provider/hashicorp/aws");
  }

  @Test
  void quarantinesManifestWithoutApmAssignments() {
    var manifest = manifest(List.of());

    assertThatThrownBy(manifest::validate)
        .isInstanceOf(QuarantineException.class)
        .extracting(exception -> ((QuarantineException) exception).code())
        .isEqualTo("missing_apm_assignment");
  }

  @Test
  void acceptsCompleteSymbolMetadata() {
    var manifest =
        manifest(
            List.of("APM0000001"),
            List.of(
                new CatalogManifestV1.Symbol(
                    "input",
                    "region",
                    "AWS region.",
                    "variables.tf",
                    "string",
                    "\"us-east-1\"",
                    false,
                    false)));

    manifest.validate();

    assertThat(Objects.requireNonNull(manifest.symbols()).getFirst())
        .extracting(
            CatalogManifestV1.Symbol::type,
            CatalogManifestV1.Symbol::defaultValue,
            CatalogManifestV1.Symbol::required,
            CatalogManifestV1.Symbol::sensitive)
        .containsExactly("string", "\"us-east-1\"", false, false);
  }

  @Test
  void mapsRawHclDefaultToTheStableJsonField() throws Exception {
    var mapper = new ObjectMapper();
    var symbol =
        new CatalogManifestV1.Symbol(
            "input", "cidrs", null, null, "list(string)", "[\"10.0.0.0/8\"]", false, false);

    var json = mapper.writeValueAsString(symbol);
    var decoded = mapper.readValue(json, CatalogManifestV1.Symbol.class);

    assertThat(json).contains("\"default_value\":\"[\\\"10.0.0.0/8\\\"]\"");
    assertThat(json).doesNotContain("defaultValue");
    assertThat(decoded.defaultValue()).isEqualTo("[\"10.0.0.0/8\"]");
  }

  @Test
  void quarantinesDuplicateAndUnsafeSymbols() {
    var duplicate =
        new CatalogManifestV1.Symbol("input", "region", null, null, null, null, true, false);
    assertThatThrownBy(
            () -> manifest(List.of("APM0000001"), List.of(duplicate, duplicate)).validate())
        .isInstanceOf(QuarantineException.class)
        .extracting(exception -> ((QuarantineException) exception).code())
        .isEqualTo("duplicate_symbol");

    var unsafe =
        new CatalogManifestV1.Symbol(
            "output", "id", null, "../secrets.md", null, null, false, true);
    assertThatThrownBy(() -> manifest(List.of("APM0000001"), List.of(unsafe)).validate())
        .isInstanceOf(QuarantineException.class)
        .extracting(exception -> ((QuarantineException) exception).code())
        .isEqualTo("unsafe_artifact_path");

    var legacyKind =
        new CatalogManifestV1.Symbol(
            "datasource", "identity", null, null, null, null, false, false);
    assertThatThrownBy(() -> manifest(List.of("APM0000001"), List.of(legacyKind)).validate())
        .isInstanceOf(QuarantineException.class)
        .extracting(exception -> ((QuarantineException) exception).code())
        .isEqualTo("invalid_symbol_kind");
  }

  @Test
  void acceptsSameSymbolNameInDifferentModuleChildren() {
    var first =
        new CatalogManifestV1.Symbol(
            "input", "enabled", null, "modules/one/variables.tf", "bool", "true", false, false);
    var second =
        new CatalogManifestV1.Symbol(
            "input", "enabled", null, "modules/two/variables.tf", "bool", "true", false, false);

    manifest(List.of("APM0000001"), List.of(first, second)).validate();
  }

  @Test
  void quarantinesEveryMissingRequiredManifestField() throws Exception {
    var mapper = new ObjectMapper();
    ObjectNode valid = mapper.valueToTree(manifest(List.of("APM0000001")));
    var requiredPaths =
        List.of(
            List.of("identity", "namespace"),
            List.of("identity", "name"),
            List.of("identity", "version"),
            List.of("display", "title"),
            List.of("display", "description"),
            List.of("display", "supportLevel"),
            List.of("display", "verification"),
            List.of("display", "lifecycle"),
            List.of("display", "riskTier"),
            List.of("display", "visibility"),
            List.of("registry", "repository"),
            List.of("registry", "artifactPath"),
            List.of("source", "repository"),
            List.of("source", "commit"),
            List.of("source", "tag"),
            List.of("release", "packageDigest"),
            List.of("release", "documentationDigest"));

    for (var requiredPath : requiredPaths) {
      var invalid = valid.deepCopy();
      if (requiredPath.size() == 1) {
        invalid.putNull(requiredPath.getFirst());
      } else {
        ((ObjectNode) invalid.get(requiredPath.getFirst())).putNull(requiredPath.getLast());
      }
      var decoded = mapper.treeToValue(invalid, CatalogManifestV1.class);

      assertThatThrownBy(decoded::validate)
          .as("missing required manifest field %s", String.join(".", requiredPath))
          .isInstanceOf(QuarantineException.class);
    }
  }

  private static CatalogManifestV1 manifest(List<String> apmIds) {
    return manifest(apmIds, List.of());
  }

  private static CatalogManifestV1 manifest(
      List<String> apmIds, List<CatalogManifestV1.Symbol> symbols) {
    var digest = "sha256:" + "a".repeat(64);
    return new CatalogManifestV1(
        1,
        "provider",
        new CatalogManifestV1.Identity("hashicorp", "aws", null, "5.100.0"),
        new CatalogManifestV1.Display(
            "AWS Provider",
            "Manage AWS resources.",
            List.of("aws"),
            List.of("cloud-platform"),
            null,
            "supported",
            "enterprise-verified",
            "approved",
            "high",
            "restricted",
            "official",
            List.of("public-cloud")),
        new CatalogManifestV1.RegistryLocation(
            "artifacts.example.invalid",
            "iac-provider-release-local",
            "registry.example.invalid/hashicorp/aws",
            "hashicorp/aws/5.100.0/provider.zip",
            null),
        new CatalogManifestV1.Compatibility(">= 1.8"),
        new CatalogManifestV1.Source("https://example.invalid/source", "abc123", "v5.100.0"),
        new CatalogManifestV1.Release(
            Instant.parse("2026-07-21T12:00:00Z"),
            digest,
            "docs/README.md",
            digest,
            null,
            null,
            null,
            false,
            false,
            false),
        new CatalogManifestV1.Access(apmIds),
        List.of(
            new CatalogManifestV1.Document(
                "README.md", "AWS Provider", "text/markdown", "docs/README.md", digest, 100)),
        symbols);
  }
}
