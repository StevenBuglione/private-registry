# Deployment readiness audit

**Audit date:** 2026-07-23

**Audited revision:** See the Git history for this file.
**Audience:** the engineer or coding agent preparing a new JFrog and AWS deployment

This is the authoritative assessment of what can be deployed today. It is intentionally
stricter than the older architecture documents. A checked-in Terraform resource is not
evidence that the application can use it.

## Executive decision

| Area | Status | Decision |
|---|---|---|
| Local Docker Compose | Ready for configuration and acceptance | Supported deployment path |
| Java API and PostgreSQL schema | Ready for local acceptance | Run migrations before services |
| First-party UI | Ready for local acceptance | Configure only public runtime values |
| JFrog Java seeder | Ready for a governed test JPD | Use a short-lived bootstrap token |
| JFrog webhook simulation | Ready locally | Production payload still needs real-JPD contract testing |
| AWS foundation resources | Scaffolded | Review and adapt before apply |
| AWS application services | **Blocked** | Do not deploy the current ECS definitions unchanged |
| Production Entra through ALB | **Blocked pending integration** | Correct routing, scopes, secrets, and acceptance tests |

The supported local topology is:

```text
browser -> UI/Nginx -> API (registry_web)
                         |
                         v
                    PostgreSQL
                         ^
                         |
JFrog -> webhook/API -> queue tables -> indexer (registry_indexer) -> JFrog
```

PostgreSQL owns catalog storage, full-text search, event queueing, dead letters, audit
records, UI settings, analytics, and notifications. Do not add OpenSearch, SQS,
EventBridge, application S3, or LocalStack back into the target deployment.

## Mandatory AWS corrections

Each item below is a release blocker. Close it in code, test it, and record evidence before
enabling `deploy_application_services`.

### 1. ECS uses environment names the application does not read

[`ecs.tf`](../repositories/private-registry-api/infrastructure/terraform/modules/platform/ecs.tf)
sets `REGISTRY_DATABASE_*`, `REGISTRY_JFROG_BASE_URL`,
`REGISTRY_JFROG_TOKEN_SECRET_ARN`, and
`REGISTRY_AUTHORIZATION_CONFIG_SECRET_ARN`. Spring reads `SPRING_DATASOURCE_*`,
`REGISTRY_ARTIFACTORY_*`, and `REGISTRY_ENTRA_*`.

**Required correction:** implement the contracts in
[`deploy/environment`](../repositories/private-registry-api/deploy/environment/README.md).
Inject secret **values** with the ECS `secrets` field. Do not pass a Secrets Manager ARN to
an application property expecting a password or token.

### 2. The process and database-role model is wrong

Compose correctly separates:

- API: `registry_web`, webhook intake enabled, ingestion disabled.
- Indexer: `registry_indexer`, ingestion enabled, no public HTTP listener.
- Migration: schema owner, Flyway enabled, exits.
- Seeder: `registry_indexer` plus a temporary privileged JFrog token, exits.

The AWS module instead runs a combined API/worker using `registry_app`. Migration
[`V120`](../repositories/private-registry-api/src/main/resources/db/migration/V120__finalize_normalization_and_runtime_roles.sql)
defines `registry_app` as read-only/reporting. It cannot perform the required API or worker
writes.

**Required correction:** create distinct API and indexer task definitions/services and
use the least-privileged roles above. Keep the indexer off the ALB.

### 3. RDS Proxy requires IAM authentication the Java datasource does not implement

[`database.tf`](../repositories/private-registry-api/infrastructure/terraform/modules/platform/database.tf)
sets `iam_auth = "REQUIRED"`. The application has no IAM JDBC authentication-token
provider and does not retrieve database credentials from Secrets Manager.

**Required correction:** choose and test one complete design:

1. Use password authentication, inject independently rotatable `registry_web`,
   `registry_indexer`, and migration credentials, and configure the proxy consistently; or
2. Add and test an IAM-token-aware datasource, refresh behavior, TLS verification, and
   failure recovery.

Do not combine IAM-required proxy authentication with a static Spring password.

### 4. Secret injection IAM is attached to the wrong role

ECS resolves task-definition `secrets` with the task **execution role**. The current
permissions are primarily on application task roles.

**Required correction:** grant the execution role narrowly scoped `secretsmanager:GetSecretValue`
and `kms:Decrypt` for only the injected secrets. Retain application runtime permissions on
the task role. Verify a cold task start and a forced deployment after secret rotation.

### 5. ALB verification is missing the expected client ID

`REGISTRY_ALLOWED_OIDC_CLIENT_ID` is absent from the ECS environment. The API deliberately
rejects ALB identity headers without an exact signer, client ID, issuer, expiry, and
identity match.

**Required correction:** inject all values from `api.env.example`, then run the ALB
signature and negative-validation tests against the deployed endpoint.

### 6. OIDC scopes are insufficient for Graph authorization

The Terraform defaults/examples do not consistently request `offline_access User.Read`.
The application calls Microsoft Graph `/me/checkMemberGroups` with the delegated access
token. Microsoft limits `checkMemberGroups` input to 20 group IDs per call; the application
batches configured groups accordingly.

**Required correction:** use `openid profile email offline_access User.Read`, grant/admin
consent according to organization policy, and prove APM-A, APM-B, no-entitlement, and
administrator behavior with real identities.

### 7. ALB routes do not expose required API endpoints

`local.api_paths` currently contains only `/api/*`, `/registry/docs/*`, and `/top/*`.
Production Nginx serves the SPA and does not proxy API requests.

**Required correction:** explicitly route at least:

- `/api/*`
- `/registry/docs/*`
- `/top/*`
- `/swagger-ui*`
- `/v3/api-docs*`
- `/internal/webhooks/jfrog`

The webhook route must not use interactive OIDC. Protect it with its HMAC and strict
origin/subscription/repository/path allowlists. Decide separately whether metrics are
internal-only or routed through a management listener.

### 8. The internal ALB may be unreachable from JFrog

The scaffold creates an internal ALB. JFrog Cloud normally cannot call a private endpoint
without customer-provided connectivity.

**Required correction:** choose one:

- a dedicated, narrowly exposed webhook ingress with WAF/rate limits and HMAC validation;
- private connectivity supported by the selected JFrog topology; or
- the authenticated `POST /api/v1/sync/artifacts` endpoint called by a GitHub runner that
  can reach the registry.

Reconciliation must remain enabled regardless; events are hints, not the source of truth.

### 9. The migration task cannot exist in the documented first pass

The migration task definition is conditional on `deploy_application_services=true`, while
the intended sequence requires migrations before long-running services.

**Required correction:** decouple the migration task definition from the service flag.
Create the foundation and migration task, run and verify Flyway, then enable services.

### 10. Image publication does not satisfy Terraform

The API build workflow pushes only the API repository. Terraform expects a separate
`migrations` image and tag even though the same Java image can run API, worker, migration,
and seeder modes.

**Required correction:** either publish the same digest to all required ECR repositories
or simplify Terraform to use one immutable Java image digest for all Java tasks. Build and
publish the UI separately. Record image digests, SBOMs, scans, and source SHA.

### 11. There is no AWS indexer service

The production module contains UI, API, and migration task definitions only.

**Required correction:** add an indexer service with no ALB target group, a long-running
non-web command, worker health monitoring, graceful shutdown, and the
`registry_indexer` database role.

### 12. Repository names are inconsistent

The application and seeder use singular governed local repositories:

- `iac-provider-release-local`
- `iac-module-release-local`
- `iac-catalog-release-local`

Some platform variables use plural `iac-providers-release-local` and
`iac-modules-release-local`. These variables are not wired into the Java application.

**Required correction:** standardize on the three singular names or make all names
explicit inputs shared by Terraform and the process environments.

### 13. JFrog ownership is split and must be intentional

The JFrog Terraform root provisions public remote caches and APM permissions. The Java
seeder creates the three local release repositories through the official JFrog
Artifactory Java Client.

**Required correction:** document the ownership boundary, use a temporary bootstrap token
for create/upload/property operations, and a separate read-only runtime token for the
indexer/API. Make repository immutability and retention policy explicit.

### 14. The production webhook payload contract is not proven

The current endpoint expects `X-JFrog-Signature`, `X-JFrog-Origin`, and
`X-JFrog-Subscription-Id`. JFrog's documented predefined webhook body includes fields such
as `domain`, `event_type`, `subscription_key`, and `jpd_origin`. Artifact-property
`event_type` values can be `added` or `deleted`.

**Required correction:** capture a real request from the target JPD without exposing its
secret, add contract fixtures, and adapt the parser/header contract or a dedicated JFrog
custom-webhook template. A successful call from `emit-example-webhook.ps1` proves local
HMAC handling, not JFrog compatibility.

### 15. Workflow placement depends on repository layout

Root workflows run monorepo quality checks. Deployment workflows under
`repositories/private-registry-api/.github` and
`repositories/private-registry-ui/.github` become active only after those templates are
exported as separate GitHub repositories.

**Required correction:** decide monorepo versus exported repositories before configuring
GitHub environments, paths, OIDC subjects, branch protection, and workflow variables.

### 16. The registry-mirror smoke test is tenant-specific

[`tests/registry-mirror`](../repositories/private-registry-api/infrastructure/terraform/tests/registry-mirror)
contains a static, deliberately non-routable Terraform source address and provider
hostname placeholder. Terraform source addresses cannot be supplied by a normal runtime
environment variable.

**Required correction:** update and commit the test fixtures for the destination hostname
and repository layout before using them as deployment evidence.

### 17. Historical documents describe retired infrastructure

Several older planning documents mention OpenSearch, SQS, EventBridge, LocalStack, and
application S3. They are retained as design history.

**Required correction:** use
[`30-deployment-configuration-handoff.md`](30-deployment-configuration-handoff.md),
[`31-environment-variable-reference.md`](31-environment-variable-reference.md), and this
audit as the deployment authority.

### 18. The JFrog Terraform state backend is Azure-specific

The AWS infrastructure roots use the bootstrapped S3 state backend, but the JFrog root
declares `backend "azurerm"` and its example requires an Azure Storage account/container.
That is a separate paid Azure resource unless an approved shared state account already
exists.

**Required correction/decision:** either document and use an approved existing Azure state
container, or migrate the JFrog root to the protected S3 backend before its first
destination apply. Perform any existing-state migration explicitly and back up state.
Never silently fall back to local state.

### 19. Split-process worker health is not implemented

`WorkerDependencyHealthService` exists only when
`registry.ingestion.enabled=true`. The supported API process sets ingestion false, while
the indexer sets Spring's web application type to `none`. Therefore `/health/worker` and
the API dashboard's in-process worker probe are absent in the correct topology. Enabling
ingestion in the public API merely to expose health would violate process separation.

**Required correction:** have each indexer instance publish a leased heartbeat and
dependency summary to PostgreSQL, expose only an authorized aggregate through the API,
and alarm on stale/missing heartbeats, queue lag, and dead letters. Combine that with ECS
task health and log alarms. Until implemented, use process health, queue/reconciliation
state, and logs; do not claim `/health/worker` as production evidence.

## Entra environment separation

[`identity-test`](../repositories/private-registry-api/infrastructure/terraform/identity-test)
creates local acceptance identities and writes sensitive values to ignored Terraform
state and `.env.identity-test`. It is not a production identity root.

| Context | Callback |
|---|---|
| Local Spring OAuth2 client | `http://localhost:3000/login/oauth2/code/entra` |
| Production ALB OIDC | `https://registry.example.com/oauth2/idpresponse` |

The local test client secret has a finite expiry in Terraform. Production credentials must
use the organization's rotation and ownership model. Never print, commit, or attach local
Terraform state to a handoff.

## Readiness behavior

- `/health/live`: process liveness.
- `/health/ready`: PostgreSQL-backed catalog readiness only.
- `/health/worker`: available only in a web process with ingestion enabled; it is not a
  valid endpoint in the supported split-process topology until blocker 19 is resolved.
- `/api/v1/artifactory/status`: authenticated Artifactory diagnostic.

Artifactory must not be an API readiness dependency. Catalog reads should remain available
during a JFrog outage; ingestion should recover through PostgreSQL retry/reconciliation.

## Required evidence before production approval

- Terraform plans reviewed for global, primary, and DR roots.
- Database restore drill and migration rollback/forward-fix procedure.
- API, indexer, migration, and seeder run as distinct principals.
- Real ALB OIDC positive and negative verification.
- Real Graph authorization for each access class.
- Unauthorized list, count, search, detail, docs, governance, and SSE data never delivered.
- JFrog repository/property/digest checks from the destination JPD.
- Real signed JFrog webhook or runner-trigger contract test.
- Duplicate/out-of-order event, retry, dead-letter, and reconciliation recovery tests.
- UI browser matrix and accessibility tests.
- Clean API/UI quality, security, dependency, and container scans.
- PostgreSQL queue lag, dead-letter, failed-login, API latency, and worker dependency alarms.
- No secrets in Git, image layers, task-definition plaintext environment, logs, or evidence.

## Primary external contracts

- [AWS ALB authenticate users](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/listener-authenticate-users.html)
- [ECS secret injection](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/secrets-envvar-secrets-manager.html)
- [Microsoft Graph checkMemberGroups](https://learn.microsoft.com/en-us/graph/api/directoryobject-checkmembergroups?view=graph-rest-1.0)
- [JFrog predefined webhooks](https://docs.jfrog.com/integrations/docs/predefined-webhooks)
- [JFrog webhook event payloads](https://docs.jfrog.com/integrations/docs/webhook-event-types)
- [JFrog Artifactory Java Client](https://github.com/jfrog/artifactory-client-java)
