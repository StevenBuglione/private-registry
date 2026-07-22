# Testing and acceptance

## Backend

- ALB ES256 signature, algorithm, signer ARN, client ID, issuer, expiration, identity-header match, key rotation, and cache behavior.
- Real local Entra OIDC and delegated Graph membership for APM-A, APM-B, and administrator users.
- Authorization across list, count, search, detail, version, docs, governance, SSE, and direct inaccessible routes.
- Webhook signature/origin/subscription/repository/path/content-type/size validation.
- Duplicate and out-of-order idempotency, retry classification, quarantine, DLQ, outbox recovery, and reconciliation.
- Architecture check preventing raw Artifactory HTTP integrations outside the official JFrog Java client adapter.

## UI

- Unit/component behavior and accessibility checks.
- Login, APM switching, search suggestions, filters, pagination, package/version/docs navigation, copy actions, no entitlement, session expiry, Graph/API outage, legacy redirects, and SSE refresh.
- Authenticated source-vs-local comparison at 1440, 1024, 768, and 390 pixels.
- Browser console and network inspection proving unauthorized metadata is never delivered.

## Compose end to end

1. Build and start PostgreSQL, OpenSearch, LocalStack, API, indexer, and UI with health waits.
2. Seed three JFrog local repositories with at least 12 providers and 30 modules; verify properties and SHA-256 digests through the official Java client.
3. Sign in as each real Entra acceptance user and prove distinct APM-visible catalogs.
4. Publish a package version, submit the exact signed webhook payload, and measure searchable/UI-visible latency against P95 <= 5 seconds and P99 <= 30 seconds.
5. Confirm readiness, worker dependencies, logs, queues, DLQ, outbox, reconciliation, browser console, and network responses are clean.
6. Run full Gradle and UI builds/tests, secret scanning, runtime-brand scanning, and worktree review.

The starter is complete only when direct evidence satisfies every item, `web/design-qa.md` says `final result: passed`, and the scoped commit is pushed.
