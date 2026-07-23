# Implementation status

The repository is an executable starter, not only a blueprint.

Implemented surfaces include:

- tracked first-party React UI and Nginx image;
- Java 25 Spring Boot API and worker with Gradle;
- real Entra OAuth2 login, ALB assertion verification, delegated Graph membership checks, and APM authorization;
- PostgreSQL/Flyway catalog, documents, full-text search, durable event queue, dead letters, and authorized SSE;
- official JFrog Java client adapter, repository bootstrap, curated seed manifest, signed webhook intake, idempotent ingestion, quarantine, and reconciliation;
- complete Docker Compose environment with health waits;
- isolated free Entra acceptance-identity Terraform root;
- backend and UI automated tests plus browser QA workflow.

Production-specific AWS account, network, certificate, DNS, KMS, secret-manager, and disaster-recovery inputs remain environment-owned. Their Terraform modules are not required for local Compose acceptance.

Do not call a release complete until `web/design-qa.md` says `final result: passed`, the curated JFrog catalog is reconciled, all three real Entra identities prove distinct access, latency/error/database-queue evidence is clean, and the scoped branch is pushed.
