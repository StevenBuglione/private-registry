# Registry implementation instructions

This repository contains the complete starter. Preserve the UI/API repository boundary and never commit secrets, Terraform state, generated credentials, downloaded seed archives, or local screenshots.

## Non-negotiable architecture

- JFrog Artifactory is authoritative for package bytes and immutable versions.
- Every Artifactory operation goes through the official JFrog Artifactory Java Client adapter.
- PostgreSQL owns catalog, authorization, lifecycle, governance, ingestion, and outbox state.
- S3-compatible storage owns normalized immutable documentation.
- OpenSearch is a derived, rebuildable search projection.
- EventBridge and SQS events are reconciliation hints; workers re-read current JFrog state.
- The browser calls only the same-origin Java API and never receives service credentials or OAuth tokens.
- Production uses ALB-managed Entra OIDC. Local Compose uses the same real Entra application through Spring OAuth2 login.
- Every catalog query requires an `AccessContext` and fails closed on identity or Graph errors.

## Change rules

- Keep the UI first-party. Do not add upstream clone, patch, overlay, or runtime network dependencies.
- Maintain canonical plural routes and explicit legacy redirects.
- Return the same 404 for inaccessible, revoked, and nonexistent package versions.
- Add a Flyway migration for every schema change; never mutate released artifact content.
- Keep Java on version 25, Spring Boot on the declared 4.1 line, and Gradle as the build entry point.
- Keep blocking JFrog/JDBC/OpenSearch adapters on bounded virtual threads; do not introduce WebFlux without a new architecture decision.
- Verify UI changes in the in-app browser at 1440, 1024, 768, and 390 pixels.
- Run Gradle tests, UI tests/lint/build, Compose health checks, secret scanning, and branding scanning before commit.

## Local sequence

1. Configure ignored `.env.identity-test`, `.env.artifactory`, and `.env.eventing` files as documented in `README.md`.
2. Run `docker compose up --build --detach --wait` from `repositories/private-registry-api`.
3. Run `docker compose --profile seed run --rm seeder` when refreshing JFrog.
4. Restart the indexer and verify catalog counts, queues, DLQ, outbox, and worker health.
5. Sign in with the real APM-A, APM-B, and administrator acceptance identities and prove distinct catalogs.
6. Finish with clean automated tests and authenticated browser QA.
