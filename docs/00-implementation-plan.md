# Implementation plan

The Registry is a first-party React UI plus one Java 25 Spring Modulith application. PostgreSQL is its only stateful application service.

1. **Identity plane:** real Entra OIDC, server-side sessions, delegated Graph membership, and fail-closed APM authorization.
2. **Artifact plane:** governed provider/module bytes in JFrog, accessed only through the official JFrog Java Client.
3. **Catalog plane:** package metadata, documentation, governance, full-text/trigram search, and UI settings in PostgreSQL.
4. **Event plane:** signed JFrog webhook intake to an idempotent PostgreSQL queue with retries, stale-claim recovery, and a dead-letter view.
5. **Update plane:** Java worker re-reads current JFrog state, validates content, activates it transactionally, and emits PostgreSQL notifications for authorized SSE refresh.
6. **Recovery plane:** 15-minute incremental and nightly full reconciliation, plus startup repair in Compose.
7. **Delivery plane:** PostgreSQL, combined API/worker, and UI in Docker Compose; production Terraform does not create application S3 buckets, OpenSearch, SQS, or EventBridge.
