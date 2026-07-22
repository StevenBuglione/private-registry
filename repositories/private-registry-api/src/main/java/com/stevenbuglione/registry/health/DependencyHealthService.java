package com.stevenbuglione.registry.health;

import java.util.LinkedHashMap;
import java.util.Map;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class DependencyHealthService {

  private final JdbcClient jdbc;
  private final OpenSearchClient openSearch;

  public DependencyHealthService(JdbcClient jdbc, OpenSearchClient openSearch) {
    this.jdbc = jdbc;
    this.openSearch = openSearch;
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

    var ready = dependencies.values().stream().noneMatch("down"::equals);
    return new HealthReport(ready, Map.copyOf(dependencies));
  }

  public record HealthReport(boolean ready, Map<String, String> dependencies) {}
}
