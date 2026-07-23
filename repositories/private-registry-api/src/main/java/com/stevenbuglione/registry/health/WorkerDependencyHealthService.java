package com.stevenbuglione.registry.health;

import com.stevenbuglione.registry.storage.ArtifactStorageStatus;
import jakarta.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "registry.ingestion", name = "enabled", havingValue = "true")
public class WorkerDependencyHealthService {

  private static final long PROBE_TIMEOUT_SECONDS = 5;

  private final JdbcClient jdbc;
  private final ArtifactStorageStatus artifactStorage;
  private final ExecutorService probeExecutor = Executors.newVirtualThreadPerTaskExecutor();

  public WorkerDependencyHealthService(JdbcClient jdbc, ArtifactStorageStatus artifactStorage) {
    this.jdbc = jdbc;
    this.artifactStorage = artifactStorage;
  }

  public WorkerHealthReport check() {
    var probes = new LinkedHashMap<String, Future<Boolean>>();
    probes.put("postgresql", submit(() -> jdbc.sql("SELECT 1").query(Integer.class).single() == 1));
    probes.put("artifactory", submit(artifactStorage::ping));

    var dependencies = new LinkedHashMap<String, String>();
    var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(PROBE_TIMEOUT_SECONDS);
    probes.forEach((name, future) -> dependencies.put(name, status(future, deadline)));
    var ready = dependencies.values().stream().allMatch("up"::equals);
    return new WorkerHealthReport(ready, Map.copyOf(dependencies));
  }

  private Future<Boolean> submit(Callable<Boolean> probe) {
    return probeExecutor.submit(probe);
  }

  private static String status(Future<Boolean> future, long deadline) {
    var remaining = deadline - System.nanoTime();
    if (remaining <= 0) {
      future.cancel(true);
      return "down";
    }
    try {
      return Boolean.TRUE.equals(future.get(remaining, TimeUnit.NANOSECONDS)) ? "up" : "down";
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      future.cancel(true);
      return "down";
    } catch (ExecutionException | TimeoutException exception) {
      future.cancel(true);
      return "down";
    }
  }

  @PreDestroy
  void shutdownProbeExecutor() {
    probeExecutor.shutdownNow();
  }

  public record WorkerHealthReport(boolean ready, Map<String, String> dependencies) {}
}
