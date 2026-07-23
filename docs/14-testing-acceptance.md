# Testing and acceptance

## Backend

- Run Gradle formatting, compilation correctness, nullability, Modulith, ArchUnit, PMD, SpotBugs/FindSecBugs, tests, coverage, and build gates.
- Verify PostgreSQL migrations from an empty database and an upgraded persistent database.
- Verify full-text/trigram relevance and APM filtering in every list/search/detail path.
- Verify queue idempotency, concurrent `SKIP LOCKED` claims, retry backoff, stale-claim recovery, quarantine, and dead-letter inspection.
- Verify all Artifactory access passes through the official JFrog Java Client adapter.

## UI

- Run Biome, strict TypeScript, typed ESLint, dependency-cruiser, Knip, Stylelint, Vitest/coverage, production build, and bundle budgets.
- Run Playwright and axe over signed-in, empty, unauthorized, error, responsive, and theme states.
- Compare the Registry side by side with the official Terraform Registry at 1440, 1024, 768, and 390 pixels.

## Compose acceptance

1. Build and start PostgreSQL, the combined API/worker, and UI with health waits.
2. Assert no OpenSearch or LocalStack container exists.
3. Seed/reconcile the governed JFrog catalog.
4. Assert package counts, PostgreSQL search, documentation, and direct-route authorization.
5. Submit an exact signed webhook and measure database-acceptance-to-search/UI latency.
6. Confirm queue pending count and dead-letter count are zero after healthy processing.
7. Confirm API/UI logs and browser console are clean.
