package com.stevenbuglione.registry.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class ReconciliationRunRepositoryTest {

  private final JdbcClient jdbc = mock(JdbcClient.class);
  private final DataSource dataSource = mock(DataSource.class);
  private final Connection connection = mock(Connection.class);
  private final Statement statement = mock(Statement.class);
  private final ResultSet result = mock(ResultSet.class);

  private ReconciliationRunRepository repository;

  @BeforeEach
  void setUp() throws SQLException {
    repository = new ReconciliationRunRepository(jdbc, dataSource);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery("SELECT pg_try_advisory_lock(764391827)")).thenReturn(result);
  }

  @Test
  void executesWorkOnlyWhileTheDatabaseLeaseIsHeld() throws SQLException {
    when(result.next()).thenReturn(true);
    when(result.getBoolean(1)).thenReturn(true);
    var executed = new AtomicBoolean();

    try (var lease = repository.tryAcquireLease().orElseThrow()) {
      lease.execute(() -> executed.set(true));
    }

    assertThat(executed).isTrue();
    verify(statement).execute("SELECT pg_advisory_unlock(764391827)");
    verify(connection).close();
  }

  @Test
  void closesTheConnectionWhenAnotherReplicaOwnsTheLease() throws SQLException {
    when(result.next()).thenReturn(true);
    when(result.getBoolean(1)).thenReturn(false);

    assertThat(repository.tryAcquireLease()).isEmpty();

    verify(connection).close();
  }
}
