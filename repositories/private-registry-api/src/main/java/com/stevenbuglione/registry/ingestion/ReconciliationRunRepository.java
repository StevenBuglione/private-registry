package com.stevenbuglione.registry.ingestion;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReconciliationRunRepository {

  private final JdbcClient jdbc;
  private final DataSource dataSource;

  public ReconciliationRunRepository(JdbcClient jdbc, DataSource dataSource) {
    this.jdbc = jdbc;
    this.dataSource = dataSource;
  }

  public Optional<ReconciliationLease> tryAcquireLease() {
    Connection connection = null;
    try {
      connection = dataSource.getConnection();
      try (var statement = connection.createStatement();
          var result = statement.executeQuery("SELECT pg_try_advisory_lock(764391827)")) {
        if (!result.next() || !result.getBoolean(1)) {
          connection.close();
          return Optional.empty();
        }
      }
      return Optional.of(new ReconciliationLease(connection));
    } catch (SQLException exception) {
      closeAfterFailure(connection, exception);
      throw new IllegalStateException("Unable to acquire the reconciliation lease", exception);
    }
  }

  public UUID start(String mode, String scope) {
    return jdbc.sql(
            """
                        INSERT INTO reconciliation_runs (mode, scope, status)
                        VALUES (:mode, :scope, 'running')
                        RETURNING id
                        """)
        .param("mode", mode)
        .param("scope", scope)
        .query(UUID.class)
        .single();
  }

  public void failAbandonedRuns() {
    jdbc.sql(
            """
            UPDATE reconciliation_runs
               SET status = 'failed', completed_at = now()
             WHERE status = 'running'
            """)
        .update();
  }

  public void complete(UUID id, int discrepancies, int repaired) {
    jdbc.sql(
            """
                        UPDATE reconciliation_runs
                           SET status = 'completed', discrepancies = :discrepancies,
                               repaired = :repaired, completed_at = now()
                         WHERE id = :id
                        """)
        .param("id", id)
        .param("discrepancies", discrepancies)
        .param("repaired", repaired)
        .update();
  }

  public void fail(UUID id) {
    jdbc.sql(
            """
                        UPDATE reconciliation_runs
                           SET status = 'failed', completed_at = now()
                         WHERE id = :id
                        """)
        .param("id", id)
        .update();
  }

  public Instant checkpoint(String name) {
    return jdbc.sql(
            """
                        SELECT checkpoint_value
                          FROM ingestion_checkpoints
                         WHERE checkpoint_name = :name
                        """)
        .param("name", name)
        .query(String.class)
        .optional()
        .map(Instant::parse)
        .orElse(Instant.EPOCH);
  }

  public void saveCheckpoint(String name, Instant value) {
    jdbc.sql(
            """
                        INSERT INTO ingestion_checkpoints (checkpoint_name, checkpoint_value, updated_at)
                        VALUES (:name, :value, now())
                        ON CONFLICT (checkpoint_name) DO UPDATE
                            SET checkpoint_value = EXCLUDED.checkpoint_value,
                                updated_at = now()
                        """)
        .param("name", name)
        .param("value", value.toString())
        .update();
  }

  private static void closeAfterFailure(@Nullable Connection connection, SQLException failure) {
    if (connection == null) {
      return;
    }
    try {
      connection.close();
    } catch (SQLException closeFailure) {
      failure.addSuppressed(closeFailure);
    }
  }

  public static final class ReconciliationLease implements AutoCloseable {

    private final Connection connection;

    private ReconciliationLease(Connection connection) {
      this.connection = connection;
    }

    public void execute(Runnable work) {
      work.run();
    }

    @Override
    public void close() {
      @Nullable SQLException failure = null;
      try (var statement = connection.createStatement()) {
        statement.execute("SELECT pg_advisory_unlock(764391827)");
      } catch (SQLException exception) {
        failure = exception;
      }
      try {
        connection.close();
      } catch (SQLException exception) {
        if (failure == null) {
          failure = exception;
        } else {
          failure.addSuppressed(exception);
        }
      }
      if (failure != null) {
        throw new IllegalStateException("Unable to release the reconciliation lease", failure);
      }
    }
  }
}
