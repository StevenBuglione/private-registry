package com.stevenbuglione.registry.ingestion;

import java.util.UUID;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReconciliationRunRepository {

    private final JdbcClient jdbc;

    public ReconciliationRunRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID start(String mode, String scope) {
        return jdbc.sql("""
                        INSERT INTO reconciliation_runs (mode, scope, status)
                        VALUES (:mode, :scope, 'running')
                        RETURNING id
                        """)
                .param("mode", mode)
                .param("scope", scope)
                .query(UUID.class)
                .single();
    }

    public void complete(UUID id, int discrepancies, int repaired) {
        jdbc.sql("""
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
        jdbc.sql("""
                        UPDATE reconciliation_runs
                           SET status = 'failed', completed_at = now()
                         WHERE id = :id
                        """)
                .param("id", id)
                .update();
    }

    public Instant checkpoint(String name) {
        return jdbc.sql("""
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
        jdbc.sql("""
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
}
