package com.stevenbuglione.registry.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.stevenbuglione.registry.health.WorkerDependencyHealthService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkerHealthControllerTest {

  @Mock private WorkerDependencyHealthService health;

  @Test
  void returnsServiceUnavailableAndNamedDependenciesWhenAWorkerAdapterIsDown() {
    var dependencies =
        Map.of(
            "postgresql", "up",
            "artifactory", "down");
    when(health.check())
        .thenReturn(new WorkerDependencyHealthService.WorkerHealthReport(false, dependencies));

    var response = new WorkerHealthController(health).worker();

    assertThat(response.getStatusCode().value()).isEqualTo(503);
    assertThat(response.getBody()).containsEntry("status", "not_ready");
    assertThat(response.getBody()).containsEntry("dependencies", dependencies);
  }
}
