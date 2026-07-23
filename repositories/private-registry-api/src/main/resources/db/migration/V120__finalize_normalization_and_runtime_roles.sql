DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'registry_web') THEN
        CREATE ROLE registry_web LOGIN;
    END IF;

    EXECUTE format('GRANT CONNECT ON DATABASE %I TO registry_web', current_database());

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rds_iam') THEN
        GRANT rds_iam TO registry_web;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO registry_web;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO registry_web;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO registry_web;

GRANT INSERT ON audit_events TO registry_web;

GRANT SELECT, INSERT, UPDATE ON registry_sync_credentials TO registry_web;
GRANT SELECT, INSERT, UPDATE ON registry_traffic_identities TO registry_web;
GRANT SELECT, INSERT, DELETE ON registry_page_views TO registry_web;

GRANT SELECT, UPDATE ON registry_homepage_settings TO registry_web;
GRANT SELECT, INSERT, DELETE ON registry_homepage_features TO registry_web;

GRANT SELECT, INSERT ON catalog_event_queue TO registry_web;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO registry_web;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO registry_web;

COMMENT ON ROLE registry_web IS
    'Public Registry API role: catalog reads plus narrowly scoped audit, analytics, admin settings, synchronization credentials, and event publication writes.';

DROP TRIGGER packages_categories_sync_trigger ON packages;
DROP FUNCTION registry_sync_package_categories();

ALTER TABLE packages
    DROP COLUMN categories;

ALTER TABLE registry_homepage_settings
    DROP COLUMN featured_provider_ids,
    DROP COLUMN featured_module_ids;

DROP TRIGGER registry_page_views_identity_trigger ON registry_page_views;
DROP FUNCTION registry_resolve_traffic_identity();

ALTER TABLE registry_page_views
    DROP COLUMN subject,
    DROP COLUMN display_name,
    DROP COLUMN email;
