package com.stevenbuglione.registry.ingestion;

import com.stevenbuglione.registry.eventing.EventingProperties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "registry.ingestion", name = "enabled", havingValue = "true")
public class PostgresCatalogEventWorker {

  private final CatalogEventQueueRepository queue;
  private final IngestionEventRepository eventJournal;
  private final CatalogIngestionService ingestion;
  private final EventingProperties eventing;
  private final AtomicBoolean processing = new AtomicBoolean();

  public PostgresCatalogEventWorker(
      CatalogEventQueueRepository queue,
      IngestionEventRepository eventJournal,
      CatalogIngestionService ingestion,
      EventingProperties eventing) {
    this.queue = queue;
    this.eventJournal = eventJournal;
    this.ingestion = ingestion;
    this.eventing = eventing;
  }

  @Scheduled(fixedDelayString = "${registry.eventing.poll-interval:30s}")
  public void processAvailableEvents() {
    if (!processing.compareAndSet(false, true)) {
      return;
    }
    try {
      for (var processed = 0; processed < eventing.pollBatchSize(); processed++) {
        var item = queue.claimOne();
        if (item.isEmpty()) {
          return;
        }
        process(item.orElseThrow());
      }
    } finally {
      processing.set(false);
    }
  }

  @Scheduled(fixedDelayString = "${registry.eventing.claim-recovery-delay:1m}")
  public void recoverStaleClaims() {
    queue.recoverStaleClaims(eventing.claimTimeout());
    eventJournal.recoverStaleClaims(eventing.claimTimeout());
  }

  @Scheduled(
      fixedDelayString = "${registry.eventing.retention-cleanup-delay:24h}",
      initialDelayString = "${registry.eventing.retention-cleanup-delay:24h}")
  public void purgeTerminalEvents() {
    queue.purgeTerminalEvents(eventing.completedRetention(), eventing.deadLetterRetention());
    eventJournal.purgeTerminalEvents(eventing.completedRetention(), eventing.deadLetterRetention());
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
