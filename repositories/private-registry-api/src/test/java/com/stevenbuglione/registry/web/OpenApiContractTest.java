package com.stevenbuglione.registry.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class OpenApiContractTest {

  private static final Set<String> HTTP_METHODS =
      Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

  @Test
  void primaryContractIsValidYamlWithResolvableReferencesAndUniqueOperations() throws IOException {
    var contractPath = Path.of("api", "openapi.yaml");
    var document = mapping(new Yaml().load(Files.readString(contractPath)));

    assertThat(document.get("openapi")).isEqualTo("3.1.0");
    var paths = mapping(document.get("paths"));
    assertThat(paths.keySet())
        .contains(
            "/api/v1/auth/session",
            "/api/v1/auth/logout",
            "/api/v1/catalog/packages",
            "/api/v1/catalog/packages/provider/{namespace}/{name}/{version}/documentation",
            "/api/v1/catalog/packages/module/{namespace}/{name}/{target}/{version}/documentation",
            "/api/v1/catalog/events",
            "/internal/webhooks/jfrog",
            "/health/live",
            "/health/ready",
            "/health/worker");
    assertThat(paths.keySet())
        .noneMatch(
            path ->
                path.contains("..")
                    || path.startsWith("/registry/docs")
                    || path.startsWith("/api/v1/enterprise")
                    || path.endsWith("/governance")
                    || path.startsWith("/top/"));

    var operationIds = new ArrayList<String>();
    paths
        .values()
        .forEach(
            pathItem ->
                mapping(pathItem)
                    .forEach(
                        (method, operation) -> {
                          if (HTTP_METHODS.contains(method)) {
                            var operationMap = mapping(operation);
                            assertThat(operationMap).containsKeys("operationId", "responses");
                            operationIds.add((String) operationMap.get("operationId"));
                          }
                        }));
    assertThat(new HashSet<>(operationIds)).hasSameSizeAs(operationIds);

    var references = new ArrayList<String>();
    collectReferences(document, references);
    assertThat(references).isNotEmpty();
    references.forEach(
        reference ->
            assertThat(resolveReference(document, reference))
                .as("OpenAPI reference %s", reference)
                .isNotNull());
  }

  @Test
  void syncCredentialWorkflowIsExplicitAndSafeForSwagger() throws IOException {
    var document = mapping(new Yaml().load(Files.readString(Path.of("api", "openapi.yaml"))));
    var paths = mapping(document.get("paths"));
    var credentialOperations = mapping(paths.get("/api/v1/admin/sync-credentials"));
    var syncOperation = mapping(mapping(paths.get("/api/v1/sync/artifacts")).get("post"));

    assertThat(mapping(credentialOperations.get("get")).get("tags"))
        .isEqualTo(List.of("Sync API keys"));
    assertThat(mapping(credentialOperations.get("post")).get("tags"))
        .isEqualTo(List.of("Sync API keys"));
    assertThat(syncOperation.get("tags")).isEqualTo(List.of("Artifact sync"));
    assertThat(syncOperation.get("security"))
        .isEqualTo(List.of(Map.of("syncCredential", List.of())));

    var components = mapping(document.get("components"));
    var schemes = mapping(components.get("securitySchemes"));
    assertThat(mapping(schemes.get("syncCredential")))
        .containsEntry("type", "http")
        .containsEntry("scheme", "bearer");

    var schemas = mapping(components.get("schemas"));
    var createdCredential = mapping(schemas.get("CreatedSyncCredential"));
    var token = mapping(mapping(createdCredential.get("properties")).get("token"));
    assertThat(token).containsEntry("readOnly", true).doesNotContainKey("writeOnly");
  }

  private static void collectReferences(Object node, List<String> references) {
    if (node instanceof Map<?, ?> map) {
      map.forEach(
          (key, value) -> {
            if ("$ref".equals(key) && value instanceof String reference) {
              references.add(reference);
            }
            collectReferences(value, references);
          });
    } else if (node instanceof Iterable<?> iterable) {
      iterable.forEach(value -> collectReferences(value, references));
    }
  }

  private static @Nullable Object resolveReference(Map<String, Object> document, String reference) {
    if (!reference.startsWith("#/")) {
      return null;
    }
    Object current = document;
    for (var segment : reference.substring(2).split("/", -1)) {
      current = mapping(current).get(segment.replace("~1", "/").replace("~0", "~"));
      if (current == null) {
        return null;
      }
    }
    return current;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> mapping(@Nullable Object value) {
    assertThat(value).isInstanceOf(Map.class);
    return (Map<String, Object>) Objects.requireNonNull(value);
  }
}
