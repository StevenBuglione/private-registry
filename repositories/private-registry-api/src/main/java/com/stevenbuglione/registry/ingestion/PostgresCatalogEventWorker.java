package com.stevenbuglione.registry.ingestion;

import com.stevenbuglione.registry.eventing.EventingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "registry.ingestion", name = "enabled", havingValue = "true")
public class PostgresCatalogEventWorker {

  private final CatalogEventQueueRepository queue;
  private final CatalogIngestionService ingestion;
  private final EventingProperties eventing;

  public PostgresCatalogEventWorker(
      CatalogEventQueueRepository queue,
      CatalogIngestionService ingestion,
      EventingProperties eventing) {
    this.queue = queue;
    this.ingestion = ingestion;
    this.eventing = eventing;
  }

  @Scheduled(fixedDelayString = "${registry.eventing.poll-interval:250ms}")
  public void processAvailableEvents() {
    queue.claim(eventing.pollBatchSize()).forEach(this::process);
  }

  @Scheduled(fixedDelayString = "${registry.eventing.claim-recovery-delay:1m}")
  public void recoverStaleClaims() {
    queue.recoverStaleClaims(eventing.claimTimeout());
  }

  private void process(CatalogEventQueueRepository.QueueItem item) {
    try {
      var outcome = ingestion.accept(item.event());
      if (outcome == CatalogIngestionService.Outcome.QUARANTINED) {
        queue.deadLetterQuarantined(item);
      } else {
        queue.complete(item);
      }
    } catch (RuntimeException exception) {
      queue.retryOrDeadLetter(item, exception, eventing.maximumAttempts());
    }
  }
}
