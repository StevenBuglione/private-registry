package com.stevenbuglione.registry.catalog;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "registry.eventing", name = "enabled", havingValue = "true")
public class PostgresCatalogChangeListener implements SmartLifecycle {

  private static final Logger LOGGER = LoggerFactory.getLogger(PostgresCatalogChangeListener.class);
  private static final String CHANNEL = "registry_catalog_changes";

  private final DataSource dataSource;
  private final JdbcClient jdbc;
  private final ObjectMapper objectMapper;
  private final CatalogChangeNotifier notifier;
  private volatile boolean running;
  private volatile @Nullable Connection activeConnection;
  private @Nullable ExecutorService executor;
  private @Nullable Future<?> listenerTask;

  public PostgresCatalogChangeListener(
      DataSource dataSource,
      JdbcClient jdbc,
      ObjectMapper objectMapper,
      CatalogChangeNotifier notifier) {
    this.dataSource = dataSource;
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.notifier = notifier;
  }

  @Override
  public synchronized void start() {
    if (running) {
      return;
    }
    var newExecutor =
        Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("registry-pg-listener").factory());
    running = true;
    try {
      listenerTask = newExecutor.submit(this::listenUntilStopped);
      executor = newExecutor;
    } catch (RuntimeException exception) {
      running = false;
      newExecutor.shutdownNow();
      throw exception;
    }
  }

  @Override
  public synchronized void stop() {
    running = false;
    var task = listenerTask;
    listenerTask = null;
    if (task != null && !task.isDone() && !task.cancel(true)) {
      LOGGER.warn("Catalog notification listener did not accept cancellation");
    }
    var currentExecutor = executor;
    executor = null;
    closeActiveConnection();
    if (currentExecutor != null) {
      currentExecutor.shutdownNow();
      awaitTermination(currentExecutor);
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

  private void listenUntilStopped() {
    while (running && !Thread.currentThread().isInterrupted()) {
      try (Connection connection = dataSource.getConnection()) {
        listen(connection);
      } catch (SQLException | RuntimeException exception) {
        if (running) {
          LOGGER.warn("Catalog notification listener disconnected; retrying", exception);
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
      var postgres = connection.unwrap(PGConnection.class);
      while (running && !Thread.currentThread().isInterrupted()) {
        forward(postgres.getNotifications(5_000));
      }
    } finally {
      if (Objects.equals(activeConnection, connection)) {
        activeConnection = null;
      }
    }
  }

  private void forward(PGNotification @Nullable [] notifications) {
    if (notifications == null) {
      return;
    }
    for (var notification : notifications) {
      forward(notification.getParameter());
    }
  }

  void forward(String payload) {
    try {
      var document = objectMapper.readTree(payload);
      var packageIdNode = document.path("package_id");
      if (!packageIdNode.isString() || packageIdNode.stringValue().isBlank()) {
        throw new IllegalArgumentException("Catalog notification is missing package_id");
      }
      var packageId = packageIdNode.stringValue();
      var apmIds =
          Set.copyOf(
              jdbc.sql(
                      """
                            SELECT access.apm_id
                              FROM package_apm_access access
                              JOIN packages p ON p.id = access.package_id
                             WHERE CASE
                                       WHEN p.kind = 'module'
                                           THEN 'module/' || p.namespace || '/' || p.name || '/' || p.target
                                       ELSE 'provider/' || p.namespace || '/' || p.name
                                   END = :packageId
                             ORDER BY access.apm_id
                            """)
                  .param("packageId", packageId)
                  .query(String.class)
                  .list());
      notifier.publish(new CatalogChangeEvent(packageId, "indexed", apmIds, Instant.now()));
    } catch (JacksonException | IllegalArgumentException exception) {
      LOGGER.warn("Ignoring malformed catalog notification", exception);
    }
  }

  private static void retryDelay() {
    try {
      Thread.sleep(1_000);
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
      LOGGER.warn("Unable to close the catalog notification connection during shutdown", exception);
    }
  }

  private static void awaitTermination(ExecutorService currentExecutor) {
    try {
      if (!currentExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        LOGGER.warn("Catalog notification listener did not terminate within five seconds");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }
}
