package com.stevenbuglione.registry.ingestion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "registry.ingestion", name = "enabled", havingValue = "true")
public class SearchOutboxProcessor {

  private final SearchOutboxRepository outbox;
  private final CatalogSearchIndex searchIndex;
  private final IngestionProperties properties;

  public SearchOutboxProcessor(
      SearchOutboxRepository outbox,
      CatalogSearchIndex searchIndex,
      IngestionProperties properties) {
    this.outbox = outbox;
    this.searchIndex = searchIndex;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${registry.ingestion.outbox-delay:1s}")
  public void publishPendingDocuments() {
    outbox
        .claim(properties.outboxBatchSize())
        .forEach(
            item -> {
              try {
                searchIndex.index(item.indexName(), item.documentId(), item.payload());
                outbox.complete(item);
              } catch (RuntimeException exception) {
                outbox.fail(item, exception, properties.outboxMaximumAttempts());
              }
            });
    outbox.activateVersionsBackedByCompletedProjection();
  }

  @Scheduled(fixedDelayString = "${registry.ingestion.claim-recovery-delay:1m}")
  public void recoverStaleClaims() {
    outbox.recoverStaleClaims();
  }
}
