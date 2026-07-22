package com.stevenbuglione.registry.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("registry.artifactory")
public record ArtifactoryProperties(
    URI url,
    String accessToken,
    Duration connectionTimeout,
    Duration socketTimeout,
    String moduleRepository,
    String providerRepository) {

  public ArtifactoryProperties {
    if (url == null) {
      url = URI.create("https://trialwbgt07.jfrog.io/artifactory");
    }
    if (accessToken == null) {
      accessToken = "";
    }
    if (connectionTimeout == null) {
      connectionTimeout = Duration.ofSeconds(3);
    }
    if (socketTimeout == null) {
      socketTimeout = Duration.ofSeconds(5);
    }
    if (moduleRepository == null || moduleRepository.isBlank()) {
      moduleRepository = "iac-modules-public-remote";
    }
    if (providerRepository == null || providerRepository.isBlank()) {
      providerRepository = "iac-providers-public-remote";
    }
  }

  public boolean hasAccessToken() {
    return !accessToken.isBlank();
  }
}
