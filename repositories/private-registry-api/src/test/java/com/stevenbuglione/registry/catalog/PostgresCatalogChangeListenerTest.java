package com.stevenbuglione.registry.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

class PostgresCatalogChangeListenerTest {

  @Test
  // These are Mockito resources; the listener under test closes both through its lifecycle.
  @SuppressWarnings("PMD.CloseResource")
  void shutdownClosesTheNotificationConnectionBeforeInvokingItsCallback() throws Exception {
    var dataSource = mock(DataSource.class);
    var connection = mock(Connection.class);
    var statement = mock(Statement.class);
    var postgres = mock(PGConnection.class);
    var notificationWaitStarted = new CountDownLatch(1);
    var connectionClosed = new Semaphore(0);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(connection.unwrap(PGConnection.class)).thenReturn(postgres);
    when(postgres.getNotifications(5_000))
        .thenAnswer(
            ignored -> {
              notificationWaitStarted.countDown();
              connectionClosed.acquireUninterruptibly();
              return null;
            });
    org.mockito.Mockito.doAnswer(
            ignored -> {
              connectionClosed.release();
              return null;
            })
        .when(connection)
        .close();
    var listener =
        new PostgresCatalogChangeListener(
            dataSource,
            mock(JdbcClient.class),
            new ObjectMapper(),
            mock(CatalogChangeNotifier.class));
    var callbackInvoked = new AtomicBoolean();

    listener.start();
    assertThat(notificationWaitStarted.await(2, TimeUnit.SECONDS)).isTrue();
    listener.stop(() -> callbackInvoked.set(true));

    verify(connection, atLeastOnce()).close();
    assertThat(listener.isRunning()).isFalse();
    assertThat(callbackInvoked).isTrue();
  }
}
