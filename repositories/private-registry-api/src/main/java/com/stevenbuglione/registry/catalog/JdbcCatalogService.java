package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.model.Approval;
import com.stevenbuglione.registry.model.CatalogPackage;
import com.stevenbuglione.registry.model.Governance;
import com.stevenbuglione.registry.model.PackageKind;
import com.stevenbuglione.registry.model.Symbol;
import com.stevenbuglione.registry.security.identity.AccessContext;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class JdbcCatalogService implements CatalogService {

  private final JdbcClient jdbc;
  private final CatalogQueryBuilder queries;
  private final CatalogPackageEnricher enricher;

  public JdbcCatalogService(
      JdbcClient jdbc, CatalogQueryBuilder queries, CatalogPackageEnricher enricher) {
    this.jdbc = jdbc;
    this.queries = queries;
    this.enricher = enricher;
  }

  @Override
  public CatalogPage<CatalogPackage> findPackages(AccessContext accessContext, CatalogQuery query) {
    var plan = queries.findPackages(accessContext, query);
    var rows =
        jdbc.sql(plan.sql()).params(plan.parameters()).query(CatalogRowMapper::packageRow).list();
    var hasNext = query.page() == 0 && rows.size() > query.limit();
    var pageRows = hasNext ? rows.subList(0, query.limit()) : rows;
    var items = enricher.enrich(pageRows);
    var nextCursor = hasNext ? CatalogQueryBuilder.encodeCursor(query, pageRows.getLast()) : null;
    return new CatalogPage<>(items, nextCursor, countMatching(accessContext, query));
  }

  @Override
  public long countPackages(AccessContext accessContext, PackageKind kind) {
    return countMatching(
        accessContext,
        new CatalogQuery(
            new CatalogQuery.Criteria(null, kind, null, null, null, "updated", null, 1, null)));
  }

  @Override
  public List<String> filterAccessiblePackageIds(
      AccessContext accessContext, List<String> packageIds) {
    if (packageIds.isEmpty()) {
      return List.of();
    }
    var authorization = queries.authorizationFilters(accessContext);
    var accessible =
        Set.copyOf(
            jdbc.sql(
                    "SELECT "
                        + CatalogQueryBuilder.PUBLIC_ID
                        + " AS public_id FROM packages p"
                        + authorization.sql()
                        + " AND ("
                        + CatalogQueryBuilder.PUBLIC_ID
                        + ") IN (:packageIds)")
                .params(authorization.parameters())
                .param("packageIds", packageIds)
                .query(String.class)
                .list());
    return packageIds.stream().filter(accessible::contains).toList();
  }

  @Override
  public CatalogPackage getPackage(AccessContext accessContext, String id) {
    return getPackage(accessContext, id, null);
  }

  @Override
  public CatalogPackage getPackage(
      AccessContext accessContext, String id, @Nullable String version) {
    var plan = queries.findPackage(accessContext, id);
    try {
      var row =
          jdbc.sql(plan.sql())
              .params(plan.parameters())
              .query(CatalogRowMapper::packageRow)
              .single();
      var item = enricher.enrich(List.of(row)).getFirst();
      if (version == null || version.isBlank() || "latest".equals(version)) {
        return withSelectedVersion(
            item,
            item.latestVersion(),
            enricher.symbolsForVersion(row.databaseId(), item.latestVersion()));
      }
      var selected =
          item.versions().stream()
              .filter(candidate -> version.equals(candidate.version()) && !candidate.revoked())
              .findFirst()
              .orElseThrow(() -> new NotFoundException("Package not found"));
      return withSelectedVersion(
          item,
          selected.version(),
          enricher.symbolsForVersion(row.databaseId(), selected.version()));
    } catch (EmptyResultDataAccessException exception) {
      throw new NotFoundException("Package not found");
    }
  }

  @Override
  public Governance getGovernance(AccessContext accessContext, String id) {
    return getGovernance(accessContext, id, null);
  }

  @Override
  public Governance getGovernance(
      AccessContext accessContext, String id, @Nullable String version) {
    var item = getPackage(accessContext, id, version);
    var authorization = queries.authorizationFilters(accessContext);
    var authorizationSql = CatalogQueryBuilder.additionalPredicates(authorization);
    var approvals =
        jdbc.sql(
                """
                        SELECT a.approval_type, a.decision, a.decided_by, a.decided_at
                          FROM approvals a
                          JOIN package_versions pv ON pv.id = a.package_version_id
                          JOIN packages p ON p.id = pv.package_id
                         WHERE %s = :id AND pv.version = :version
                        """
                        .formatted(CatalogQueryBuilder.PUBLIC_ID)
                    + authorizationSql
                    + " ORDER BY a.approval_type")
            .params(authorization.parameters())
            .param("id", id)
            .param("version", item.latestVersion())
            .query(
                (resultSet, rowNumber) ->
                    new Approval(
                        resultSet.getString("approval_type"),
                        resultSet.getString("decision"),
                        resultSet.getString("decided_by"),
                        resultSet.getTimestamp("decided_at").toInstant()))
            .list();

    var supportUrl =
        jdbc.sql(
                """
                        SELECT t.support_url
                          FROM teams t
                          JOIN package_owners po ON po.team_id = t.id
                          JOIN packages p ON p.id = po.package_id
                         WHERE %s = :id
                        """
                        .formatted(CatalogQueryBuilder.PUBLIC_ID)
                    + authorizationSql
                    + " ORDER BY po.owner_order, po.team_id LIMIT 1")
            .params(authorization.parameters())
            .param("id", id)
            .query(String.class)
            .optional()
            .orElse(null);
    var sourceRepositoryUrl =
        jdbc.sql(
                """
                        SELECT pv.source_repository
                          FROM package_versions pv
                          JOIN packages p ON p.id = pv.package_id
                         WHERE %s = :id AND pv.version = :version
                        """
                        .formatted(CatalogQueryBuilder.PUBLIC_ID)
                    + authorizationSql)
            .params(authorization.parameters())
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
  public DocumentContent readDocument(AccessContext accessContext, String packageId, String path) {
    return readDocument(accessContext, packageId, null, path);
  }

  @Override
  public DocumentContent readDocument(
      AccessContext accessContext, String packageId, @Nullable String version, String path) {
    var item = getPackage(accessContext, packageId, version);
    var authorization = queries.authorizationFilters(accessContext);
    var candidatePaths = documentationPathCandidates(path);
    try {
      var document =
          jdbc.sql(
                  """
                            SELECT dp.content, dp.content_type
                              FROM documentation_pages dp
                              JOIN package_versions pv ON pv.id = dp.package_version_id
                              JOIN packages p ON p.id = pv.package_id
                             WHERE %s = :id
                               AND pv.version = :version
                               AND pv.active
                               AND dp.path IN (:candidatePaths)
                            """
                          .formatted(CatalogQueryBuilder.PUBLIC_ID)
                      + CatalogQueryBuilder.additionalPredicates(authorization)
                      + " ORDER BY CASE WHEN dp.path = :preferredPath THEN 0 ELSE 1 END LIMIT 1")
              .params(authorization.parameters())
              .param("id", packageId)
              .param("version", item.latestVersion())
              .param("candidatePaths", candidatePaths)
              .param("preferredPath", path)
              .query(
                  (resultSet, rowNumber) ->
                      new StoredDocument(
                          resultSet.getString("content"), resultSet.getString("content_type")))
              .single();
      if (document.content() == null) {
        throw new NotFoundException("Documentation is awaiting PostgreSQL reconciliation");
      }
      return new DocumentContent(document.content(), document.contentType());
    } catch (EmptyResultDataAccessException exception) {
      throw new NotFoundException("Documentation not found");
    }
  }

  static List<String> documentationPathCandidates(String path) {
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("Documentation path is required");
    }
    return switch (path) {
      case "index.md" -> List.of("index.md", "README.md");
      case "README.md" -> List.of("README.md", "index.md");
      default -> List.of(path);
    };
  }

  private record StoredDocument(@Nullable String content, String contentType) {}

  private static CatalogPackage withSelectedVersion(
      CatalogPackage item, String selectedVersion, List<Symbol> selectedSymbols) {
    return new CatalogPackage(
        item.id(),
        item.kind(),
        item.namespace(),
        item.name(),
        item.target(),
        item.title(),
        item.description(),
        selectedVersion,
        item.owners(),
        item.supportLevel(),
        item.lifecycle(),
        item.verification(),
        item.registryTier(),
        item.riskTier(),
        item.sourceAddress(),
        item.updatedAt(),
        item.versions(),
        selectedSymbols);
  }

  private long countMatching(AccessContext accessContext, CatalogQuery query) {
    var plan = queries.countPackages(accessContext, query);
    return jdbc.sql(plan.sql()).params(plan.parameters()).query(Long.class).single();
  }
}
