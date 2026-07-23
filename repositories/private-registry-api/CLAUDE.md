# Repository instructions

## Before coding or deploying

1. Read `docs/project-structure.md`, `docs/architecture.md`, `docs/compatibility-contract.md`, `docs/adapter-implementation.md`, and `docs/deployment-runbook.md`.
2. Keep JFrog as the package data plane and use the official Artifactory Java Client for every Artifactory operation.
3. Keep PostgreSQL as the only stateful application service. Do not introduce a separate search engine, object store, message queue, or event bus.
4. Keep all catalog authorization in the API, never in UI-only filtering.
5. Preserve server-side Entra sessions and never expose delegated Graph tokens to the browser.

## Implementation order

1. Keep the OpenAPI compatibility contract and DTO tests synchronized.
2. Put durable metadata, documents, search, queue, dead-letter, reconciliation, and audit state in PostgreSQL.
3. Validate JFrog artifacts, checksums, governance properties, and archive safety before activation.
4. Use PostgreSQL `FOR UPDATE SKIP LOCKED` for concurrent workers and `LISTEN`/`NOTIFY` only for low-latency signals.
5. Keep webhook processing idempotent and reconciliation capable of repairing missed events.
6. Verify ALB OIDC assertions and delegated Graph membership fail closed.
7. Keep the local permit-all profile out of production task definitions.
8. Run the complete local and pull-request quality gates before publishing changes.

Never store secrets, private keys, account IDs, internal DNS names, or real endpoints in source control.
