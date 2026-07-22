package com.stevenbuglione.registry.web;

import com.stevenbuglione.registry.health.WorkerDependencyHealthService;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnBean(WorkerDependencyHealthService.class)
public class WorkerHealthController {

  private final WorkerDependencyHealthService health;

  public WorkerHealthController(WorkerDependencyHealthService health) {
    this.health = health;
  }

  @GetMapping("/health/worker")
  public ResponseEntity<Map<String, Object>> worker() {
    var report = health.check();
    var body =
        Map.<String, Object>of(
            "status",
            report.ready() ? "ready" : "not_ready",
            "dependencies",
            report.dependencies());
    return report.ready() ? ResponseEntity.ok(body) : ResponseEntity.status(503).body(body);
  }
}
