package com.stevenbuglione.registry.security.identity;

import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "registry.ingestion", name = "enabled", havingValue = "true")
public class EntitlementConfigurationInitializer implements ApplicationRunner {

  private static final Pattern APM_ID = Pattern.compile("APM[0-9]{7}");

  private final JdbcClient jdbc;
  private final IdentityProperties properties;

  public EntitlementConfigurationInitializer(JdbcClient jdbc, IdentityProperties properties) {
    this.jdbc = jdbc;
    this.properties = properties;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments arguments) {
    jdbc.sql(
            """
                        UPDATE identity_group_entitlements
                           SET enabled = false, updated_at = now()
                         WHERE enabled
                        """)
        .update();
    if (!properties.apmGroupMappings().isBlank()) {
      for (var mapping : properties.apmGroupMappings().split(",", -1)) {
        var parts = mapping.trim().split(":", 2);
        if (parts.length != 2 || !APM_ID.matcher(parts[0].trim()).matches()) {
          throw new IllegalStateException(
              "REGISTRY_ENTRA_APM_GROUPS must contain APM0000000:<group-object-id> pairs");
        }
        upsert(parts[1].trim(), parts[0].trim(), parts[0].trim(), "member");
      }
    }
    if (!properties.adminGroupId().isBlank()) {
      upsert(properties.adminGroupId(), null, "Registry administrators", "registry-admin");
    }
  }

  private void upsert(
      String groupObjectId, @Nullable String apmId, String displayName, String role) {
    try {
      UUID.fromString(groupObjectId);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("An Entra group object ID is not a UUID", exception);
    }
    jdbc.sql(
            """
                        INSERT INTO identity_group_entitlements (
                            group_object_id, apm_id, display_name, registry_role, enabled, updated_at)
                        VALUES (:groupObjectId, :apmId, :displayName, :role, true, now())
                        ON CONFLICT (group_object_id) DO UPDATE
                            SET apm_id = EXCLUDED.apm_id,
                                display_name = EXCLUDED.display_name,
                                registry_role = EXCLUDED.registry_role,
                                enabled = true,
                                updated_at = now()
                        """)
        .param("groupObjectId", groupObjectId)
        .param("apmId", apmId, java.sql.Types.VARCHAR)
        .param("displayName", displayName)
        .param("role", role)
        .update();
  }
}
