CREATE TABLE IF NOT EXISTS package_apm_access (
    package_id uuid NOT NULL REFERENCES packages(id) ON DELETE CASCADE,
    apm_id varchar(128) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (package_id, apm_id)
);

ALTER TABLE package_versions
    ADD COLUMN IF NOT EXISTS active boolean NOT NULL DEFAULT true;

ALTER TABLE ingestion_events
    ALTER COLUMN package_digest DROP NOT NULL;

ALTER TABLE ingestion_events
    ADD COLUMN IF NOT EXISTS source_repository text,
    ADD COLUMN IF NOT EXISTS source_path text,
    ADD COLUMN IF NOT EXISTS payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS last_attempt_at timestamptz;

CREATE TABLE IF NOT EXISTS ingestion_quarantine (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id text NOT NULL,
    source_repository text NOT NULL,
    source_path text NOT NULL,
    reason_code text NOT NULL,
    reason_detail text NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    quarantined_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (event_id, reason_code)
);

CREATE TABLE IF NOT EXISTS search_outbox (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type text NOT NULL,
    aggregate_id uuid NOT NULL,
    package_version_id uuid NOT NULL REFERENCES package_versions(id) ON DELETE CASCADE,
    index_name text NOT NULL,
    document_id text NOT NULL,
    payload jsonb NOT NULL,
    status text NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'processing', 'completed', 'failed', 'quarantined')),
    attempts integer NOT NULL DEFAULT 0,
    available_at timestamptz NOT NULL DEFAULT now(),
    claimed_at timestamptz,
    completed_at timestamptz,
    last_error text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (index_name, document_id)
);

CREATE INDEX IF NOT EXISTS search_outbox_pending_idx
    ON search_outbox (status, available_at, created_at);

CREATE INDEX IF NOT EXISTS ingestion_quarantine_source_idx
    ON ingestion_quarantine (source_repository, source_path, quarantined_at DESC);

CREATE TABLE IF NOT EXISTS ingestion_checkpoints (
    checkpoint_name text PRIMARY KEY,
    checkpoint_value text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);

COMMENT ON COLUMN package_versions.active IS
    'A newly ingested version becomes active only after its deterministic OpenSearch document is indexed.';
