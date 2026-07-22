package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.model.Approval;
import com.stevenbuglione.registry.model.CatalogPackage;
import com.stevenbuglione.registry.model.Governance;
import com.stevenbuglione.registry.model.PackageKind;
import com.stevenbuglione.registry.model.PackageVersion;
import com.stevenbuglione.registry.model.Symbol;
import com.stevenbuglione.registry.security.identity.AccessContext;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class JdbcCatalogService implements CatalogService {

  private static final String PUBLIC_ID =
      """
            CASE
                WHEN p.kind = 'module' THEN 'module/' || p.namespace || '/' || p.name || '/' || p.target
                ELSE 'provider/' || p.namespace || '/' || p.name
            END
            """;

  private static final String RISK_RANK =
      """
            CASE p.risk_tier
                WHEN 'critical' THEN 4
                WHEN 'high' THEN 3
                WHEN 'medium' THEN 2
                ELSE 1
            END
            """;

  private static final String RELEVANCE_RANK =
      """
            CASE
                WHEN lower(p.namespace || '/' || p.name) = lower(:query) THEN 4
                WHEN lower(p.name) = lower(:query) THEN 3
                WHEN p.name ILIKE :query || '%' THEN 2
                ELSE 1
            END
            """;

  private static final String PACKAGE_SELECT =
      """
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
                     WHERE pv.package_id = p.id AND pv.active AND NOT pv.revoked
                     ORDER BY pv.published_at DESC
                     LIMIT 1
              ) latest ON true
              LEFT JOIN LATERAL (
                    SELECT string_agg(po.team_id, ',' ORDER BY po.owner_order, po.team_id) AS team_ids
                      FROM package_owners po
                     WHERE po.package_id = p.id
              ) owners ON true
            """
          .formatted(PUBLIC_ID);

  private final JdbcClient jdbc;
  private final Optional<DocumentContentResolver> documentContentResolver;
  private final CatalogTextSearch catalogTextSearch;

  public JdbcCatalogService(
      JdbcClient jdbc,
      Optional<DocumentContentResolver> documentContentResolver,
      CatalogTextSearch catalogTextSearch) {
    this.jdbc = jdbc;
    this.documentContentResolver = documentContentResolver;
    this.catalogTextSearch = catalogTextSearch;
  }

  @Override
  public CatalogPage<CatalogPackage> findPackages(AccessContext accessContext, CatalogQuery query) {
    if (query.apmId() != null && !accessContext.mayUseApm(query.apmId())) {
      return new CatalogPage<>(List.of(), null, 0);
    }

    var filters = filters(accessContext, query);
    var sql = new StringBuilder(PACKAGE_SELECT).append(filters.sql());
    var parameters = new HashMap<>(filters.parameters());
    appendCursor(sql, parameters, query);
    sql.append(orderBy(query.sort())).append(" LIMIT :pageSize");
    parameters.put("pageSize", query.limit() + 1);

    var rows = jdbc.sql(sql.toString()).params(parameters).query(this::mapPackageRow).list();
    var hasNext = rows.size() > query.limit();
    var pageRows = hasNext ? rows.subList(0, query.limit()) : rows;
    var items = enrich(pageRows);
    var nextCursor = hasNext ? encodeCursor(query, pageRows.getLast()) : null;
    return new CatalogPage<>(items, nextCursor, countMatching(accessContext, query));
  }

  @Override
  public long countPackages(AccessContext accessContext, PackageKind kind) {
    return countMatching(
        accessContext,
        new CatalogQuery(
            new CatalogQuery.Criteria(
                null, kind, null, null, null, null, null, "updated", null, 1)));
  }

  @Override
  public CatalogPackage getPackage(AccessContext accessContext, String id) {
    return getPackage(accessContext, id, null);
  }

  @Override
  public CatalogPackage getPackage(
      AccessContext accessContext, String id, @Nullable String version) {
    var filters =
        filters(
            accessContext,
            new CatalogQuery(
                new CatalogQuery.Criteria(
                    null, null, null, null, null, null, null, "updated", null, 1)));
    var parameters = new HashMap<>(filters.parameters());
    parameters.put("publicId", id);
    try {
      var row =
          jdbc.sql(PACKAGE_SELECT + filters.sql() + " AND " + PUBLIC_ID + " = :publicId")
              .params(parameters)
              .query(this::mapPackageRow)
              .single();
      var item = enrich(List.of(row)).getFirst();
      if (version == null || version.isBlank() || "latest".equals(version)) {
        return item;
      }
      var selected =
          item.versions().stream()
              .filter(candidate -> version.equals(candidate.version()) && !candidate.revoked())
              .findFirst()
              .orElseThrow(() -> new NotFoundException("Package not found"));
      return withSelectedVersion(
          item, selected.version(), symbolsForVersion(row.databaseId(), selected.version()));
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
    var authorization = authorizationFilters(accessContext);
    var authorizationSql = additionalPredicates(authorization);
    var approvals =
        jdbc.sql(
                """
                        SELECT a.approval_type, a.decision, a.decided_by, a.decided_at
                          FROM approvals a
                          JOIN package_versions pv ON pv.id = a.package_version_id
                          JOIN packages p ON p.id = pv.package_id
                         WHERE %s = :id AND pv.version = :version
                        """
                        .formatted(PUBLIC_ID)
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
                        .formatted(PUBLIC_ID)
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
                        .formatted(PUBLIC_ID)
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
    var authorization = authorizationFilters(accessContext);
    var candidatePaths = documentationPathCandidates(path);
    try {
      var document =
          jdbc.sql(
                  """
                            SELECT dp.content, dp.content_type, dp.s3_key, dp.digest
                              FROM documentation_pages dp
                              JOIN package_versions pv ON pv.id = dp.package_version_id
                              JOIN packages p ON p.id = pv.package_id
                             WHERE %s = :id
                               AND pv.version = :version
                               AND pv.active
                               AND dp.path IN (:candidatePaths)
                            """
                          .formatted(PUBLIC_ID)
                      + additionalPredicates(authorization)
                      + " ORDER BY CASE WHEN dp.path = :preferredPath THEN 0 ELSE 1 END LIMIT 1")
              .params(authorization.parameters())
              .param("id", packageId)
              .param("version", item.latestVersion())
              .param("candidatePaths", candidatePaths)
              .param("preferredPath", path)
              .query(
                  (resultSet, rowNumber) ->
                      new StoredDocument(
                          resultSet.getString("content"),
                          resultSet.getString("content_type"),
                          resultSet.getString("s3_key"),
                          resultSet.getString("digest")))
              .single();
      var content =
          document.content() != null
              ? document.content()
              : documentContentResolver
                  .map(resolver -> resolver.readVerified(document.s3Key(), document.digest()))
                  .orElseThrow(() -> new NotFoundException("Documentation not found"));
      return new DocumentContent(content, document.contentType());
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

  private record StoredDocument(
      @Nullable String content, String contentType, String s3Key, String digest) {}

  private List<Symbol> symbolsForVersion(UUID packageId, String version) {
    return jdbc.sql(
            """
                        SELECT s.kind, s.name, s.description, s.document_path,
                               s.symbol_type, s.default_value, s.is_required, s.sensitive
                          FROM symbols s
                          JOIN package_versions pv ON pv.id = s.package_version_id
                         WHERE pv.package_id = :packageId
                           AND pv.version = :version
                           AND pv.active
                           AND NOT pv.revoked
                         ORDER BY s.kind, s.name
                        """)
        .param("packageId", packageId)
        .param("version", version)
        .query(
            (resultSet, rowNumber) ->
                new Symbol(
                    resultSet.getString("kind"),
                    resultSet.getString("name"),
                    resultSet.getString("description"),
                    resultSet.getString("document_path"),
                    resultSet.getString("symbol_type"),
                    resultSet.getString("default_value"),
                    resultSet.getBoolean("is_required"),
                    resultSet.getBoolean("sensitive")))
        .list();
  }

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
        item.riskTier(),
        item.sourceAddress(),
        item.updatedAt(),
        item.versions(),
        selectedSymbols);
  }

  private QueryFilters filters(AccessContext accessContext, CatalogQuery query) {
    var sql = new StringBuilder(" WHERE 1 = 1");
    var parameters = new HashMap<String, Object>();
    sql.append(
        " AND EXISTS (SELECT 1 FROM package_versions visible_version"
            + " WHERE visible_version.package_id = p.id AND visible_version.active AND NOT visible_version.revoked)");
    if (!accessContext.registryAdmin()) {
      if (accessContext.apmIds().isEmpty()) {
        sql.append(" AND 1 = 0");
      } else {
        sql.append(
            """
                         AND EXISTS (
                               SELECT 1
                                 FROM package_apm_access visible_apm
                                WHERE visible_apm.package_id = p.id
                                  AND visible_apm.apm_id IN (:authorizedApmIds)
                         )
                        """);
        parameters.put("authorizedApmIds", accessContext.apmIds());
      }
    }
    if (query.apmId() != null) {
      sql.append(
          """
                     AND EXISTS (
                           SELECT 1
                             FROM package_apm_access selected_apm
                            WHERE selected_apm.package_id = p.id
                              AND selected_apm.apm_id = :selectedApmId
                     )
                    """);
      parameters.put("selectedApmId", query.apmId());
    }
    if (query.kind() != null) {
      sql.append(" AND p.kind = CAST(:kind AS package_kind)");
      parameters.put("kind", query.kind().jsonValue());
    }
    if (query.q() != null) {
      var matchingIds = catalogTextSearch.findPackageIds(accessContext, query, 10_000);
      if (matchingIds.isEmpty()) {
        sql.append(" AND 1 = 0");
      } else {
        sql.append(" AND (").append(PUBLIC_ID).append(") IN (:searchPackageIds)");
        parameters.put("searchPackageIds", matchingIds);
      }
      parameters.put("query", query.q());
    }
    if (query.provider() != null) {
      sql.append(" AND COALESCE(NULLIF(p.target, ''), p.name) = :provider");
      parameters.put("provider", query.provider());
    }
    if (query.lifecycle() != null) {
      sql.append(" AND p.lifecycle::text = :lifecycle");
      parameters.put("lifecycle", query.lifecycle());
    }
    if (query.risk() != null) {
      sql.append(" AND p.risk_tier = :risk");
      parameters.put("risk", query.risk());
    }
    if (query.approval() != null) {
      sql.append(
          """
                     AND EXISTS (
                           SELECT 1
                             FROM package_versions approved_version
                             JOIN approvals approval ON approval.package_version_id = approved_version.id
                            WHERE approved_version.package_id = p.id
                              AND approved_version.active
                              AND approval.decision = :approval
                     )
                    """);
      parameters.put("approval", query.approval());
    }
    return new QueryFilters(sql.toString(), parameters);
  }

  private QueryFilters authorizationFilters(AccessContext accessContext) {
    return filters(
        accessContext,
        new CatalogQuery(
            new CatalogQuery.Criteria(
                null, null, null, null, null, null, null, "updated", null, 1)));
  }

  private static String additionalPredicates(QueryFilters filters) {
    return filters.sql().substring(" WHERE 1 = 1".length());
  }

  private long countMatching(AccessContext accessContext, CatalogQuery query) {
    var filters = filters(accessContext, query);
    return jdbc.sql("SELECT count(*) FROM packages p" + filters.sql())
        .params(filters.parameters())
        .query(Long.class)
        .single();
  }

  private static void appendCursor(
      StringBuilder sql, Map<String, Object> parameters, CatalogQuery query) {
    if (query.cursor() == null) {
      return;
    }
    var cursor = decodeCursor(query.cursor());
    if (!query.sort().equals(cursor.sort())) {
      throw new IllegalArgumentException("Cursor does not match the requested sort");
    }
    parameters.put("cursorId", cursor.publicId());
    switch (query.sort()) {
      case "name" -> sql.append(" AND ").append(PUBLIC_ID).append(" > :cursorId");
      case "updated" -> {
        try {
          parameters.put("cursorUpdated", Instant.parse(cursor.value()));
        } catch (DateTimeParseException exception) {
          throw new IllegalArgumentException("Invalid catalog cursor", exception);
        }
        sql.append(" AND (p.updated_at < :cursorUpdated OR (p.updated_at = :cursorUpdated AND ")
            .append(PUBLIC_ID)
            .append(" > :cursorId))");
      }
      case "risk" -> {
        try {
          parameters.put("cursorRisk", Integer.parseInt(cursor.value()));
        } catch (NumberFormatException exception) {
          throw new IllegalArgumentException("Invalid catalog cursor", exception);
        }
        sql.append(" AND (")
            .append(RISK_RANK)
            .append(" < :cursorRisk OR (")
            .append(RISK_RANK)
            .append(" = :cursorRisk AND ")
            .append(PUBLIC_ID)
            .append(" > :cursorId))");
      }
      case "relevance" -> {
        try {
          parameters.put("cursorRelevance", Integer.parseInt(cursor.value()));
        } catch (NumberFormatException exception) {
          throw new IllegalArgumentException("Invalid catalog cursor", exception);
        }
        sql.append(" AND (")
            .append(RELEVANCE_RANK)
            .append(" < :cursorRelevance OR (")
            .append(RELEVANCE_RANK)
            .append(" = :cursorRelevance AND ")
            .append(PUBLIC_ID)
            .append(" > :cursorId))");
      }
      default -> throw new IllegalArgumentException("Unsupported catalog sort");
    }
  }

  private static String orderBy(String sort) {
    return switch (sort) {
      case "name" -> " ORDER BY public_id ASC";
      case "risk" -> " ORDER BY " + RISK_RANK + " DESC, public_id ASC";
      case "relevance" -> " ORDER BY " + RELEVANCE_RANK + " DESC, public_id ASC";
      case "updated" -> " ORDER BY p.updated_at DESC, public_id ASC";
      default -> throw new IllegalArgumentException("Unsupported catalog sort");
    };
  }

  private static String encodeCursor(CatalogQuery query, PackageRow row) {
    var value =
        switch (query.sort()) {
          case "name" -> "";
          case "updated" -> row.item().updatedAt().toString();
          case "risk" -> Integer.toString(riskRank(row.item().riskTier()));
          case "relevance" -> Integer.toString(relevanceRank(row.item(), query.q()));
          default -> throw new IllegalArgumentException("Unsupported catalog sort");
        };
    var plain = String.join("\u001f", query.sort(), value, row.item().id());
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(plain.getBytes(StandardCharsets.UTF_8));
  }

  private static DecodedCursor decodeCursor(String encoded) {
    try {
      var plain = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
      var segments = plain.split("\u001f", -1);
      if (segments.length != 3 || segments[0].isBlank() || segments[2].isBlank()) {
        throw new IllegalArgumentException("Invalid catalog cursor");
      }
      return new DecodedCursor(segments[0], segments[1], segments[2]);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Invalid catalog cursor", exception);
    }
  }

  private List<CatalogPackage> enrich(List<PackageRow> rows) {
    if (rows.isEmpty()) {
      return List.of();
    }
    var ids = rows.stream().map(PackageRow::databaseId).toList();
    var versions = new LinkedHashMap<UUID, List<PackageVersion>>();
    jdbc.sql(
            """
                        SELECT package_id, version, published_at, package_digest, documentation_digest,
                               documentation_root, artifact_repository, artifact_path,
                               source_commit, prerelease, deprecated, revoked
                          FROM package_versions
                         WHERE package_id IN (:packageIds) AND active
                         ORDER BY package_id, published_at DESC
                        """)
        .param("packageIds", ids)
        .query(
            (resultSet, rowNumber) ->
                new VersionRow(
                    resultSet.getObject("package_id", UUID.class),
                    new PackageVersion(
                        resultSet.getString("version"),
                        resultSet.getTimestamp("published_at").toInstant(),
                        resultSet.getString("package_digest"),
                        resultSet.getString("documentation_digest"),
                        resultSet.getString("documentation_root"),
                        resultSet.getString("artifact_repository"),
                        resultSet.getString("artifact_path"),
                        resultSet.getString("source_commit"),
                        resultSet.getBoolean("prerelease"),
                        resultSet.getBoolean("deprecated"),
                        resultSet.getBoolean("revoked"))))
        .list()
        .forEach(
            row ->
                versions
                    .computeIfAbsent(row.packageId(), ignored -> new ArrayList<>())
                    .add(row.version()));

    return rows.stream()
        .map(
            row -> {
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
                  List.copyOf(versions.getOrDefault(row.databaseId(), List.of())),
                  List.of());
            })
        .toList();
  }

  private PackageRow mapPackageRow(ResultSet resultSet, int rowNumber) throws SQLException {
    var ownerIds = resultSet.getString("owner_ids");
    var owners = ownerIds.isBlank() ? List.<String>of() : Arrays.asList(ownerIds.split(",", -1));
    var item =
        new CatalogPackage(
            resultSet.getString("public_id"),
            java.util.Objects.requireNonNull(PackageKind.from(resultSet.getString("kind"))),
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

  private static int riskRank(String riskTier) {
    return switch (riskTier) {
      case "critical" -> 4;
      case "high" -> 3;
      case "medium" -> 2;
      default -> 1;
    };
  }

  private static int relevanceRank(CatalogPackage item, @Nullable String query) {
    if (query == null) {
      return 1;
    }
    if ((item.namespace() + "/" + item.name()).equalsIgnoreCase(query)) {
      return 4;
    }
    if (item.name().equalsIgnoreCase(query)) {
      return 3;
    }
    return item.name()
                .regionMatches(true, 0, query, 0, Math.min(item.name().length(), query.length()))
            && item.name().length() >= query.length()
        ? 2
        : 1;
  }

  private record QueryFilters(String sql, Map<String, Object> parameters) {
    private QueryFilters {
      parameters = Map.copyOf(parameters);
    }
  }

  private record DecodedCursor(String sort, String value, String publicId) {}

  private record PackageRow(UUID databaseId, CatalogPackage item) {}

  private record VersionRow(UUID packageId, PackageVersion version) {}
}
