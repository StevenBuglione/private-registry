# Private Registry API and AWS Platform

This repository owns the Java 25 Spring Boot catalog API, database migrations, shared contracts, OpenSearch integration, local Docker Compose stack, and the infrastructure definition for the private registry control plane.

## Runtime

- Java 25 with Spring Boot 4.1
- Gradle 9.6 through the committed Gradle Wrapper
- Spring MVC, Validation, Security, Actuator, JDBC/Hikari, and Flyway
- JFrog Artifactory Java Client 2.21.2 for Artifactory connectivity and repository operations
- PostgreSQL for authoritative catalog and governance metadata
- The official OpenSearch Java client for search-cluster connectivity

## Authority model

- JFrog is authoritative for package bytes, package existence, checksums, signatures, and promotion state.
- Aurora PostgreSQL is authoritative for package catalog metadata, ownership, lifecycle, approvals, ingestion state, and audit records.
- S3 stores normalized immutable versioned documentation, quarantine evidence, reconciliation reports, and audit exports.
- OpenSearch is a derived search index and must be fully rebuildable.
- EventBridge and SQS deliver lifecycle events but reconciliation is required to recover from event loss or operator error.

## Run the complete local stack

```bash
docker compose up --build --wait
curl http://localhost:8080/health/ready
curl 'http://localhost:8080/registry/docs/search?q=vpc'
```

Open <http://localhost:3000> for the complete UI. Compose starts PostgreSQL,
OpenSearch, LocalStack, the Java API and indexer, and the first-party Registry UI.
Nginx proxies UI data and governance requests to the API on the Compose
network. Flyway applies the production schema and a separate local-only fixture
migration. The local security setting permits requests so the full contract can be
exercised without an identity provider.

API readiness covers PostgreSQL and OpenSearch only, so a JFrog outage does not
remove catalog reads. The indexer exposes `/health/worker`, which checks PostgreSQL,
OpenSearch, Artifactory, SQS, and S3 with bounded probes.

Seed the governed repositories through the official JFrog Java client:

```bash
docker compose --profile seed build seeder
docker compose --profile seed run --rm seeder
```

The seeder persists upstream downloads in the `registry-seed-cache` volume, verifies
provider checksums, uses Artifactory checksum deploy with a streamed-file fallback,
and retries interrupted uploads. Re-running it is safe: matching artifacts and
manifests are skipped. For a focused recovery run, set
`REGISTRY_SEED_PACKAGES=provider/hashicorp/null` and optionally
`REGISTRY_SEED_VERSIONS=3.2.4` before invoking Compose.

To stop the services while preserving local data:

```bash
docker compose down
```

Use `docker compose down --volumes` only when you intentionally want to erase the local database and search data.

## Build and test

```bash
./gradlew check
./gradlew bootJar
```

The Gradle build uses a Java 25 toolchain. No system Gradle installation is required after the wrapper is committed.

## Infrastructure deployment

1. Configure and apply `infrastructure/terraform/bootstrap/`.
2. Fill environment backend and variable files under `infrastructure/terraform/live/`.
3. Apply the platform with `deploy_application_services = false`.
4. Build/push UI and API images.
5. Run database migrations and install OpenSearch templates.
6. Apply with `deploy_application_services = true` and immutable image tags.
7. Publish package events only after JFrog promotion completes.

Read `CLAUDE.md`, `docs/project-structure.md`, `docs/compatibility-contract.md`, and `docs/deployment-runbook.md` before deployment.
