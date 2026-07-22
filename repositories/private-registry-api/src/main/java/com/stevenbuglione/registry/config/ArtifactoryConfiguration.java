package com.stevenbuglione.registry.config;

import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactoryClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ArtifactoryConfiguration {

  @Bean(destroyMethod = "close")
  Artifactory artifactory(ArtifactoryProperties properties) {
    var builder =
        ArtifactoryClientBuilder.create()
            .setUrl(properties.url().toString())
            .setConnectionTimeout(Math.toIntExact(properties.connectionTimeout().toMillis()))
            .setSocketTimeout(Math.toIntExact(properties.socketTimeout().toMillis()))
            .setUserAgent("private-registry-api");
    if (properties.hasAccessToken()) {
      builder.setAccessToken(properties.accessToken());
    }
    return builder.build();
  }
}
