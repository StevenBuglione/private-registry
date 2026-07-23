package com.stevenbuglione.registry.health;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class DependencyHealthService {

  private final JdbcClient jdbc;

  public DependencyHealthService(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public HealthReport check() {
    var dependencies = new LinkedHashMap<String, String>();
    try {
      jdbc.sql("SELECT 1").query(Integer.class).single();
      dependencies.put("postgresql", "up");
    } catch (RuntimeException exception) {
      dependencies.put("postgresql", "down");
    }

    var ready = dependencies.values().stream().noneMatch("down"::equals);
    return new HealthReport(ready, Map.copyOf(dependencies));
  }

  public record HealthReport(boolean ready, Map<String, String> dependencies) {}
}
