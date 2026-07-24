ALTER TABLE registry_homepage_settings
    ADD COLUMN featured_providers_enabled boolean NOT NULL DEFAULT true,
    ADD COLUMN featured_modules_enabled boolean NOT NULL DEFAULT true;

COMMENT ON COLUMN registry_homepage_settings.featured_providers_enabled IS
    'Whether the curated provider collection is rendered on the Registry homepage.';

COMMENT ON COLUMN registry_homepage_settings.featured_modules_enabled IS
    'Whether the curated module collection is rendered on the Registry homepage.';
