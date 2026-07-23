package com.stevenbuglione.registry.ingestion;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "registry.ingestion", name = "enabled", havingValue = "true")
public class PostgresCatalogQueueListener implements SmartLifecycle {

  private static final Logger LOGGER = LoggerFactory.getLogger(PostgresCatalogQueueListener.class);
  private static final String CHANNEL = "registry_catalog_work";

  private final DataSource dataSource;
  private final PostgresCatalogEventWorker worker;
  private final Lock lifecycleLock = new ReentrantLock();
  private volatile boolean running;
  private volatile @Nullable Connection activeConnection;
  private @Nullable ExecutorService executor;
  private @Nullable Future<?> listenerTask;

  public PostgresCatalogQueueListener(DataSource dataSource, PostgresCatalogEventWorker worker) {
    this.dataSource = dataSource;
    this.worker = worker;
  }

  @Override
  public void start() {
    lifecycleLock.lock();
    try {
      if (running) {
        return;
      }
      executor =
          Executors.newSingleThreadExecutor(
              Thread.ofVirtual().name("registry-catalog-queue-listener").factory());
      running = true;
      try {
        listenerTask = executor.submit(this::listenUntilStopped);
      } catch (RuntimeException exception) {
        running = false;
        shutdownExecutor();
        throw exception;
      }
    } finally {
      lifecycleLock.unlock();
    }
  }

  @Override
  public void stop() {
    lifecycleLock.lock();
    try {
      running = false;
      cancelListenerTask();
      closeActiveConnection();
      shutdownExecutor();
    } finally {
      lifecycleLock.unlock();
    }
  }

  @Override
  public void stop(Runnable callback) {
    stop();
    callback.run();
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  private void cancelListenerTask() {
    var task = listenerTask;
    listenerTask = null;
    if (task != null && !task.isDone() && !task.cancel(true)) {
      LOGGER.warn("Catalog queue notification listener did not accept cancellation");
    }
  }

  // ExecutorService is explicitly shut down and awaited before this method returns.
  @SuppressWarnings("PMD.CloseResource")
  private void shutdownExecutor() {
    if (executor != null) {
      var currentExecutor = executor;
      executor = null;
      currentExecutor.shutdownNow();
      awaitExecutorTermination(currentExecutor);
    }
  }

  private void listenUntilStopped() {
    while (running && !Thread.currentThread().isInterrupted()) {
      try (Connection connection = dataSource.getConnection()) {
        listen(connection);
      } catch (SQLException | RuntimeException exception) {
        if (running) {
          LOGGER.warn("Catalog queue notification listener disconnected; retrying", exception);
          retryDelay();
        }
      }
    }
  }

  private void listen(Connection connection) throws SQLException {
    activeConnection = connection;
    connection.setAutoCommit(true);
    try {
      try (var statement = connection.createStatement()) {
        statement.execute("LISTEN " + CHANNEL);
      }
      worker.processAvailableEvents();
      var postgres = connection.unwrap(PGConnection.class);
      while (running && !Thread.currentThread().isInterrupted()) {
        var notifications = postgres.getNotifications(30_000);
        if (notifications != null && notifications.length > 0) {
          worker.processAvailableEvents();
        }
      }
    } finally {
      if (Objects.equals(activeConnection, connection)) {
        activeConnection = null;
      }
    }
  }

  private static void retryDelay() {
    try {
      Thread.sleep(5_000);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }

  // The listener owns this long-lived connection and closes it explicitly to unblock the driver.
  @SuppressWarnings("PMD.CloseResource")
  private void closeActiveConnection() {
    var connection = activeConnection;
    activeConnection = null;
    if (connection == null) {
      return;
    }
    try {
      connection.close();
    } catch (SQLException exception) {
      LOGGER.warn("Unable to close the catalog queue connection during shutdown", exception);
    }
  }

  // ExecutorService is explicitly shut down before this bounded termination wait.
  @SuppressWarnings("PMD.CloseResource")
  private static void awaitExecutorTermination(ExecutorService currentExecutor) {
    try {
      if (!currentExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        LOGGER.warn("Catalog queue notification listener did not terminate within five seconds");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }
}
