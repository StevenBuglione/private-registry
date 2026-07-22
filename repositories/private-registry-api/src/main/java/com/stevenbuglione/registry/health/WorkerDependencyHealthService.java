package com.stevenbuglione.registry.health;

import com.stevenbuglione.registry.artifactory.ArtifactoryGateway;
import com.stevenbuglione.registry.eventing.EventingProperties;
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
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

@Service
@ConditionalOnProperty(prefix = "registry.eventing", name = "enabled", havingValue = "true")
public class WorkerDependencyHealthService {

  private static final long PROBE_TIMEOUT_SECONDS = 5;

  private final JdbcClient jdbc;
  private final OpenSearchClient openSearch;
  private final ArtifactoryGateway artifactory;
  private final SqsClient sqs;
  private final S3Client s3;
  private final EventingProperties properties;
  private final ExecutorService probeExecutor = Executors.newVirtualThreadPerTaskExecutor();

  public WorkerDependencyHealthService(
      JdbcClient jdbc,
      OpenSearchClient openSearch,
      ArtifactoryGateway artifactory,
      SqsClient sqs,
      S3Client s3,
      EventingProperties properties) {
    this.jdbc = jdbc;
    this.openSearch = openSearch;
    this.artifactory = artifactory;
    this.sqs = sqs;
    this.s3 = s3;
    this.properties = properties;
  }

  public WorkerHealthReport check() {
    var probes = new LinkedHashMap<String, Future<Boolean>>();
    probes.put("postgresql", submit(() -> jdbc.sql("SELECT 1").query(Integer.class).single() == 1));
    probes.put("opensearch", submit(() -> openSearch.cluster().health() != null));
    probes.put("artifactory", submit(artifactory::ping));
    probes.put("sqs", submit(this::checkSqs));
    probes.put("s3", submit(this::checkS3));

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

  private boolean checkSqs() {
    if (properties.queueUrl().isBlank()) {
      return false;
    }
    return sqs.getQueueAttributes(
            GetQueueAttributesRequest.builder()
                .queueUrl(properties.queueUrl())
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build())
        != null;
  }

  private boolean checkS3() {
    return s3.headBucket(HeadBucketRequest.builder().bucket(properties.documentBucket()).build())
        != null;
  }

  @PreDestroy
  void shutdownProbeExecutor() {
    probeExecutor.shutdownNow();
  }

  public record WorkerHealthReport(boolean ready, Map<String, String> dependencies) {}
}
