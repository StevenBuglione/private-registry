# ADR: Consolidate Registry runtime state in PostgreSQL

**Status:** Accepted; supersedes ADR 0005 and ADR 0006.

## Decision

Use PostgreSQL for catalog metadata, validated documentation, full-text/trigram search, durable event work, retries, dead letters, reconciliation checkpoints, audit records, and activation notifications. Run HTTP handling, queue processing, and reconciliation in one Java 25 Spring Modulith image. Retain JFrog as the artifact authority.

## Consequences

- Local Compose needs only PostgreSQL, the API/worker, and UI.
- Production no longer needs application S3 buckets, OpenSearch, SQS, EventBridge, Scheduler, or separate indexer/reconciler services.
- Catalog backup and restore has one consistent state boundary.
- Workers scale horizontally using `FOR UPDATE SKIP LOCKED`.
- PostgreSQL capacity and query plans must be monitored because search and queue load now share the database.
- `LISTEN`/`NOTIFY` is an optimization; durable tables remain the source of truth.
