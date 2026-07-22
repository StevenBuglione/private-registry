package com.stevenbuglione.registry.config;

import com.stevenbuglione.registry.security.identity.IdentityProperties;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
public class IdentityConfiguration {

  @Bean
  HttpClient identityHttpClient(IdentityProperties properties) {
    return HttpClient.newBuilder()
        .connectTimeout(properties.graphTimeout())
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }
}
