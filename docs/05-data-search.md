# Data and search

PostgreSQL stores packages, immutable versions, APM grants, owners, approvals, symbols, validated UTF-8 documentation, ingestion history, events, retries, dead letters, reconciliation checkpoints, audit records, and UI settings.

Package writes maintain a weighted `tsvector` over identity, title, namespace, description, and governed keywords. A GIN index supports full-text search; `pg_trgm` indexes improve exact-ish namespace/name/target matching. Search SQL applies active-version and APM authorization predicates before returning identifiers.

Cursor pagination operates on stable server-side ordering. Unauthorized and nonexistent routes produce the same result. Search has no eventually consistent external projection, so activation and discoverability commit together.

Documentation bytes are digest-verified, size-bounded, decoded as strict UTF-8, and stored with their version row. Existing installations with legacy null document content are repaired by a full reconciliation; the Compose seeder performs one automatically.
