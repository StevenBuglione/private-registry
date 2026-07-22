# Work breakdown

## UI

- Maintain the first-party React application and local assets.
- Preserve authorized search, filters, package/version/docs navigation, APM selection, SSE refresh, and closed/error states.
- Run component/accessibility tests and source-vs-local browser QA at required breakpoints.

## Identity and catalog API

- Verify ALB Entra assertions and local OAuth2 sessions.
- Resolve configured group membership through delegated Graph checks.
- Require `AccessContext` for every catalog surface and keep unauthorized routes indistinguishable from missing records.
- Maintain deterministic cursor pagination and version-aware documentation/governance routes.

## JFrog and ingestion

- Use only the official JFrog Java client for Artifactory operations.
- Bootstrap, seed, verify, and reconcile immutable provider/module versions and governance properties.
- Validate signed webhook intake before EventBridge acceptance.
- Prove duplicates, ordering, retries, quarantine, DLQ, outbox recovery, and reconciliation.

## Delivery

- Keep Compose reproducible and all services healthy.
- Keep credentials/state/caches out of Git.
- Run full automated, runtime, secret, and branding checks.
- Commit and push only when the completion audit has direct evidence for every requirement.
