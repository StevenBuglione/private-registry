package com.stevenbuglione.registry.web;

import com.stevenbuglione.registry.health.DependencyHealthService;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class HealthController {

    private final DependencyHealthService health;
    private final String version;
    private final Instant startedAt = Instant.now();

    public HealthController(DependencyHealthService health, ObjectProvider<BuildProperties> buildProperties) {
        this.health = health;
        var build = buildProperties.getIfAvailable();
        this.version = build == null ? "development" : build.getVersion();
    }

    @GetMapping("/health/live")
    public Map<String, String> live() {
        return Map.of("status", "live");
    }

    @GetMapping("/health/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        var report = health.check();
        var body = Map.<String, Object>of(
                "status", report.ready() ? "ready" : "not_ready",
                "dependencies", report.dependencies());
        return report.ready()
                ? ResponseEntity.ok(body)
                : ResponseEntity.status(503).body(body);
    }

    @GetMapping("/api/v1/status")
    public Map<String, Object> status() {
        return Map.of("status", "ok", "version", version, "started_at", startedAt);
    }
}
