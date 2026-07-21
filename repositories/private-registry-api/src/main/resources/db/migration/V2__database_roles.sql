DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'registry_app') THEN
        CREATE ROLE registry_app LOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'registry_indexer') THEN
        CREATE ROLE registry_indexer LOGIN;
    END IF;

    EXECUTE format('GRANT CONNECT ON DATABASE %I TO registry_app, registry_indexer', current_database());

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rds_iam') THEN
        GRANT rds_iam TO registry_app;
        GRANT rds_iam TO registry_indexer;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO registry_app, registry_indexer;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO registry_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO registry_indexer;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO registry_indexer;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO registry_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO registry_indexer;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO registry_indexer;
