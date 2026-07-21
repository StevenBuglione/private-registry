CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TYPE package_kind AS ENUM ('module', 'provider');
CREATE TYPE lifecycle_state AS ENUM ('draft', 'candidate', 'approved', 'maintenance', 'deprecated', 'revoked', 'archived');
CREATE TYPE support_level AS ENUM ('supported', 'maintenance', 'experimental', 'deprecated', 'revoked', 'archived');

CREATE TABLE packages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    kind package_kind NOT NULL,
    namespace text NOT NULL,
    name text NOT NULL,
    target text NOT NULL DEFAULT '',
    title text NOT NULL,
    description text NOT NULL,
    source_address text NOT NULL,
    visibility text NOT NULL CHECK (visibility IN ('enterprise', 'restricted')),
    risk_tier text NOT NULL CHECK (risk_tier IN ('low', 'medium', 'high', 'critical')),
    verification text NOT NULL,
    support_level support_level NOT NULL,
    lifecycle lifecycle_state NOT NULL,
    replacement_source text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (kind, namespace, name, target),
    CHECK ((kind = 'module' AND target <> '') OR (kind = 'provider' AND target = ''))
);

CREATE TABLE package_versions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    package_id uuid NOT NULL REFERENCES packages(id) ON DELETE CASCADE,
    version text NOT NULL,
    package_digest text NOT NULL CHECK (package_digest ~ '^sha256:[0-9a-f]{64}$'),
    documentation_digest text NOT NULL CHECK (documentation_digest ~ '^sha256:[0-9a-f]{64}$'),
    documentation_root text NOT NULL,
    artifact_repository text NOT NULL,
    artifact_path text NOT NULL,
    source_repository text NOT NULL,
    source_commit text NOT NULL,
    source_tag text NOT NULL,
    terraform_constraint text NOT NULL,
    opentofu_constraint text NOT NULL,
    published_at timestamptz NOT NULL,
    prerelease boolean NOT NULL DEFAULT false,
    deprecated boolean NOT NULL DEFAULT false,
    revoked boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (package_id, version),
    UNIQUE (package_id, package_digest)
);

CREATE TABLE teams (
    id text PRIMARY KEY,
    display_name text NOT NULL,
    support_url text,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE package_owners (
    package_id uuid NOT NULL REFERENCES packages(id) ON DELETE CASCADE,
    team_id text NOT NULL REFERENCES teams(id),
    owner_order integer NOT NULL DEFAULT 0,
    PRIMARY KEY (package_id, team_id)
);

CREATE TABLE approvals (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    package_version_id uuid NOT NULL REFERENCES package_versions(id) ON DELETE CASCADE,
    approval_type text NOT NULL,
    decision text NOT NULL CHECK (decision IN ('approved', 'rejected', 'waived')),
    decided_by text NOT NULL,
    decided_at timestamptz NOT NULL,
    policy_version text NOT NULL,
    justification text,
    evidence_s3_uri text NOT NULL,
    UNIQUE (package_version_id, approval_type, policy_version)
);

CREATE TABLE symbols (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    package_version_id uuid NOT NULL REFERENCES package_versions(id) ON DELETE CASCADE,
    kind text NOT NULL,
    name text NOT NULL,
    description text,
    document_path text NOT NULL,
    UNIQUE (package_version_id, kind, name)
);

CREATE TABLE documentation_pages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    package_version_id uuid NOT NULL REFERENCES package_versions(id) ON DELETE CASCADE,
    path text NOT NULL,
    title text,
    content_type text NOT NULL,
    s3_key text NOT NULL,
    digest text NOT NULL CHECK (digest ~ '^sha256:[0-9a-f]{64}$'),
    size_bytes bigint NOT NULL CHECK (size_bytes >= 0),
    content text,
    UNIQUE (package_version_id, path)
);

COMMENT ON COLUMN documentation_pages.content IS
    'Optional local-development content. Production documents are read from the immutable S3 object identified by s3_key.';

CREATE TABLE ingestion_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id text NOT NULL UNIQUE,
    idempotency_key text NOT NULL UNIQUE,
    event_type text NOT NULL,
    schema_version integer NOT NULL,
    package_digest text NOT NULL,
    status text NOT NULL CHECK (status IN ('received', 'processing', 'completed', 'failed', 'quarantined')),
    attempts integer NOT NULL DEFAULT 0,
    correlation_id text NOT NULL,
    error_code text,
    error_detail text,
    received_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz
);

CREATE TABLE lifecycle_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    package_id uuid NOT NULL REFERENCES packages(id) ON DELETE CASCADE,
    package_version_id uuid REFERENCES package_versions(id) ON DELETE CASCADE,
    event_type text NOT NULL,
    reason text NOT NULL,
    replacement_source text,
    incident_reference text,
    effective_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE audit_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at timestamptz NOT NULL,
    actor_type text NOT NULL,
    actor_id text NOT NULL,
    action text NOT NULL,
    resource_type text NOT NULL,
    resource_id text NOT NULL,
    correlation_id text NOT NULL,
    detail jsonb NOT NULL DEFAULT '{}'::jsonb,
    evidence_s3_uri text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE reconciliation_runs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    mode text NOT NULL CHECK (mode IN ('dry-run', 'repair')),
    scope text NOT NULL,
    status text NOT NULL CHECK (status IN ('running', 'completed', 'failed')),
    discrepancies integer NOT NULL DEFAULT 0,
    repaired integer NOT NULL DEFAULT 0,
    report_s3_uri text,
    started_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz
);

CREATE INDEX package_versions_package_published_idx ON package_versions(package_id, published_at DESC);
CREATE INDEX packages_visibility_lifecycle_idx ON packages(visibility, lifecycle);
CREATE INDEX audit_events_resource_idx ON audit_events(resource_type, resource_id, occurred_at DESC);
CREATE INDEX audit_events_correlation_idx ON audit_events(correlation_id);
CREATE INDEX ingestion_events_status_received_idx ON ingestion_events(status, received_at);
