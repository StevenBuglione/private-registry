package com.stevenbuglione.registry.ingestion;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.stevenbuglione.registry.catalog.CatalogChangeEvent;
import java.time.Instant;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SearchOutboxRepository {

    private static final TypeReference<Map<String, Object>> DOCUMENT_TYPE = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final CatalogActivationNotifier activationNotifier;
    private final com.stevenbuglione.registry.catalog.CatalogChangeNotifier liveNotifier;

    public SearchOutboxRepository(
            JdbcClient jdbc,
            ObjectMapper objectMapper,
            CatalogActivationNotifier activationNotifier,
            com.stevenbuglione.registry.catalog.CatalogChangeNotifier liveNotifier) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.activationNotifier = activationNotifier;
        this.liveNotifier = liveNotifier;
    }

    @Transactional
    public List<OutboxItem> claim(int limit) {
        return jdbc.sql("""
                        WITH candidates AS (
                            SELECT id
                              FROM search_outbox
                             WHERE status IN ('pending', 'failed')
                               AND available_at <= now()
                             ORDER BY created_at
                             FOR UPDATE SKIP LOCKED
                             LIMIT :limit
                        )
                        UPDATE search_outbox o
                           SET status = 'processing', attempts = attempts + 1,
                               claimed_at = now(), updated_at = now()
                          FROM candidates c
                         WHERE o.id = c.id
                        RETURNING o.id, o.package_version_id, o.index_name, o.document_id,
                                  o.payload::text AS payload, o.attempts, o.revision
                        """)
                .param("limit", limit)
                .query(this::map)
                .list();
    }

    @Transactional
    public void complete(OutboxItem item) {
        var completed = jdbc.sql("""
                        UPDATE search_outbox
                           SET status = 'completed', completed_at = now(), updated_at = now(), last_error = NULL
                         WHERE id = :id AND revision = :revision AND status = 'processing'
                        """)
                .param("id", item.id())
                .param("revision", item.revision())
                .update();
        if (completed == 0) {
            return;
        }
        var identity = jdbc.sql("""
                        SELECT p.id AS package_id,
                               CASE
                                   WHEN p.kind = 'module'
                                       THEN 'module/' || p.namespace || '/' || p.name || '/' || p.target
                                   ELSE 'provider/' || p.namespace || '/' || p.name
                               END AS public_id,
                               pv.version
                          FROM package_versions pv
                          JOIN packages p ON p.id = pv.package_id
                         WHERE pv.id = :versionId
                        """)
                .param("versionId", item.packageVersionId())
                .query((resultSet, rowNumber) -> new IndexedIdentity(
                        resultSet.getObject("package_id", UUID.class),
                        resultSet.getString("public_id"),
                        resultSet.getString("version")))
                .single();
        jdbc.sql("UPDATE package_versions SET active = true WHERE package_id = :packageId AND NOT revoked")
                .param("packageId", identity.packageId())
                .update();
        activationNotifier.notifyChanged(identity.publicId(), identity.version());
        var apmIds = Set.copyOf(jdbc.sql("""
                        SELECT apm_id
                          FROM package_apm_access
                         WHERE package_id = :packageId
                         ORDER BY apm_id
                        """)
                .param("packageId", identity.packageId())
                .query(String.class)
                .list());
        publishAfterCommit(new CatalogChangeEvent(identity.publicId(), "indexed", apmIds, Instant.now()));
    }

    public void fail(OutboxItem item, RuntimeException failure, int maximumAttempts) {
        var terminal = item.attempts() >= maximumAttempts;
        jdbc.sql("""
                        UPDATE search_outbox
                           SET status = :status,
                               available_at = now() + (LEAST(attempts, 10) * interval '5 seconds'),
                               last_error = :error, updated_at = now()
                         WHERE id = :id AND revision = :revision AND status = 'processing'
                        """)
                .param("id", item.id())
                .param("revision", item.revision())
                .param("status", terminal ? "quarantined" : "failed")
                .param("error", truncate(failure.getMessage()))
                .update();
    }

    public void recoverStaleClaims() {
        jdbc.sql("""
                        UPDATE search_outbox
                           SET status = 'failed', available_at = now(), updated_at = now(),
                               last_error = 'Recovered stale processing claim'
                         WHERE status = 'processing' AND claimed_at < now() - interval '5 minutes'
                        """)
                .update();
    }

    public void activateVersionsBackedByCompletedProjection() {
        jdbc.sql("""
                        UPDATE package_versions version
                           SET active = true
                         WHERE NOT version.active
                           AND NOT version.revoked
                           AND EXISTS (
                               SELECT 1
                                 FROM search_outbox projection
                                WHERE projection.aggregate_id = version.package_id
                                  AND projection.status = 'completed'
                           )
                        """)
                .update();
    }

    private OutboxItem map(ResultSet resultSet, int rowNumber) throws SQLException {
        try {
            return new OutboxItem(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("package_version_id", UUID.class),
                    resultSet.getString("index_name"),
                    resultSet.getString("document_id"),
                    objectMapper.readValue(resultSet.getString("payload"), DOCUMENT_TYPE),
                    resultSet.getInt("attempts"),
                    resultSet.getLong("revision"));
        } catch (JacksonException exception) {
            throw new SQLException("Invalid search outbox payload", exception);
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return "Unspecified OpenSearch failure";
        }
        return message.substring(0, Math.min(message.length(), 4_000));
    }

    public record OutboxItem(
            UUID id,
            UUID packageVersionId,
            String indexName,
            String documentId,
            Map<String, Object> payload,
            int attempts,
            long revision) {}

    private void publishAfterCommit(CatalogChangeEvent event) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            liveNotifier.publish(event);
                        }
                    });
        } else {
            liveNotifier.publish(event);
        }
    }

    private record IndexedIdentity(UUID packageId, String publicId, String version) {}
}
