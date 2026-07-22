package com.stevenbuglione.registry.eventing.webhook;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("registry.eventing.webhook")
public record JfrogWebhookProperties(
    boolean enabled,
    String secret,
    Set<String> allowedOrigins,
    String subscriptionId,
    Set<String> allowedRepositories,
    Set<String> allowedPathPrefixes,
    int maximumPayloadBytes) {

  private static final Set<String> DEFAULT_REPOSITORIES =
      Set.of("iac-provider-release-local", "iac-module-release-local", "iac-catalog-release-local");
  private static final Set<String> DEFAULT_PATH_PREFIXES =
      Set.of(
          "v1/",
          "hashicorp/",
          "datadog/",
          "grafana/",
          "terraform-aws-modules/",
          "terraform-google-modules/",
          "Azure/",
          "gruntwork-io/",
          "particuleio/",
          "terraform-module/",
          "cloudposse/",
          "philips-labs/",
          "registry/");

  public JfrogWebhookProperties {
    if (secret == null) {
      secret = "";
    }
    allowedOrigins = allowedOrigins == null ? Set.of() : Set.copyOf(allowedOrigins);
    if (subscriptionId == null) {
      subscriptionId = "";
    }
    allowedRepositories =
        allowedRepositories == null || allowedRepositories.isEmpty()
            ? DEFAULT_REPOSITORIES
            : Set.copyOf(allowedRepositories);
    allowedPathPrefixes =
        allowedPathPrefixes == null || allowedPathPrefixes.isEmpty()
            ? DEFAULT_PATH_PREFIXES
            : Set.copyOf(allowedPathPrefixes);
    if (maximumPayloadBytes < 1) {
      maximumPayloadBytes = 262_144;
    }
  }
}
