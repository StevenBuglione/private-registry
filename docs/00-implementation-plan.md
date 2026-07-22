# Implementation plan

## Product outcome

Deliver an authenticated internal Registry where engineers can search the provider and module versions authorized for their APM groups, read normalized documentation and governance metadata, and copy JFrog-backed install configuration. JFrog continues serving package bytes independently when the catalog is unavailable.

## Runtime layers

1. **Package data plane:** three governed JFrog local repositories created and accessed only through the official Java client.
2. **Identity plane:** ALB Entra OIDC in production; Spring-managed Entra OAuth2 locally; delegated Graph group checks mapped to APM access.
3. **Event plane:** signed JFrog webhook intake to EventBridge and SQS with DLQ.
4. **Ingestion plane:** reconcile current JFrog metadata/digests, normalize docs to S3, transact PostgreSQL plus search outbox, then activate deterministic OpenSearch documents.
5. **Catalog plane:** Java 25 Spring MVC on virtual threads with authorization-filtered search, details, docs, governance, counts, and SSE.
6. **UI plane:** a first-party React application visually grounded in the Terraform Registry utility layout and using only internal branding/data.

## Acceptance gates

- Curated JFrog catalog has at least 12 providers and 30 modules with required versions, platforms, properties, and digests.
- APM-A, APM-B, administrator, and no-entitlement identities receive the correct catalog without unauthorized response metadata.
- Duplicate/out-of-order events are idempotent; unsafe content is quarantined; retries, DLQ, outbox recovery, and reconciliation are verified.
- Webhook-to-searchable/UI-visible latency meets P95 <= 5 seconds and P99 <= 30 seconds.
- Gradle, UI, Compose, security, branding, accessibility, responsive browser, and console/network checks pass.
- Scoped changes are committed and pushed only after the complete audit is green.
