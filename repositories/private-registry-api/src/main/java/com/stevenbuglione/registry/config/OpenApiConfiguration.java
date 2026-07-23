package com.stevenbuglione.registry.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

  private static final List<RouteGroup> ROUTE_GROUPS =
      List.of(
          new RouteGroup("/api/v1/auth/", "Authentication"),
          new RouteGroup("/api/v1/catalog/", "Catalog"),
          new RouteGroup("/api/v1/registry/homepage", "Homepage"),
          new RouteGroup("/api/v1/admin/sync-credentials", "Sync API keys"),
          new RouteGroup("/api/v1/admin/traffic", "Traffic analytics"),
          new RouteGroup("/api/v1/admin/", "Administration"),
          new RouteGroup("/api/v1/analytics/", "Traffic analytics"),
          new RouteGroup("/api/v1/sync/artifacts", "Artifact sync"),
          new RouteGroup("/api/v1/artifactory/status", "Artifactory"),
          new RouteGroup("/registry/", "Compatibility"),
          new RouteGroup("/top/", "Compatibility"),
          new RouteGroup("/api/v1/enterprise/", "Compatibility"),
          new RouteGroup("/internal/webhooks/", "Webhook"));

  @Bean
  OpenAPI registryOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Registry API")
                .version("1.0.0")
                .description(
                    """
                    Live API reference generated from the running Spring controllers.
                    Interactive requests use the current authenticated Registry session.
                    """))
        .servers(List.of(new Server().url("/").description("Current Registry deployment")))
        .components(
            new Components()
                .addSecuritySchemes(
                    "syncCredential",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("RegistrySyncCredential")
                        .description(
                            "Scoped runner credential used only by the artifact sync endpoint.")))
        .tags(
            List.of(
                tag("Authentication", "Signed-in Registry session operations."),
                tag("Catalog", "Entitlement-filtered package discovery and documentation."),
                tag("Homepage", "Registry homepage configuration."),
                tag("Administration", "Administrator-only operations and audit data."),
                tag("Sync API keys", "Scoped runner credential lifecycle."),
                tag(
                    "Traffic analytics",
                    "Authenticated page-view collection and administrator-only reporting."),
                tag("Artifact sync", "Runner-triggered artifact synchronization."),
                tag("Artifactory", "Artifactory dependency status."),
                tag("Compatibility", "Terraform Registry-compatible application routes."),
                tag("Webhook", "Signed JFrog event intake."),
                tag("Health", "Application and worker health endpoints.")));
  }

  @Bean
  OpenApiCustomizer registryOperationGroups() {
    return openApi -> {
      var paths = openApi.getPaths();
      if (paths == null) {
        return;
      }
      paths.forEach(
          (path, item) ->
              item.readOperations().forEach(operation -> operation.setTags(List.of(tagFor(path)))));
      var syncPath = paths.get("/api/v1/sync/artifacts");
      if (syncPath != null && syncPath.getPost() != null) {
        syncPath
            .getPost()
            .setSecurity(List.of(new SecurityRequirement().addList("syncCredential")));
      }
    };
  }

  private static Tag tag(String name, String description) {
    return new Tag().name(name).description(description);
  }

  private static String tagFor(String path) {
    return ROUTE_GROUPS.stream()
        .filter(group -> path.startsWith(group.prefix()))
        .map(RouteGroup::tag)
        .findFirst()
        .orElse("Health");
  }

  private record RouteGroup(String prefix, String tag) {}
}
