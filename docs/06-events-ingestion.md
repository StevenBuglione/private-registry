# Events and ingestion

JFrog sends a signed JSON webhook to `/api/v1/internal/webhooks/jfrog`. The API validates signature, origin, subscription, repository/path allowlist, content type, and size before inserting the normalized event into PostgreSQL.

`catalog_event_queue.event_id` is unique. A duplicate delivery returns the original publication identity without creating duplicate work. Consumers claim available rows with `FOR UPDATE SKIP LOCKED`, allowing any API replica to work safely.

Processing re-reads current artifact metadata, properties, manifest, documentation, and archives through the official JFrog Java Client. It verifies digests, immutable versions, APM assignments, schemas, archive safety, and UTF-8 documentation before one PostgreSQL activation transaction.

Transient failures use timestamp-based bounded backoff. Terminal validation failures and exhausted retries use `dead_letter` status and appear in `catalog_event_dead_letters`. Claims abandoned by a crashed worker are recovered. Incremental and full reconciliation repair lost webhook hints.
