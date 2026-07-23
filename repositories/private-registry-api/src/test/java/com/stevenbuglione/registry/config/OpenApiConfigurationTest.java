package com.stevenbuglione.registry.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class OpenApiConfigurationTest {

  private final OpenApiConfiguration configuration = new OpenApiConfiguration();

  @Test
  void describesTheLiveRegistryApiAndRunnerSecurityScheme() {
    var openApi = configuration.registryOpenApi();

    assertThat(openApi.getInfo().getTitle()).isEqualTo("Registry API");
    assertThat(openApi.getInfo().getDescription()).contains("running Spring controllers");
    assertThat(openApi.getComponents().getSecuritySchemes())
        .containsKey("syncCredential")
        .extractingByKey("syncCredential")
        .satisfies(
            scheme -> {
              assertThat(scheme.getScheme()).isEqualTo("bearer");
              assertThat(scheme.getBearerFormat()).isEqualTo("RegistrySyncCredential");
            });
  }

  @Test
  void groupsEveryApplicationRouteAndSecuresOnlyRunnerSyncWithTheBearerScheme() {
    var openApi = configuration.registryOpenApi();
    openApi.setPaths(
        new Paths()
            .addPathItem("/api/v1/auth/session", get())
            .addPathItem("/api/v1/catalog/packages", get())
            .addPathItem("/api/v1/registry/homepage", get())
            .addPathItem("/api/v1/admin/dashboard", get())
            .addPathItem("/api/v1/admin/sync-credentials", get())
            .addPathItem("/api/v1/admin/traffic", get())
            .addPathItem("/api/v1/analytics/page-views", post())
            .addPathItem("/api/v1/sync/artifacts", post())
            .addPathItem("/api/v1/artifactory/status", get())
            .addPathItem("/registry/docs/search", get())
            .addPathItem("/api/v1/enterprise/packages/{path}", get())
            .addPathItem("/internal/webhooks/jfrog", post())
            .addPathItem("/health/ready", get())
            .addPathItem("/api/v1/status", get()));

    configuration.registryOperationGroups().customise(openApi);

    assertThat(tags(openApi))
        .containsExactlyInAnyOrderEntriesOf(
            Map.ofEntries(
                Map.entry("/api/v1/auth/session", "Authentication"),
                Map.entry("/api/v1/catalog/packages", "Catalog"),
                Map.entry("/api/v1/registry/homepage", "Homepage"),
                Map.entry("/api/v1/admin/dashboard", "Administration"),
                Map.entry("/api/v1/admin/sync-credentials", "Sync API keys"),
                Map.entry("/api/v1/admin/traffic", "Traffic analytics"),
                Map.entry("/api/v1/analytics/page-views", "Traffic analytics"),
                Map.entry("/api/v1/sync/artifacts", "Artifact sync"),
                Map.entry("/api/v1/artifactory/status", "Artifactory"),
                Map.entry("/registry/docs/search", "Compatibility"),
                Map.entry("/api/v1/enterprise/packages/{path}", "Compatibility"),
                Map.entry("/internal/webhooks/jfrog", "Webhook"),
                Map.entry("/health/ready", "Health"),
                Map.entry("/api/v1/status", "Health")));
    var syncPath = Objects.requireNonNull(openApi.getPaths().get("/api/v1/sync/artifacts"));
    var syncOperation = Objects.requireNonNull(syncPath.getPost());
    assertThat(syncOperation.getSecurity())
        .singleElement()
        .satisfies(requirement -> assertThat(requirement).containsKey("syncCredential"));
  }

  private static PathItem get() {
    return new PathItem().get(new Operation());
  }

  private static PathItem post() {
    return new PathItem().post(new Operation());
  }

  private static Map<String, String> tags(OpenAPI openApi) {
    return openApi.getPaths().entrySet().stream()
        .collect(
            java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().readOperations().getFirst().getTags().getFirst()));
  }
}
