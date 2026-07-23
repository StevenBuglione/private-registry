package com.stevenbuglione.registry.catalog;

import com.stevenbuglione.registry.security.identity.AccessContext;
import java.util.HashMap;
import java.util.Map;

record CatalogQueryFilters(String sql, Map<String, Object> parameters) {

  CatalogQueryFilters {
    parameters = Map.copyOf(parameters);
  }

  static CatalogQueryFilters from(AccessContext accessContext, CatalogQuery query) {
    var sql =
        new StringBuilder(
            """
             WHERE 1 = 1
               AND EXISTS (
                     SELECT 1
                       FROM package_versions visible_version
                      WHERE visible_version.package_id = p.id
                        AND visible_version.active
                        AND NOT visible_version.revoked)
            """);
    var parameters = new HashMap<String, Object>();
    appendAuthorization(sql, parameters, accessContext);
    appendKind(sql, parameters, query);
    appendNamespace(sql, parameters, query);
    appendTextSearch(sql, parameters, query);
    appendTaxonomy(sql, parameters, query);
    return new CatalogQueryFilters(sql.toString(), parameters);
  }

  private static void appendAuthorization(
      StringBuilder sql, Map<String, Object> parameters, AccessContext accessContext) {
    if (accessContext.registryAdmin()) {
      return;
    }
    if (accessContext.apmIds().isEmpty()) {
      sql.append(" AND 1 = 0");
      return;
    }
    sql.append(
        """
         AND EXISTS (
               SELECT 1
                 FROM package_apm_access visible_apm
                WHERE visible_apm.package_id = p.id
                  AND visible_apm.apm_id IN (:authorizedApmIds))
        """);
    parameters.put("authorizedApmIds", accessContext.apmIds());
  }

  private static void appendKind(
      StringBuilder sql, Map<String, Object> parameters, CatalogQuery query) {
    if (query.kind() != null) {
      sql.append(" AND p.kind = CAST(:kind AS package_kind)");
      parameters.put("kind", query.kind().jsonValue());
    }
  }

  private static void appendNamespace(
      StringBuilder sql, Map<String, Object> parameters, CatalogQuery query) {
    if (query.namespace() != null) {
      sql.append(" AND lower(p.namespace) = lower(:namespace)");
      parameters.put("namespace", query.namespace());
    }
  }

  private static void appendTextSearch(
      StringBuilder sql, Map<String, Object> parameters, CatalogQuery query) {
    if (query.q() == null) {
      return;
    }
    sql.append(
        """
         AND (
              p.search_document @@ websearch_to_tsquery('simple', :query)
              OR %s %% lower(:query)
              OR p.title ILIKE :queryPattern
              OR p.description ILIKE :queryPattern)
        """
            .formatted(CatalogQueryBuilder.SEARCH_IDENTITY));
    parameters.put("query", query.q());
    parameters.put("queryPattern", "%" + query.q() + "%");
  }

  private static void appendTaxonomy(
      StringBuilder sql, Map<String, Object> parameters, CatalogQuery query) {
    if (!query.providers().isEmpty()) {
      sql.append(" AND COALESCE(NULLIF(p.target, ''), p.name) IN (:providers)");
      parameters.put("providers", query.providers());
    }
    appendTier(sql, parameters, query);
    if (!query.categories().isEmpty()) {
      sql.append(
          """
           AND EXISTS (
                 SELECT 1
                   FROM package_categories visible_category
                  WHERE visible_category.package_id = p.id
                    AND visible_category.category_slug IN (:registryCategories))
          """);
      parameters.put("registryCategories", query.categories());
    }
  }

  private static void appendTier(
      StringBuilder sql, Map<String, Object> parameters, CatalogQuery query) {
    if (query.tiers().isEmpty()) {
      return;
    }
    if (query.tiers().contains("none")) {
      sql.append(" AND 1 = 0");
      return;
    }
    sql.append(" AND p.registry_tier IN (:registryTiers)");
    parameters.put("registryTiers", query.tiers());
  }
}
