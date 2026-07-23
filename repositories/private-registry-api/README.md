# Private Registry API

This repository owns the Java 25 Spring Boot catalog API, database migrations, shared contracts, local Docker Compose stack, and infrastructure definition for the private registry control plane.

## Runtime

- Java 25 with Spring Boot 4.1
- Gradle 9.6 through the committed Gradle Wrapper
- Spring MVC, Validation, Security, Actuator, JDBC/Hikari, and Flyway
- JFrog Artifactory Java Client 2.21.2 for Artifactory connectivity and repository operations
- PostgreSQL for catalog metadata, governed documentation, full-text search, event processing, dead letters, reconciliation, and audit state

## Authority model

- JFrog is authoritative for package bytes, package existence, checksums, signatures, and promotion state.
- PostgreSQL is authoritative for package catalog metadata, documentation, ownership, lifecycle, approvals, search, ingestion state, and audit records.
- PostgreSQL's durable queue delivers lifecycle events; scheduled reconciliation recovers from missed webhooks or operator error.
- PostgreSQL `LISTEN`/`NOTIFY` drives low-latency SSE invalidation without becoming a durability dependency.

## Run the complete local stack

```bash
docker compose up --build --wait
curl http://localhost:8080/health/ready
curl 'http://localhost:8080/registry/docs/search?q=vpc'
```

Open <http://localhost:3000> for the complete UI. Compose starts PostgreSQL,
the Java API/worker and the first-party Registry UI. PostgreSQL is the only
stateful Compose service.
Nginx proxies UI data and governance requests to the API on the Compose
network. Flyway applies the production schema and a separate local-only fixture
migration. The default Compose profiles use the real Microsoft Entra application;
OAuth and delegated Graph tokens remain in the server-side session while every
catalog query is filtered to the caller's APM access context.

Compose deliberately separates migration and runtime database authority. Flyway
connects as the schema owner in a one-shot migration container. A second
one-shot bootstrap assigns local runtime credentials, after which the public API
authenticates directly as the narrowly writable `registry_web` role and the
isolated ingestion service authenticates directly as
`registry_indexer`. This keeps catalog mutation authority out of the
internet-facing process while making migrations, web writes, and worker
processing exercise production-shaped grants.

API readiness covers PostgreSQL only, so a JFrog outage does not remove catalog
reads. In the split-process topology, the non-web indexer is monitored through
container health, queue/reconciliation state, and logs. A durable worker-heartbeat
contract is identified as a production blocker in the deployment-readiness audit.

Seed the governed repositories through the official JFrog Java client:

```bash
docker compose --profile seed build seeder
docker compose --profile seed run --rm seeder
```

The seeder persists upstream downloads in the `registry-seed-cache` volume, verifies
provider checksums, uses Artifactory checksum deploy with a streamed-file fallback,
retries interrupted uploads, and performs a full PostgreSQL reconciliation before it
exits. It extracts module inputs, outputs, dependencies,
and declared resources from root Terraform files, plus provider guides, resources,
data sources, and functions from provider source archives. Release archives remain
immutable; governed manifests and documentation are refreshed only after release
equivalence is verified. Re-running it is safe: matching artifacts are skipped. For
a focused recovery run, set
`REGISTRY_SEED_PACKAGES=provider/hashicorp/null` and optionally
`REGISTRY_SEED_VERSIONS=3.2.4` before invoking Compose.

To stop the services while preserving local data:

```bash
docker compose down
```

Use `docker compose down --volumes` only when you intentionally want to erase the local database.

## Build and test

```bash
bash scripts/quality.sh local
bash scripts/quality.sh pr
./gradlew bootJar
```

PowerShell users can run `./scripts/quality.ps1 local` or
`./scripts/quality.ps1 pr`. The Gradle build uses a Java 25 toolchain. No system
Gradle installation is required after the wrapper is committed. See
[`docs/code-quality.md`](docs/code-quality.md) for the analyzer matrix,
suppression policy, CI gates, SonarQube setup, and branch-protection checklist.

## Infrastructure deployment

The current AWS application-service Terraform is a scaffold and must not be deployed
unchanged. Its environment names, database authentication/roles, secret injection,
routing, process separation, and image workflow do not yet match the application.

Start with the repository-level
[`DEPLOYMENT.md`](../../DEPLOYMENT.md), process-specific
[`deploy/environment`](deploy/environment/README.md) templates, and
[`deployment-readiness audit`](../../docs/32-deployment-readiness-audit.md). Close every
mandatory blocker before setting `deploy_application_services = true`.

Read `CLAUDE.md`, `docs/project-structure.md`, `docs/database-design.md`,
`docs/compatibility-contract.md`, and `docs/deployment-runbook.md` before
deployment.
