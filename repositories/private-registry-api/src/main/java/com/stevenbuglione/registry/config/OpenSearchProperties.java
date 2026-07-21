package com.stevenbuglione.registry.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("registry.opensearch")
public record OpenSearchProperties(URI endpoint) {

    public OpenSearchProperties {
        if (endpoint == null) {
            endpoint = URI.create("http://localhost:9200");
        }
    }
}
