package com.stevenbuglione.registry.ingestion;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.stevenbuglione.registry.eventing.CatalogArtifactChanged;
import com.stevenbuglione.registry.eventing.EventingProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostgresCatalogEventWorkerTest {

  private static final int MAXIMUM_ATTEMPTS = 5;

  @Mock private CatalogEventQueueRepository queue;
  @Mock private IngestionEventRepository eventJournal;
  @Mock private CatalogIngestionService ingestion;

  private PostgresCatalogEventWorker worker;

  @BeforeEach
  void setUp() {
    worker =
        new PostgresCatalogEventWorker(
            queue,
            eventJournal,
            ingestion,
            new EventingProperties(
                true,
                Duration.ofSeconds(30),
                Duration.ofMinutes(1),
                Duration.ofMinutes(5),
                25,
                MAXIMUM_ATTEMPTS,
                Duration.ofDays(7),
                Duration.ofDays(90)));
  }

  @Test
  void completesSuccessfullyProcessedEvents() {
    var item = item(1);
    when(queue.claimOne()).thenReturn(Optional.of(item)).thenReturn(Optional.empty());
    when(ingestion.accept(item.event())).thenReturn(CatalogIngestionService.Outcome.COMPLETED);

    worker.processAvailableEvents();

    verify(queue).complete(item);
  }

  @Test
  void completesDuplicateEventsIdempotently() {
    var item = item(1);
    when(queue.claimOne()).thenReturn(Optional.of(item)).thenReturn(Optional.empty());
    when(ingestion.accept(item.event())).thenReturn(CatalogIngestionService.Outcome.DUPLICATE);

    worker.processAvailableEvents();

    verify(queue).complete(item);
  }

  @Test
  void deadLettersQuarantinedEvents() {
    var item = item(1);
    when(queue.claimOne()).thenReturn(Optional.of(item)).thenReturn(Optional.empty());
    when(ingestion.accept(item.event())).thenReturn(CatalogIngestionService.Outcome.QUARANTINED);

    worker.processAvailableEvents();

    verify(queue).deadLetterQuarantined(item);
  }

  @Test
  void retriesUnexpectedFailuresUsingTheConfiguredAttemptLimit() {
    var item = item(2);
    var failure = new IllegalStateException("Artifactory unavailable");
    when(queue.claimOne()).thenReturn(Optional.of(item)).thenReturn(Optional.empty());
    when(ingestion.accept(item.event())).thenThrow(failure);

    worker.processAvailableEvents();

    verify(ingestion).accept(item.event());
    verify(queue).retryOrDeadLetter(item, failure, MAXIMUM_ATTEMPTS);
    verifyNoMoreInteractions(ingestion);
  }

  @Test
  void delegatesStaleClaimRecoveryToTheDatabaseQueue() {
    worker.recoverStaleClaims();

    verify(queue).recoverStaleClaims(Duration.ofMinutes(5));
    verify(eventJournal).recoverStaleClaims(Duration.ofMinutes(5));
  }

  @Test
  void purgesTerminalQueueAndJournalRecordsUsingConfiguredRetention() {
    worker.purgeTerminalEvents();

    verify(queue).purgeTerminalEvents(Duration.ofDays(7), Duration.ofDays(90));
    verify(eventJournal).purgeTerminalEvents(Duration.ofDays(7), Duration.ofDays(90));
  }

  private static CatalogEventQueueRepository.QueueItem item(int attempts) {
    return new CatalogEventQueueRepository.QueueItem(
        UUID.randomUUID(), event(), attempts, UUID.randomUUID());
  }

  private static CatalogArtifactChanged event() {
    return new CatalogArtifactChanged(
        1,
        "event-1",
        CatalogArtifactChanged.Action.DEPLOYED,
        "jfrog.local",
        "subscription-1",
        "iac-catalog-release-local",
        "v1/providers/hashicorp/null/3.2.4/catalog-manifest.json",
        Instant.parse("2026-07-22T20:00:00Z"),
        "correlation-1",
        Map.of());
  }
}
