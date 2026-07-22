ALTER TABLE search_outbox
    ADD COLUMN IF NOT EXISTS revision bigint NOT NULL DEFAULT 1;

COMMENT ON COLUMN search_outbox.revision IS
    'Optimistic generation used to prevent a worker from completing a superseded outbox payload.';
