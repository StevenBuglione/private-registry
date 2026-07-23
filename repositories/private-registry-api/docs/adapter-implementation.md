# Production adapter implementation

The Java 25 Spring Boot service intentionally has two external adapters: PostgreSQL for all application state and the official JFrog Artifactory Java Client for governed artifact access.

## PostgreSQL adapter

- Connect through the platform's managed PostgreSQL endpoint with TLS verification.
- Use separate application and migration users and bounded Hikari pools.
- Keep package, version, document, authorization, event, DLQ, and audit changes transactional.
- Use `tsvector`/GIN and `pg_trgm` indexes for authorized catalog search.
- Claim queue rows with `FOR UPDATE SKIP LOCKED`; never hold a transaction open while calling JFrog.
- Recover abandoned processing claims and retain terminal failures for operator inspection.
- Use `LISTEN`/`NOTIFY` only as a low-latency wake-up signal; tables remain the durable source of truth.
- Back up and restore the database as one consistent unit.

## JFrog client

- Use only the official Artifactory Java Client; architecture tests reject alternate Artifactory HTTP clients.
- Use a read-only token for runtime ingestion and a separate publishing identity for seeding.
- Verify artifact and manifest SHA-256 values, repository allowlists, and content-size limits.
- Reject unsafe archives and invalid UTF-8 documentation before database activation.
- Treat JFrog properties as governance evidence, never as trusted HTML.
- Keep JFrog out of API readiness so existing catalog reads remain available during an outage.

## Identity and authorization

Verify the ALB `x-amzn-oidc-data` JWT signature with the expected signer ARN, issuer, client ID, expiration, and matching identity header. Local Compose uses the same real Entra application through Spring OAuth2. Resolve configured APM memberships through Microsoft Graph, keep delegated tokens server-side, fail closed on Graph errors, and apply the resulting `AccessContext` to every catalog query.

## Worker semantics

- Idempotency is enforced by both the transport event ID and a canonical SHA-256
  key over the artifact action, repository, path, occurrence time, and sorted
  properties. Replayed deliveries therefore collapse without suppressing a later
  legitimate change to the same artifact.
- Transient failures use bounded retries and database timestamps for backoff.
- Validation and quarantine failures enter the PostgreSQL dead-letter view immediately.
- Processing claims older than the recovery threshold become retryable.
- Reconciliation repairs missed webhooks and older database rows.
- Correlation and event IDs are retained with every queue row and ingestion record.
