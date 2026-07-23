CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE packages
    ADD COLUMN IF NOT EXISTS search_keywords text[] NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS search_document tsvector NOT NULL DEFAULT ''::tsvector;

CREATE OR REPLACE FUNCTION registry_update_package_search_document()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.search_document :=
        setweight(to_tsvector('simple', coalesce(NEW.name, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(NEW.namespace, '')), 'B') ||
        setweight(to_tsvector('simple', coalesce(NEW.description, '')), 'C') ||
        setweight(
            to_tsvector('simple', array_to_string(NEW.search_keywords, ' ')),
            'B');
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS packages_search_document_trigger ON packages;
CREATE TRIGGER packages_search_document_trigger
BEFORE INSERT OR UPDATE OF namespace, name, title, description, search_keywords
ON packages
FOR EACH ROW
EXECUTE FUNCTION registry_update_package_search_document();

UPDATE packages
SET search_keywords = search_keywords;

CREATE INDEX IF NOT EXISTS packages_search_document_idx
    ON packages USING gin (search_document);

CREATE INDEX IF NOT EXISTS packages_search_identity_trgm_idx
    ON packages USING gin (
        lower(namespace || '/' || name || '/' || target) gin_trgm_ops);

CREATE TABLE IF NOT EXISTS catalog_event_queue (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id text NOT NULL UNIQUE,
    payload jsonb NOT NULL,
    status text NOT NULL DEFAULT 'queued'
        CHECK (status IN ('queued', 'processing', 'retry', 'completed', 'dead_letter')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    available_at timestamptz NOT NULL DEFAULT now(),
    claimed_at timestamptz,
    completed_at timestamptz,
    failure_code text,
    failure_detail text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS catalog_event_queue_pending_idx
    ON catalog_event_queue (available_at, created_at)
    WHERE status IN ('queued', 'retry');

CREATE INDEX IF NOT EXISTS catalog_event_queue_dead_letter_idx
    ON catalog_event_queue (updated_at DESC)
    WHERE status = 'dead_letter';

CREATE OR REPLACE VIEW catalog_event_dead_letters AS
SELECT id,
       event_id,
       payload,
       attempts,
       failure_code,
       failure_detail,
       created_at,
       updated_at
FROM catalog_event_queue
WHERE status = 'dead_letter';

ALTER TABLE documentation_pages
    RENAME COLUMN s3_key TO storage_key;

ALTER TABLE approvals
    RENAME COLUMN evidence_s3_uri TO evidence_uri;

ALTER TABLE audit_events
    RENAME COLUMN evidence_s3_uri TO evidence_uri;

ALTER TABLE reconciliation_runs
    RENAME COLUMN report_s3_uri TO report_uri;

COMMENT ON COLUMN documentation_pages.content IS
    'Authoritative UTF-8 documentation stored in PostgreSQL. Legacy null rows are repaired by reconciliation.';

COMMENT ON TABLE catalog_event_queue IS
    'Durable PostgreSQL work queue with retry, stale-claim recovery, and dead-letter states.';

UPDATE package_versions
SET active = NOT revoked;

DROP TABLE IF EXISTS search_outbox;
