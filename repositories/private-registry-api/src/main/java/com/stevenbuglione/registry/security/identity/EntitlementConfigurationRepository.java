package com.stevenbuglione.registry.security.identity;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class EntitlementConfigurationRepository {

  private final JdbcClient jdbc;

  public EntitlementConfigurationRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Set<String> configuredGroupIds() {
    return Set.copyOf(
        jdbc.sql(
                """
                        SELECT group_object_id
                          FROM identity_group_entitlements
                         WHERE enabled
                         ORDER BY group_object_id
                        """)
            .query(String.class)
            .list());
  }

  public ResolvedEntitlements resolve(Set<String> memberGroupIds) {
    if (memberGroupIds.isEmpty()) {
      return new ResolvedEntitlements(List.of(), false);
    }

    var rows =
        jdbc.sql(
                """
                        SELECT apm_id, display_name, registry_role
                          FROM identity_group_entitlements
                         WHERE enabled AND group_object_id IN (:groupIds)
                         ORDER BY apm_id NULLS LAST, display_name
                        """)
            .param("groupIds", memberGroupIds)
            .query(
                (resultSet, rowNumber) ->
                    new EntitlementRow(
                        resultSet.getString("apm_id"),
                        resultSet.getString("display_name"),
                        resultSet.getString("registry_role")))
            .list();

    var entitlements =
        rows.stream()
            .filter(row -> row.apmId() != null)
            .map(row -> new ApmEntitlement(row.apmId(), row.displayName()))
            .distinct()
            .sorted(Comparator.comparing(ApmEntitlement::apmId))
            .toList();
    var administrator = rows.stream().anyMatch(row -> "registry-admin".equals(row.registryRole()));
    return new ResolvedEntitlements(entitlements, administrator);
  }

  public record ResolvedEntitlements(List<ApmEntitlement> entitlements, boolean administrator) {
    public ResolvedEntitlements {
      entitlements = List.copyOf(entitlements);
    }
  }

  private record EntitlementRow(String apmId, String displayName, String registryRole) {}
}
