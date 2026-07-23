# Private Registry

A production-oriented starter for an authenticated, governed infrastructure package registry. The repository contains a first-party React UI, a Java 25 Spring Boot API/worker, and a complete local Docker Compose environment backed by PostgreSQL and an existing JFrog Artifactory instance.

JFrog remains authoritative for immutable provider and module bytes. PostgreSQL is the Registry's only stateful application service: it owns catalog and authorization state, documentation, full-text search, durable events, retries, dead letters, reconciliation, and audit records.

```text
Browser -> Registry UI -> Java API/worker -> PostgreSQL
                                  -> authorized SSE updates

JFrog webhook -> signed intake -> PostgreSQL queue -> Java worker
                                                    -> JFrog Java Client validation
                                                    -> PostgreSQL activation + NOTIFY
```

## Included

- React 19, Vite, TanStack Query, React Router, Headless UI, Tailwind, and sanitized Markdown.
- Java 25, Spring Boot 4.1, Spring MVC with virtual threads, Gradle, JDBC, and Flyway.
- Real Microsoft Entra OIDC for local Compose and strict ALB OIDC assertion verification for production.
- Delegated Microsoft Graph `checkMemberGroups` authorization with fail-closed APM filtering.
- The official JFrog Artifactory Java Client for repository bootstrap, seeding, reconciliation, and artifact reads.
- PostgreSQL full-text/trigram search, durable queue, retry/dead-letter processing, signed webhook intake, idempotent ingestion, quarantine, reconciliation, and authorized SSE invalidation.
- A curated catalog manifest for 12 providers and 30 multi-cloud modules, with module inputs, outputs, dependencies, and declared resources extracted from upstream Terraform source.
- Provider guides, resources, data sources, and functions extracted from the mirrored provider source and served through the authorized documentation API.

## Local prerequisites

- Docker Desktop with Compose v2.
- Access to a JFrog Artifactory instance through an existing local JFrog CLI configuration. The CLI is used only to export credentials; application Artifactory operations use the official Java client.
- Azure CLI and Terraform only when creating the free Entra acceptance-test identities.

## Configure local secrets

From `repositories/private-registry-api`:

```powershell
.\scripts\export-jfrog-env.ps1
.\scripts\bootstrap-local-eventing-env.ps1
```

For real local Entra login, apply the isolated identity root and export its sensitive outputs to the ignored Compose environment file:

```powershell
terraform -chdir=infrastructure/terraform/identity-test init
terraform -chdir=infrastructure/terraform/identity-test apply
.\infrastructure\terraform\identity-test\scripts\export-compose-env.ps1
```

That root creates directory objects only: one application, service principal, delegated `User.Read` grant, two APM groups, one administrator group, and three unlicensed test users. It creates no subscription resources or paid Azure infrastructure. Never commit the generated state or `.env.*` files.

## Start and verify Compose

```powershell
docker compose up --build --detach --wait
Invoke-RestMethod http://localhost:8080/health/ready
```

Open <http://localhost:3000/>. The local profile uses the real Entra application and keeps OAuth and Graph tokens server-side.

Interactive API documentation is available at <http://localhost:3000/swagger-ui.html> after sign-in.
Springdoc generates the specification from the running Spring controllers so the page always lists
the complete application API, including catalog, administration, synchronization, compatibility,
webhook, and health endpoints.

Seed the governed JFrog repositories and curated catalog with the dedicated, resumable seeder:

```powershell
docker compose --profile seed run --rm seeder
```

The seeder preserves immutable release archives, verifies upstream checksums, refreshes only governed metadata that changed, and completes a full PostgreSQL reconciliation before it exits.

Stop the stack without deleting data:

```powershell
docker compose down
```

Use `docker compose down --volumes` only when intentionally discarding the PostgreSQL catalog, queue, documents, and search data.

## Build and test

```powershell
cd repositories/private-registry-api
.\gradlew.bat clean test bootJar --no-configuration-cache

cd ..\private-registry-ui\web
corepack pnpm install --frozen-lockfile
corepack pnpm test:e2e:install
corepack pnpm quality:full
```

## Repository layout

Administration and GitHub runner setup are documented in [docs/administration.md](docs/administration.md).

- `repositories/private-registry-ui/` — first-party UI, Nginx runtime, assets, and browser-facing configuration.
- `repositories/private-registry-api/` — API, identity, JFrog client adapter, event worker, migrations, seed manifest, Compose, and deployment infrastructure.
- `contracts/` — shared catalog and event contracts.
- `docs/` — target architecture, security, operations, and acceptance guidance.

See the component READMEs for detailed configuration and operational commands.
