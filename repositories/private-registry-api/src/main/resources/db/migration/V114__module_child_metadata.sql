ALTER TABLE symbols
    DROP CONSTRAINT IF EXISTS symbols_package_version_id_kind_name_key;

CREATE UNIQUE INDEX IF NOT EXISTS symbols_package_version_kind_name_path_key
    ON symbols (package_version_id, kind, name, document_path);
