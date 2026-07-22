package com.stevenbuglione.registry.config;

import java.net.URISyntaxException;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenSearchConfiguration {

  @Bean(destroyMethod = "close")
  OpenSearchTransport openSearchTransport(OpenSearchProperties properties)
      throws URISyntaxException {
    var host = HttpHost.create(properties.endpoint().toString());
    return ApacheHttpClient5TransportBuilder.builder(host)
        .setMapper(new JacksonJsonpMapper())
        .build();
  }

  @Bean
  OpenSearchClient openSearchClient(OpenSearchTransport transport) {
    return new OpenSearchClient(transport);
  }
}
