package com.stevenbuglione.registry.ingestion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchOutboxProcessorTest {

  @Mock private SearchOutboxRepository outbox;

  @Mock private CatalogSearchIndex searchIndex;

  @Test
  void retriesFailedProjectionAndCompletesTheRecoveredAttempt() {
    var item =
        new SearchOutboxRepository.OutboxItem(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "registry-packages-v1",
            "provider/hashicorp/null:3.2.4",
            Map.of("name", "null"),
            1,
            1);
    var recovered =
        new SearchOutboxRepository.OutboxItem(
            item.id(),
            item.packageVersionId(),
            item.indexName(),
            item.documentId(),
            item.payload(),
            2,
            1);
    var claims = List.of(List.of(item), List.of(recovered));
    var claimIndex = new AtomicInteger();
    when(outbox.claim(25)).thenAnswer(ignored -> claims.get(claimIndex.getAndIncrement()));
    doThrow(new IllegalStateException("OpenSearch unavailable"))
        .doNothing()
        .when(searchIndex)
        .index(item.indexName(), item.documentId(), item.payload());
    var processor =
        new SearchOutboxProcessor(
            outbox,
            searchIndex,
            new IngestionProperties(true, List.of(), null, null, 0, 0, 0, 25, 10, 16));

    processor.publishPendingDocuments();
    processor.publishPendingDocuments();

    verify(outbox).fail(eq(item), any(RuntimeException.class), eq(10));
    verify(outbox).complete(recovered);
    verify(outbox, org.mockito.Mockito.times(2)).activateVersionsBackedByCompletedProjection();
  }

  @Test
  void delegatesStaleClaimRecovery() {
    var processor =
        new SearchOutboxProcessor(
            outbox,
            searchIndex,
            new IngestionProperties(true, List.of(), null, null, 0, 0, 0, 25, 10, 16));

    processor.recoverStaleClaims();

    verify(outbox).recoverStaleClaims();
  }
}
