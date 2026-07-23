# Environment variable reference

This is the runtime contract for the Registry as of 2026-07-23. The canonical examples are
in [`deploy/environment`](../repositories/private-registry-api/deploy/environment/README.md).
Spring Boot relaxed binding maps `REGISTRY_EVENTING_ENABLED` to
`registry.eventing.enabled`, for example.

## Rules

1. Give each process only the values it consumes.
2. Put secret values in the deployment platform's secret mechanism, never plaintext
   Terraform variables, task-definition `environment`, image layers, GitHub variables, or
   committed `.env` files.
3. The Java process reads secret **values**. It does not resolve Secrets Manager ARNs.
4. Do not enable `REGISTRY_SECURITY_PERMIT_ALL` on a public web process.
5. Do not enable Flyway on horizontally scaled API or indexer services.
6. Use the database roles `registry_web`, `registry_indexer`, and a dedicated migration
   owner; do not use the cluster master for a long-running service.
7. Durations use Spring formats such as `500ms`, `30s`, `15m`, or `7d`. Cron expressions
   are six-field Spring expressions in UTC.

## API and common Spring settings

| Variable | Required | Secret | Default | Meaning |
|---|---:|---:|---|---|
| `SPRING_PROFILES_ACTIVE` | No | No | empty | Use `local,entra` only for local Compose OIDC. Production ALB mode does not use the `entra` profile. Never use `local` in production because it loads fixtures. |
| `SPRING_DATASOURCE_URL` | Yes | No | local JDBC URL | PostgreSQL JDBC URL. Require TLS in production. |
| `SPRING_DATASOURCE_USERNAME` | Yes | No | `registry` | API=`registry_web`; worker/seeder=`registry_indexer`; migration=schema owner. |
| `SPRING_DATASOURCE_PASSWORD` | Yes | Yes | `registry` | Password or a value supplied by a tested authentication adapter. |
| `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | No | No | `10` | Bound per-task connections against total PostgreSQL capacity. |
| `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE` | No | No | `2` | Minimum idle connections. |
| `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT` | No | No | `5000` ms | Pool acquisition timeout. |
| `SPRING_DATASOURCE_HIKARI_VALIDATION_TIMEOUT` | No | No | `3000` ms | Connection validation timeout. |
| `SPRING_FLYWAY_ENABLED` | Yes | No | `true` | `true` only in the one-shot migration process; `false` in API, worker, and seeder. |
| `SPRING_FLYWAY_URL` | Migration | No | datasource URL | Schema migration JDBC URL. |
| `SPRING_FLYWAY_USER` | Migration | No | datasource user | Schema-owner role. |
| `SPRING_FLYWAY_PASSWORD` | Migration | Yes | datasource password | Schema-owner credential. |
| `SERVER_PORT` | No | No | `8080` | API listener port. |
| `SPRING_MAIN_WEB_APPLICATION_TYPE` | Worker/jobs | No | servlet | Set to `none` for indexer, migration, and seeder. CLI argument `--spring.main.web-application-type=none` is equivalent. |
| `SPRING_MAIN_KEEP_ALIVE` | Indexer | No | false | Set `true` for the long-running non-web indexer. |
| `REGISTRY_RUNTIME_EXIT_AFTER_STARTUP` | Migration | No | false | Stops the one-shot migration process after startup. |

The API uses Java 25 virtual threads. Database and JFrog calls are still blocking adapters,
so connection pools, timeouts, and downstream concurrency remain bounded.

## Artifactory

| Variable | Required | Secret | Default | Meaning |
|---|---:|---:|---|---|
| `REGISTRY_ARTIFACTORY_URL` | Yes | No | invalid example URL | Full URL ending in `/artifactory`. Prefer this explicit variable. |
| `REGISTRY_JFROG_HOSTNAME` | Local/UI | No | invalid example hostname | Hostname shorthand used by Compose and UI. Java derives its URL from it only when the explicit URL is absent. |
| `REGISTRY_ARTIFACTORY_ACCESS_TOKEN` | Yes | Yes | empty | Primary Java-client token setting. |
| `JFROG_ACCESS_TOKEN` | Local/scripts | Yes | empty | Supported Java fallback and JFrog CLI convention. Prefer the primary variable in production. |
| `REGISTRY_ARTIFACTORY_CONNECTION_TIMEOUT` | No | No | `3s` | TCP connection timeout. Seeder should use a larger value such as `30s`. |
| `REGISTRY_ARTIFACTORY_SOCKET_TIMEOUT` | No | No | `5s` | Socket/read timeout. Seeder needs enough time for large upstream artifacts. |
| `REGISTRY_ARTIFACTORY_MODULE_REPOSITORY` | No | No | `iac-modules-public-remote` | Module source/read repository used by diagnostics and compatibility paths. |
| `REGISTRY_ARTIFACTORY_PROVIDER_REPOSITORY` | No | No | `iac-providers-public-remote` | Provider source/read repository used by diagnostics and compatibility paths. |

All Java interactions with Artifactory must remain behind the official JFrog Artifactory
Java Client adapter. Do not add ad hoc REST clients elsewhere in the code.

Recommended token split:

| Principal | JFrog permissions |
|---|---|
| Seeder/bootstrap | Create the three governed local repositories; deploy, annotate, read, AQL, and verify. Short-lived only. |
| Indexer | Read/download/file-info/properties/AQL on governed repositories. No delete or overwrite. |
| API | Read-only diagnostic access needed by the Artifactory status/admin view. |
| GitHub publisher | Deploy only to its package paths; no catalog administration. |

## Production ALB identity and Graph authorization

| Variable | Required | Secret | Default | Meaning |
|---|---:|---:|---|---|
| `REGISTRY_SECURITY_PERMIT_ALL` | Yes | No | `false` | Must remain `false` for API. Worker/jobs may set true because they have no web listener. |
| `REGISTRY_ALLOWED_ALB_SIGNER_ARN` | Production API | No | empty | Exact ALB ARN accepted in `x-amzn-oidc-data`. |
| `REGISTRY_ALLOWED_OIDC_CLIENT_ID` | Production API | No | empty | Exact Entra application/client ID expected in the ALB token. |
| `REGISTRY_ALLOWED_OIDC_ISSUER` | Production API | No | empty | Expected issuer, normally `https://login.microsoftonline.com/<tenant>/v2.0`. |
| `AWS_REGION` | Production API | No | `us-east-1` | Region used to fetch the ALB public signing key. |
| `REGISTRY_GRAPH_ENDPOINT` | No | No | Microsoft Graph `/me/checkMemberGroups` | Delegated membership endpoint. |
| `REGISTRY_GRAPH_TIMEOUT` | No | No | `5s` | Fail-closed Graph request timeout. |
| `REGISTRY_MEMBERSHIP_CACHE_TTL` | No | No | `60s` | Successful membership cache duration. Tokens are never cached. |
| `REGISTRY_ENTRA_APM_GROUPS` | Production API | No | empty | Comma-separated `APM_ID:GROUP_OBJECT_ID` mappings. Example: `APM0000001:guid,APM0000002:guid`. |
| `REGISTRY_ENTRA_ADMIN_GROUP_ID` | Production API | No | empty | Entra group object ID whose members receive registry-admin access. |
| `REGISTRY_POST_LOGOUT_REDIRECT_URI` | No | No | `/` | Safe post-logout path/URI. |

`APM_ID` values follow the database constraint `APM` plus seven digits. Every package must
be assigned to at least one APM. Multiple mappings are checked in Graph batches of at most
20 configured group object IDs.

The ALB OIDC action has a separate client secret. That secret belongs in the Terraform
runner/deployment secret store and the ALB listener configuration; it is not a Java
environment variable in production.

## Local Entra OIDC

The `entra` Spring profile consumes:

| Variable | Required | Secret | Meaning |
|---|---:|---:|---|
| `ENTRA_TENANT_ID` | Yes | No | Tenant GUID used in the issuer URI. |
| `ENTRA_CLIENT_ID` | Yes | No | Local Registry application client ID. |
| `ENTRA_CLIENT_SECRET` | Yes | Yes | Confidential-client secret. |
| `REGISTRY_ENTRA_APM_GROUPS` | Yes | No | Same mapping contract as production. |
| `REGISTRY_ENTRA_ADMIN_GROUP_ID` | Yes | No | Local administrator test group. |

The identity export script also writes `REGISTRY_OIDC_*`,
`REGISTRY_ENTRA_TENANT_ID`, and `REGISTRY_E2E_*`. Those are handoff/test metadata and are
not read by the current Spring runtime. `REGISTRY_E2E_*` contains test passwords and must
be treated as secret.

Local scopes are configured in `application-entra.yaml` as
`openid,profile,email,offline_access,User.Read`. Local callback:
`http://localhost:3000/login/oauth2/code/entra`.

## PostgreSQL eventing and worker

| Variable | Required | Default | Meaning |
|---|---:|---|---|
| `REGISTRY_EVENTING_ENABLED` | Yes | `false` | Enables PostgreSQL event services. API and worker use true; migration uses false. |
| `REGISTRY_EVENT_POLL_INTERVAL` | No | `30s` | Worker queue polling delay. |
| `REGISTRY_EVENT_CLAIM_RECOVERY_DELAY` | No | `1m` | Stale-claim recovery schedule. |
| `REGISTRY_EVENT_CLAIM_TIMEOUT` | No | `5m` | Age after which an in-flight claim can be recovered. |
| `REGISTRY_EVENT_POLL_BATCH_SIZE` | No | `25` | Claimed rows per poll; accepted range 1–100. |
| `REGISTRY_EVENT_MAXIMUM_ATTEMPTS` | No | `5` | Attempts before dead-lettering. |
| `REGISTRY_EVENT_COMPLETED_RETENTION` | No | `7d` | Completed event retention. |
| `REGISTRY_EVENT_DEAD_LETTER_RETENTION` | No | `90d` | Dead-letter retention. |
| `REGISTRY_EVENTING_RETENTION_CLEANUP_DELAY` | No | `24h` | Cleanup fixed delay and initial delay. |

PostgreSQL row locking, idempotency keys, retries, dead letters, outbox activation, and
notifications replace SQS/EventBridge/OpenSearch. Do not configure AWS queue/search
variables.

## JFrog webhook intake

| Variable | Required | Secret | Default | Meaning |
|---|---:|---:|---|---|
| `REGISTRY_WEBHOOK_ENABLED` | API | No | `false` | Enables `POST /internal/webhooks/jfrog`. |
| `REGISTRY_WEBHOOK_SECRET` | API | Yes | empty | HMAC shared secret. Generate at least 32 random bytes. |
| `REGISTRY_WEBHOOK_ORIGIN` | API | No | empty | Allowed origin set; comma-separated when multiple values are required. |
| `REGISTRY_WEBHOOK_SUBSCRIPTION_ID` | API | No | empty | Exact configured subscription ID. |
| `REGISTRY_WEBHOOK_ALLOWED_REPOSITORIES` | No | No | three governed locals | Exact repository allowlist. |
| `REGISTRY_WEBHOOK_ALLOWED_PATH_PREFIXES` | No | No | curated prefixes | Comma-separated path allowlist. Keep as narrow as the package layout permits. |
| `REGISTRY_WEBHOOK_MAXIMUM_PAYLOAD_BYTES` | No | No | `262144` | Raw request size limit. |

The currently implemented custom header/payload contract must be proven against the target
JFrog instance as described in the readiness audit.

## Ingestion, reconciliation, and download statistics

| Variable | Required | Default | Meaning |
|---|---:|---|---|
| `REGISTRY_INGESTION_ENABLED` | Worker/seeder | `false` | Enables artifact reconciliation/ingestion. Must be false in API. |
| `REGISTRY_GOVERNED_REPOSITORIES` | No | three governed locals | Repositories the worker is allowed to inspect. |
| `REGISTRY_CATALOG_REPOSITORY` | No | `iac-catalog-release-local` | Catalog manifest repository. |
| `REGISTRY_INGESTION_MANIFEST_SUFFIX` | No | `catalog-manifest.json` | Suffix identifying a catalog manifest. |
| `REGISTRY_MAXIMUM_MANIFEST_BYTES` | No | `8388608` | Manifest size ceiling. |
| `REGISTRY_MAXIMUM_ARTIFACT_BYTES` | No | `536870912` | Artifact size ceiling. |
| `REGISTRY_MAXIMUM_DOCUMENT_BYTES` | No | `16777216` | Normalized documentation size ceiling. |
| `REGISTRY_DOCUMENT_INGESTION_CONCURRENCY` | No | `8` | Bounded concurrency, accepted range 1–64. |
| `REGISTRY_RECONCILE_ON_STARTUP` | No | `false` | Full reconciliation during startup. Use intentionally; seeder uses true. |
| `REGISTRY_INCREMENTAL_RECONCILIATION_DELAY` | No | `15m` | Incremental schedule. |
| `REGISTRY_INCREMENTAL_RECONCILIATION_INITIAL_DELAY` | No | `15m` | First incremental run delay. |
| `REGISTRY_FULL_RECONCILIATION_CRON` | No | `0 17 2 * * *` | Nightly full reconciliation in UTC. |
| `REGISTRY_DOWNLOAD_STATISTICS_DELAY` | No | `15m` | Artifactory download-count refresh delay. |
| `REGISTRY_DOWNLOAD_STATISTICS_INITIAL_DELAY` | No | `30s` | Initial statistics refresh delay. |
| `REGISTRY_ANALYTICS_CLEANUP_CRON` | No | `0 11 3 * * *` | Authenticated traffic-event retention cleanup in UTC. |

## Seeder job

| Variable | Required | Secret | Default | Meaning |
|---|---:|---:|---|---|
| `REGISTRY_SEED_ENABLED` | Seeder | No | `false` | Runs the curated seeder. |
| `REGISTRY_SEED_MANIFEST_RESOURCE` | No | No | `classpath:seed/curated-catalog-v1.json` | Checked-in seed manifest location. |
| `REGISTRY_SEED_PROVIDER_REPOSITORY` | No | No | `iac-provider-release-local` | Governed provider target. |
| `REGISTRY_SEED_MODULE_REPOSITORY` | No | No | `iac-module-release-local` | Governed module target. |
| `REGISTRY_SEED_CATALOG_REPOSITORY` | No | No | `iac-catalog-release-local` | Catalog manifest target. |
| `REGISTRY_SEED_CONNECTION_TIMEOUT` | No | No | `15s` | Upstream download connection timeout. |
| `REGISTRY_SEED_REQUEST_TIMEOUT` | No | No | `5m` | Upstream request timeout. |
| `REGISTRY_SEED_MAXIMUM_DOWNLOAD_BYTES` | No | No | `629145600` | Per-download ceiling. |
| `REGISTRY_SEED_CACHE_DIRECTORY` | No | No | OS temp directory | Persistent cache path; Compose mounts `/var/cache/registry-seed`. |
| `REGISTRY_SEED_UPLOAD_ATTEMPTS` | No | No | `4` | Upload attempt count. |
| `REGISTRY_SEED_UPLOAD_RETRY_BACKOFF` | No | No | `15s` | Upload retry delay. |
| `REGISTRY_SEED_PACKAGES` | No | No | all | Comma-separated package filter for partial/recovery runs. |
| `REGISTRY_SEED_VERSIONS` | No | No | all pinned | Comma-separated version filter. |
| `REGISTRY_SEED_ALLOW_UNPINNED_DIGESTS` | No | No | `false` | Unsafe emergency escape hatch. Keep false for acceptance/production. |
| `REGISTRY_SEED_EXIT_AFTER_COMPLETION` | Seeder | No | `false` | Stops the job after completion; set true. |

The seeder publishes the catalog-ready manifest last. A partially uploaded package must not
become visible.

## UI runtime settings

The UI container renders these public values into `/config/runtime.json` at startup:

| Variable | Required | Secret | Example |
|---|---:|---:|---|
| `REGISTRY_API_BASE_URL` | Yes | No | `/api/v1` |
| `REGISTRY_JFROG_HOSTNAME` | Yes | No | `your-company.jfrog.io` |
| `REGISTRY_ENVIRONMENT` | Yes | No | `production` |
| `REGISTRY_SUPPORT_URL` | Yes | No | support portal URL |

`VITE_BUILD_SHA` is a Docker build argument embedded for release identification. No OIDC,
Graph, database, JFrog token, or webhook secret belongs in the browser environment.

## Docker Compose-only settings

| Variable | Default | Meaning |
|---|---|---|
| `REGISTRY_POSTGRES_PORT` | `5432` | Host PostgreSQL port. |
| `REGISTRY_API_PORT` | `8080` | Host API port. |
| `REGISTRY_UI_PORT` | `3000` | Host UI port. |
| `REGISTRY_WEB_DB_PASSWORD` | local fallback | Password created for `registry_web`. |
| `REGISTRY_INDEXER_DB_PASSWORD` | local fallback | Password created for `registry_indexer`. |
| `REGISTRY_SEED_PACKAGES` | empty | Optional local seed subset. |
| `REGISTRY_SEED_VERSIONS` | empty | Optional local version subset. |

Compose additionally requires these ignored files:

- `.env.artifactory`
- `.env.eventing`
- `.env.identity-test`

They contain secrets and must never be printed or committed.

## GitHub Actions configuration

Exact names can change when the AWS scaffold is corrected; keep this table synchronized
with the selected workflow layout.

| Name | Type | Purpose |
|---|---|---|
| `AWS_IMAGE_ROLE_ARN` | GitHub environment variable | OIDC role allowed to push release images. |
| `AWS_TERRAFORM_PLAN_ROLE_ARN` | GitHub environment variable | Read/plan role. |
| `AWS_TERRAFORM_APPLY_ROLE_ARN` | Protected environment variable | Apply/deploy role. |
| `AWS_REGION` | Variable/input | Target region. |
| `TF_STATE_BUCKET` | Variable | Terraform state bucket. |
| `TF_LOCK_TABLE` or selected lock configuration | Variable | Terraform locking. |
| `JFROG_URL` | Environment variable/secret context | JFrog base URL for JFrog Terraform/provider jobs. |
| `JFROG_ACCESS_TOKEN` | Secret | Short-lived/scoped JFrog token. |
| `SONAR_TOKEN` | Optional secret | Only if SonarQube is deliberately enabled; it is not required to build the service. |

Use GitHub OIDC for AWS rather than long-lived AWS access keys. Environment protection and
branch protection remain authoritative even if `act` is used for local workflow debugging.
The JFrog Terraform root currently uses an Azure Storage backend; either supply its
approved `backend.hcl` values or migrate that root to the protected S3 backend as a
reviewed change.

## Unsupported/obsolete names

Do not configure these unless a future adapter explicitly implements them:

- `REGISTRY_DATABASE_HOST`
- `REGISTRY_DATABASE_PORT`
- `REGISTRY_DATABASE_NAME`
- `REGISTRY_DATABASE_USER`
- `REGISTRY_DATABASE_SECRET_ARN`
- `REGISTRY_JFROG_BASE_URL`
- `REGISTRY_JFROG_TOKEN_SECRET_ARN`
- `REGISTRY_AUTHORIZATION_CONFIG_SECRET_ARN`
- `REGISTRY_DATA_API_URL`
- `REGISTRY_ENTERPRISE_API_URL`
- `REGISTRY_FEATURE_PROVIDERS`
- `REGISTRY_FEATURE_MODULES`
- `REGISTRY_FEATURE_SECURITY`
- `REGISTRY_FEATURE_AUDIT`

Their presence in the current AWS Terraform scaffold is a known blocker, not an
application contract.
