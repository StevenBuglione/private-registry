package com.stevenbuglione.registry.health;

import com.stevenbuglione.registry.artifactory.ArtifactoryGateway;
import java.util.LinkedHashMap;
import java.util.Map;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class DependencyHealthService {

    private final JdbcClient jdbc;
    private final OpenSearchClient openSearch;
    private final ArtifactoryGateway artifactory;

    public DependencyHealthService(
            JdbcClient jdbc, OpenSearchClient openSearch, ArtifactoryGateway artifactory) {
        this.jdbc = jdbc;
        this.openSearch = openSearch;
        this.artifactory = artifactory;
    }

    public HealthReport check() {
        var dependencies = new LinkedHashMap<String, String>();
        try {
            jdbc.sql("SELECT 1").query(Integer.class).single();
            dependencies.put("postgresql", "up");
        } catch (RuntimeException exception) {
            dependencies.put("postgresql", "down");
        }

        try {
            var health = openSearch.cluster().health();
            dependencies.put("opensearch", health.status().jsonValue());
        } catch (Exception exception) {
            dependencies.put("opensearch", "down");
        }

        try {
            dependencies.put("artifactory", artifactory.ping() ? "up" : "down");
        } catch (RuntimeException exception) {
            dependencies.put("artifactory", "down");
        }

        var ready = dependencies.values().stream().noneMatch("down"::equals);
        return new HealthReport(ready, Map.copyOf(dependencies));
    }

    public record HealthReport(boolean ready, Map<String, String> dependencies) {}
}
