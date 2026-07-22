GRANT SELECT ON identity_group_entitlements, package_apm_access TO registry_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON identity_group_entitlements, package_apm_access TO registry_indexer;

CREATE INDEX identity_group_entitlements_enabled_idx
    ON identity_group_entitlements(enabled, group_object_id);
