package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.model.Approval;
import com.stevenbuglione.registry.model.CatalogPackage;
import com.stevenbuglione.registry.model.Governance;
import com.stevenbuglione.registry.model.PackageKind;
import com.stevenbuglione.registry.model.PackageVersion;
import com.stevenbuglione.registry.model.SearchResult;
import com.stevenbuglione.registry.model.Symbol;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class JdbcCatalogService implements CatalogService {

    private static final String PUBLIC_ID = """
            CASE
                WHEN p.kind = 'module' THEN 'module/' || p.namespace || '/' || p.name || '/' || p.target
                ELSE 'provider/' || p.namespace || '/' || p.name
            END
            """;

    private static final String PACKAGE_SELECT = """
            SELECT p.id AS database_id,
                   %s AS public_id,
                   p.kind::text AS kind,
                   p.namespace,
                   p.name,
                   p.target,
                   p.title,
                   p.description,
                   latest.version AS latest_version,
                   COALESCE(owners.team_ids, '') AS owner_ids,
                   p.support_level::text AS support_level,
                   p.lifecycle::text AS lifecycle,
                   p.verification,
                   p.risk_tier,
                   p.source_address,
                   p.updated_at
              FROM packages p
              LEFT JOIN LATERAL (
                    SELECT pv.version
                      FROM package_versions pv
                     WHERE pv.package_id = p.id AND NOT pv.revoked
                     ORDER BY pv.published_at DESC
                     LIMIT 1
              ) latest ON true
              LEFT JOIN LATERAL (
                    SELECT string_agg(po.team_id, ',' ORDER BY po.owner_order, po.team_id) AS team_ids
                      FROM package_owners po
                     WHERE po.package_id = p.id
              ) owners ON true
            """.formatted(PUBLIC_ID);

    private final JdbcClient jdbc;

    public JdbcCatalogService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CatalogPackage> listPackages(PackageKind kind) {
        var sql = PACKAGE_SELECT;
        if (kind != null) {
            sql += " WHERE p.kind = CAST(:kind AS package_kind)";
        }
        sql += " ORDER BY public_id";

        var statement = jdbc.sql(sql);
        if (kind != null) {
            statement = statement.param("kind", kind.jsonValue());
        }
        return statement.query(this::mapPackageRow).list().stream().map(this::enrich).toList();
    }

    @Override
    public CatalogPackage getPackage(String id) {
        try {
            var row = jdbc.sql(PACKAGE_SELECT + " WHERE " + PUBLIC_ID + " = :id")
                    .param("id", id)
                    .query(this::mapPackageRow)
                    .single();
            return enrich(row);
        } catch (EmptyResultDataAccessException exception) {
            throw new NotFoundException("Package not found: " + id);
        }
    }

    @Override
    public Governance getGovernance(String id) {
        var item = getPackage(id);
        var approvals = jdbc.sql("""
                        SELECT a.approval_type, a.decision, a.decided_by, a.decided_at
                          FROM approvals a
                          JOIN package_versions pv ON pv.id = a.package_version_id
                          JOIN packages p ON p.id = pv.package_id
                         WHERE %s = :id AND pv.version = :version
                         ORDER BY a.approval_type
                        """.formatted(PUBLIC_ID))
                .param("id", id)
                .param("version", item.latestVersion())
                .query((resultSet, rowNumber) -> new Approval(
                        resultSet.getString("approval_type"),
                        resultSet.getString("decision"),
                        resultSet.getString("decided_by"),
                        resultSet.getTimestamp("decided_at").toInstant()))
                .list();

        var supportUrl = jdbc.sql("""
                        SELECT t.support_url
                          FROM teams t
                          JOIN package_owners po ON po.team_id = t.id
                          JOIN packages p ON p.id = po.package_id
                         WHERE %s = :id
                         ORDER BY po.owner_order, po.team_id
                         LIMIT 1
                        """.formatted(PUBLIC_ID))
                .param("id", id)
                .query(String.class)
                .optional()
                .orElse(null);

        var sourceRepositoryUrl = jdbc.sql("""
                        SELECT pv.source_repository
                          FROM package_versions pv
                          JOIN packages p ON p.id = pv.package_id
                         WHERE %s = :id AND pv.version = :version
                        """.formatted(PUBLIC_ID))
                .param("id", id)
                .param("version", item.latestVersion())
                .query(String.class)
                .optional()
                .orElse(null);

        return new Governance(
                id,
                item.owners(),
                item.supportLevel(),
                item.lifecycle(),
                item.riskTier(),
                item.verification(),
                approvals,
                item.sourceAddress(),
                "= " + item.latestVersion(),
                supportUrl,
                sourceRepositoryUrl,
                null);
    }

    @Override
    public List<SearchResult> search(String query, PackageKind kind, int requestedLimit) {
        var limit = requestedLimit <= 0 ? 25 : Math.min(requestedLimit, 100);
        var normalizedQuery = query == null ? "" : query.trim();
        var sql = PACKAGE_SELECT + " WHERE (" +
                " :query = '' OR p.namespace ILIKE '%' || :query || '%'" +
                " OR p.name ILIKE '%' || :query || '%'" +
                " OR p.title ILIKE '%' || :query || '%'" +
                " OR p.description ILIKE '%' || :query || '%'" +
                " OR COALESCE(owners.team_ids, '') ILIKE '%' || :query || '%')";
        if (kind != null) {
            sql += " AND p.kind = CAST(:kind AS package_kind)";
        }
        sql += " ORDER BY p.updated_at DESC, public_id LIMIT :limit";

        var statement = jdbc.sql(sql).param("query", normalizedQuery).param("limit", limit);
        if (kind != null) {
            statement = statement.param("kind", kind.jsonValue());
        }
        return statement.query(this::mapPackageRow).list().stream()
                .map(this::enrich)
                .map(item -> new SearchResult(
                        item.id(),
                        item.kind().jsonValue(),
                        item.namespace() + "/" + item.name(),
                        item.description(),
                        packagePath(item),
                        1.0,
                        item,
                        null))
                .toList();
    }

    @Override
    public DocumentContent readDocument(String packageId, String path) {
        try {
            return jdbc.sql("""
                            SELECT dp.content, dp.content_type
                              FROM documentation_pages dp
                              JOIN package_versions pv ON pv.id = dp.package_version_id
                              JOIN packages p ON p.id = pv.package_id
                             WHERE %s = :id
                               AND pv.version = (
                                   SELECT newest.version
                                     FROM package_versions newest
                                    WHERE newest.package_id = p.id AND NOT newest.revoked
                                    ORDER BY newest.published_at DESC
                                    LIMIT 1
                               )
                               AND dp.path = :path
                               AND dp.content IS NOT NULL
                            """.formatted(PUBLIC_ID))
                    .param("id", packageId)
                    .param("path", path)
                    .query((resultSet, rowNumber) -> new DocumentContent(
                            resultSet.getString("content"), resultSet.getString("content_type")))
                    .single();
        } catch (EmptyResultDataAccessException exception) {
            throw new NotFoundException("Documentation not found: " + path);
        }
    }

    private PackageRow mapPackageRow(ResultSet resultSet, int rowNumber) throws SQLException {
        var ownerIds = resultSet.getString("owner_ids");
        var owners = ownerIds.isBlank() ? List.<String>of() : Arrays.asList(ownerIds.split(","));
        var item = new CatalogPackage(
                resultSet.getString("public_id"),
                PackageKind.from(resultSet.getString("kind")),
                resultSet.getString("namespace"),
                resultSet.getString("name"),
                resultSet.getString("target"),
                resultSet.getString("title"),
                resultSet.getString("description"),
                resultSet.getString("latest_version"),
                List.copyOf(owners),
                resultSet.getString("support_level"),
                resultSet.getString("lifecycle"),
                resultSet.getString("verification"),
                resultSet.getString("risk_tier"),
                resultSet.getString("source_address"),
                resultSet.getTimestamp("updated_at").toInstant(),
                List.of(),
                List.of());
        return new PackageRow(resultSet.getObject("database_id", UUID.class), item);
    }

    private CatalogPackage enrich(PackageRow row) {
        var versions = jdbc.sql("""
                        SELECT version, published_at, package_digest, documentation_digest,
                               documentation_root, source_commit, prerelease, deprecated, revoked
                          FROM package_versions
                         WHERE package_id = :packageId
                         ORDER BY published_at DESC
                        """)
                .param("packageId", row.databaseId())
                .query((resultSet, rowNumber) -> new PackageVersion(
                        resultSet.getString("version"),
                        resultSet.getTimestamp("published_at").toInstant(),
                        resultSet.getString("package_digest"),
                        resultSet.getString("documentation_digest"),
                        resultSet.getString("documentation_root"),
                        resultSet.getString("source_commit"),
                        resultSet.getBoolean("prerelease"),
                        resultSet.getBoolean("deprecated"),
                        resultSet.getBoolean("revoked")))
                .list();

        var symbols = jdbc.sql("""
                        SELECT s.kind, s.name, s.description, s.document_path
                          FROM symbols s
                          JOIN package_versions pv ON pv.id = s.package_version_id
                         WHERE pv.package_id = :packageId AND pv.version = :version
                         ORDER BY s.kind, s.name
                        """)
                .param("packageId", row.databaseId())
                .param("version", row.item().latestVersion())
                .query((resultSet, rowNumber) -> new Symbol(
                        resultSet.getString("kind"),
                        resultSet.getString("name"),
                        resultSet.getString("description"),
                        resultSet.getString("document_path")))
                .list();

        var item = row.item();
        return new CatalogPackage(
                item.id(),
                item.kind(),
                item.namespace(),
                item.name(),
                item.target(),
                item.title(),
                item.description(),
                item.latestVersion(),
                item.owners(),
                item.supportLevel(),
                item.lifecycle(),
                item.verification(),
                item.riskTier(),
                item.sourceAddress(),
                item.updatedAt(),
                versions,
                symbols);
    }

    private static String packagePath(CatalogPackage item) {
        if (item.kind() == PackageKind.MODULE) {
            return "/module/%s/%s/%s/latest".formatted(item.namespace(), item.name(), item.target());
        }
        return "/provider/%s/%s/latest".formatted(item.namespace(), item.name());
    }

    private record PackageRow(UUID databaseId, CatalogPackage item) {}
}
