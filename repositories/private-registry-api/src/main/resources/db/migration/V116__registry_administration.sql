CREATE TABLE registry_sync_credentials (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(80) NOT NULL,
    scope varchar(16) NOT NULL CHECK (scope IN ('module', 'provider', 'all')),
    secret_hash bytea NOT NULL CHECK (octet_length(secret_hash) = 32),
    created_by text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    revoked_by text,
    last_used_at timestamptz,
    use_count bigint NOT NULL DEFAULT 0 CHECK (use_count >= 0),
    CHECK (expires_at > created_at),
    CHECK ((revoked_at IS NULL AND revoked_by IS NULL)
        OR (revoked_at IS NOT NULL AND revoked_by IS NOT NULL))
);

CREATE INDEX registry_sync_credentials_status_idx
    ON registry_sync_credentials (expires_at, revoked_at, created_at DESC);

CREATE INDEX audit_events_occurred_at_idx
    ON audit_events (occurred_at DESC, id DESC);

CREATE INDEX reconciliation_runs_started_at_idx
    ON reconciliation_runs (started_at DESC);

COMMENT ON TABLE registry_sync_credentials IS
    'Scoped, expiring automation credentials. Only a SHA-256 hash of each 256-bit secret is retained.';

COMMENT ON COLUMN registry_sync_credentials.secret_hash IS
    'One-way digest of a high-entropy secret. The plaintext token is returned exactly once.';
