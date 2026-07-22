package com.stevenbuglione.registry.catalog;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
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
@ConditionalOnProperty(
        prefix = "registry.ingestion", name = "enabled", havingValue = "false", matchIfMissing = true)
public class PostgresCatalogChangeListener implements SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresCatalogChangeListener.class);
    private static final String CHANNEL = "registry_catalog_changes";

    private final DataSource dataSource;
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final CatalogChangeNotifier notifier;
    private volatile boolean running;
    private ExecutorService executor;

    public PostgresCatalogChangeListener(
            DataSource dataSource, JdbcClient jdbc, ObjectMapper objectMapper, CatalogChangeNotifier notifier) {
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
        running = true;
        executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("registry-pg-listener").factory());
        executor.submit(this::listenUntilStopped);
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (executor != null) {
            executor.shutdownNow();
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
                connection.setAutoCommit(true);
                try (var statement = connection.createStatement()) {
                    statement.execute("LISTEN " + CHANNEL);
                }
                var postgres = connection.unwrap(PGConnection.class);
                while (running && !Thread.currentThread().isInterrupted()) {
                    var notifications = postgres.getNotifications(5_000);
                    if (notifications == null) {
                        continue;
                    }
                    for (var notification : notifications) {
                        forward(notification.getParameter());
                    }
                }
            } catch (SQLException | RuntimeException exception) {
                if (running) {
                    LOGGER.warn("Catalog notification listener disconnected; retrying", exception);
                    retryDelay();
                }
            }
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
            var apmIds = Set.copyOf(jdbc.sql("""
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
}
