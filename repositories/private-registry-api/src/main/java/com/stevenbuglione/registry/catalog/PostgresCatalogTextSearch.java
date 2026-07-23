package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.security.identity.AccessContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class PostgresCatalogTextSearch implements CatalogTextSearch {

  private static final String PUBLIC_ID =
      """
      CASE
          WHEN p.kind = 'module'
              THEN 'module/' || p.namespace || '/' || p.name || '/' || p.target
          ELSE 'provider/' || p.namespace || '/' || p.name
      END
      """;

  private final JdbcClient jdbc;

  public PostgresCatalogTextSearch(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<String> findPackageIds(
      AccessContext accessContext, CatalogQuery query, int maximumResults) {
    var searchQuery = Objects.requireNonNull(query.q(), "A search query is required").trim();
    if (searchQuery.isEmpty()
        || (!accessContext.registryAdmin() && accessContext.apmIds().isEmpty())) {
      return List.of();
    }

    var parameters = new HashMap<String, Object>();
    parameters.put("query", searchQuery);
    parameters.put("pattern", "%" + searchQuery + "%");
    parameters.put("maximumResults", maximumResults);

    var sql =
        new StringBuilder(
            """
            SELECT %s AS public_id
              FROM packages p
             WHERE EXISTS (
                       SELECT 1
                         FROM package_versions visible_version
                        WHERE visible_version.package_id = p.id
                          AND visible_version.active
                          AND NOT visible_version.revoked)
            """
                .formatted(PUBLIC_ID));
    appendAuthorization(sql, parameters, accessContext, query);
    sql.append(
        """
               AND (
                    p.search_document @@ websearch_to_tsquery('simple', :query)
                    OR lower(p.namespace || '/' || p.name || '/' || p.target)
                       % lower(:query)
                    OR p.title ILIKE :pattern
                    OR p.description ILIKE :pattern)
             ORDER BY ts_rank_cd(
                          p.search_document,
                          websearch_to_tsquery('simple', :query)) DESC,
                      similarity(
                          lower(p.namespace || '/' || p.name || '/' || p.target),
                          lower(:query)) DESC,
                      public_id
             LIMIT :maximumResults
            """);
    return jdbc.sql(sql.toString())
        .params(parameters)
        .query(
            (resultSet, rowNumber) ->
                Objects.requireNonNull(resultSet.getString("public_id"), "public_id"))
        .list();
  }

  private static void appendAuthorization(
      StringBuilder sql,
      Map<String, Object> parameters,
      AccessContext accessContext,
      CatalogQuery query) {
    if (!accessContext.registryAdmin()) {
      sql.append(
          """
               AND EXISTS (
                     SELECT 1
                       FROM package_apm_access authorized_apm
                      WHERE authorized_apm.package_id = p.id
                        AND authorized_apm.apm_id IN (:authorizedApmIds))
              """);
      parameters.put("authorizedApmIds", accessContext.apmIds());
    }
    if (query.apmId() != null) {
      sql.append(
          """
               AND EXISTS (
                     SELECT 1
                       FROM package_apm_access selected_apm
                      WHERE selected_apm.package_id = p.id
                        AND selected_apm.apm_id = :selectedApmId)
              """);
      parameters.put("selectedApmId", query.apmId());
    }
  }
}
