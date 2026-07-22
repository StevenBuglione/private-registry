CREATE TABLE identity_group_entitlements (
    group_object_id text PRIMARY KEY,
    apm_id text,
    display_name text NOT NULL,
    registry_role text NOT NULL DEFAULT 'member'
        CHECK (registry_role IN ('member', 'registry-admin')),
    enabled boolean NOT NULL DEFAULT true,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (apm_id IS NULL OR apm_id ~ '^APM[0-9]{7}$'),
    CHECK ((registry_role = 'member' AND apm_id IS NOT NULL)
        OR (registry_role = 'registry-admin'))
);

CREATE TABLE package_apm_access (
    package_id uuid NOT NULL REFERENCES packages(id) ON DELETE CASCADE,
    apm_id varchar(128) NOT NULL CHECK (apm_id ~ '^APM[0-9]{7}$'),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (package_id, apm_id)
);

CREATE INDEX package_apm_access_apm_package_idx
    ON package_apm_access(apm_id, package_id);

COMMENT ON TABLE package_apm_access IS
    'Server-side package visibility. Packages without an APM assignment are invisible to non-administrators.';
