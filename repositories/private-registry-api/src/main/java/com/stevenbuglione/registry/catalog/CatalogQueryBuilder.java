package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.security.identity.AccessContext;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
final class CatalogQueryBuilder {

  static final String PUBLIC_ID =
      """
      CASE
          WHEN p.kind = 'module'
              THEN 'module/' || p.namespace || '/' || p.name || '/' || p.target
          ELSE 'provider/' || p.namespace || '/' || p.name
      END
      """;

  static final String SEARCH_IDENTITY = "lower(p.namespace || '/' || p.name || '/' || p.target)";

  static final String RELEVANCE_SCORE =
      """
      (
          CASE
              WHEN lower(p.namespace || '/' || p.name) = lower(:query) THEN 100.0
              WHEN lower(p.name) = lower(:query) THEN 75.0
              WHEN p.name ILIKE :query || '%%' THEN 50.0
              ELSE 0.0
          END
          + (10.0 * ts_rank_cd(
              p.search_document,
              websearch_to_tsquery('simple', :query)))
          + similarity(%s, lower(:query))
      )
      """
          .formatted(SEARCH_IDENTITY);

  static final String DOWNLOAD_COUNT = "COALESCE(downloads.download_count, 0)";

  private static final String DOWNLOAD_TOTALS_PROJECTION =
      """
      WITH latest_version_downloads AS (
          SELECT DISTINCT ON (statistics.package_version_id)
                 statistics.package_version_id,
                 statistics.download_count
            FROM artifact_download_statistics statistics
           ORDER BY statistics.package_version_id, statistics.observed_on DESC
      ),
      package_download_totals AS (
          SELECT versions.package_id,
                 COALESCE(sum(latest.download_count), 0)::bigint AS download_count
            FROM package_versions versions
            LEFT JOIN latest_version_downloads latest
              ON latest.package_version_id = versions.id
           WHERE versions.active
             AND NOT versions.revoked
           GROUP BY versions.package_id
      )
      """;

  QueryPlan findPackages(AccessContext accessContext, CatalogQuery query) {
    var filters = CatalogQueryFilters.from(accessContext, query);
    var sql =
        new StringBuilder(downloadProjection(query))
            .append(packageSelect(query))
            .append(filters.sql());
    var parameters = new HashMap<>(filters.parameters());
    if (query.page() == 0) {
      CatalogCursorCodec.append(sql, parameters, query);
    }
    sql.append(orderBy(query.sort())).append(" LIMIT :pageSize");
    parameters.put("pageSize", query.page() == 0 ? query.limit() + 1 : query.limit());
    if (query.page() > 0) {
      sql.append(" OFFSET :pageOffset");
      parameters.put("pageOffset", query.offset());
    }
    return new QueryPlan(sql.toString(), parameters);
  }

  QueryPlan findPackage(AccessContext accessContext, String publicId) {
    var query = unrestrictedQuery();
    var filters = CatalogQueryFilters.from(accessContext, query);
    var parameters = new HashMap<>(filters.parameters());
    parameters.put("publicId", publicId);
    return new QueryPlan(
        packageSelect(query) + filters.sql() + " AND " + PUBLIC_ID + " = :publicId", parameters);
  }

  QueryPlan countPackages(AccessContext accessContext, CatalogQuery query) {
    var filters = CatalogQueryFilters.from(accessContext, query);
    return new QueryPlan("SELECT count(*) FROM packages p" + filters.sql(), filters.parameters());
  }

  CatalogQueryFilters authorizationFilters(AccessContext accessContext) {
    return CatalogQueryFilters.from(accessContext, unrestrictedQuery());
  }

  static String additionalPredicates(CatalogQueryFilters filters) {
    return filters.sql().substring(" WHERE 1 = 1".length());
  }

  static String encodeCursor(CatalogQuery query, CatalogReadRow row) {
    return CatalogCursorCodec.encode(query, row);
  }

  private static String packageSelect(CatalogQuery query) {
    var relevanceScore = query.q() == null ? "0.0::double precision" : RELEVANCE_SCORE;
    var includeDownloads = "downloads".equals(query.sort());
    var downloadCount = includeDownloads ? DOWNLOAD_COUNT : "0::bigint";
    var downloadJoin =
        includeDownloads
            ? """
               LEFT JOIN package_download_totals downloads
                 ON downloads.package_id = p.id
              """
            : "";
    return """
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
               p.registry_tier,
               p.risk_tier,
               p.source_address,
               p.updated_at,
               %s AS download_count,
               %s AS relevance_score
          FROM packages p
          LEFT JOIN LATERAL (
                SELECT pv.version
                  FROM package_versions pv
                 WHERE pv.package_id = p.id
                   AND pv.active
                   AND NOT pv.revoked
                 ORDER BY %s
                 LIMIT 1
          ) latest ON true
          LEFT JOIN LATERAL (
                SELECT string_agg(
                           po.team_id,
                           ',' ORDER BY po.owner_order, po.team_id) AS team_ids
                 FROM package_owners po
                 WHERE po.package_id = p.id
          ) owners ON true
          %s
        """
        .formatted(
            PUBLIC_ID,
            downloadCount,
            relevanceScore,
            CatalogVersionOrdering.NEWEST_FIRST,
            downloadJoin);
  }

  private static String downloadProjection(CatalogQuery query) {
    return "downloads".equals(query.sort()) ? DOWNLOAD_TOTALS_PROJECTION : "";
  }

  private static CatalogQuery unrestrictedQuery() {
    return new CatalogQuery(
        new CatalogQuery.Criteria(null, null, null, null, null, "updated", null, 1, null));
  }

  private static String orderBy(String sort) {
    return switch (sort) {
      case "name" -> " ORDER BY public_id ASC";
      case "relevance" -> " ORDER BY relevance_score DESC, public_id ASC";
      case "downloads" -> " ORDER BY download_count DESC, public_id ASC";
      case "updated" -> " ORDER BY p.updated_at DESC, public_id ASC";
      default -> throw new IllegalArgumentException("Unsupported catalog sort");
    };
  }

  record QueryPlan(String sql, Map<String, Object> parameters) {
    QueryPlan {
      parameters = Map.copyOf(parameters);
    }
  }
}
