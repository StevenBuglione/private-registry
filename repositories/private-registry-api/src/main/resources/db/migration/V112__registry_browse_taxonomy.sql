ALTER TABLE packages
    ADD COLUMN IF NOT EXISTS registry_tier text NOT NULL DEFAULT 'community'
        CHECK (registry_tier IN ('official', 'partner', 'partner-premier', 'community')),
    ADD COLUMN IF NOT EXISTS categories text[] NOT NULL DEFAULT '{}';

UPDATE packages
SET registry_tier = CASE
        WHEN kind = 'provider' AND lower(namespace) = 'hashicorp' THEN 'official'
        WHEN kind = 'provider' THEN 'partner'
        WHEN kind = 'module' AND lower(namespace) = 'azure' THEN 'partner'
        ELSE 'community'
    END,
    categories = CASE lower(name)
        WHEN 'aws' THEN ARRAY[
            'public-cloud', 'infrastructure', 'networking', 'security-authentication',
            'logging-monitoring', 'database', 'container-orchestration',
            'data-management', 'web-services'
        ]
        WHEN 'azurerm' THEN ARRAY[
            'public-cloud', 'infrastructure', 'networking', 'security-authentication',
            'logging-monitoring', 'database', 'container-orchestration',
            'data-management', 'web-services'
        ]
        WHEN 'azuread' THEN ARRAY['security-authentication']
        WHEN 'google' THEN ARRAY['public-cloud', 'infrastructure', 'networking', 'database']
        WHEN 'kubernetes' THEN ARRAY['container-orchestration']
        WHEN 'helm' THEN ARRAY['cloud-automation', 'container-orchestration']
        WHEN 'random' THEN ARRAY['utility']
        WHEN 'null' THEN ARRAY['utility']
        WHEN 'tls' THEN ARRAY['security-authentication', 'utility']
        WHEN 'time' THEN ARRAY['utility']
        WHEN 'datadog' THEN ARRAY['logging-monitoring']
        WHEN 'grafana' THEN ARRAY['logging-monitoring']
        ELSE ARRAY[]::text[]
    END;

CREATE INDEX IF NOT EXISTS packages_registry_tier_idx
    ON packages (kind, registry_tier);

CREATE INDEX IF NOT EXISTS packages_categories_idx
    ON packages USING gin (categories);

COMMENT ON COLUMN packages.registry_tier IS
    'Terraform Registry-compatible browse tier used by server-side authorized filtering.';

COMMENT ON COLUMN packages.categories IS
    'Terraform Registry-compatible provider category slugs used by server-side authorized filtering.';
