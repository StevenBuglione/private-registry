package com.stevenbuglione.registry.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class PostgresCatalogQueueListenerTest {

  @Test
  void startAndStopAreIdempotentAndStopInvokesItsCallback() {
    var listener =
        new PostgresCatalogQueueListener(
            mock(DataSource.class), mock(PostgresCatalogEventWorker.class));
    var callbackInvoked = new AtomicBoolean();

    listener.start();
    listener.start();
    assertThat(listener.isRunning()).isTrue();

    listener.stop(() -> callbackInvoked.set(true));
    listener.stop();

    assertThat(listener.isRunning()).isFalse();
    assertThat(callbackInvoked).isTrue();
  }
}
