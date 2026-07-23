# Registry deployment and configuration handoff

**Last reviewed:** 2026-07-23
**Purpose:** configure the Registry against a new JFrog Artifactory instance, prove it
locally, and prepare a safe AWS deployment.

This guide is written so another engineer or coding agent can take over without relying on
conversation history. It describes the implemented PostgreSQL-only system. Read the
following in order:

1. This guide.
2. [`31-environment-variable-reference.md`](31-environment-variable-reference.md).
3. [`32-deployment-readiness-audit.md`](32-deployment-readiness-audit.md).
4. The API's
   [`database-design.md`](../repositories/private-registry-api/docs/database-design.md)
   and
   [`deployment-runbook.md`](../repositories/private-registry-api/docs/deployment-runbook.md).

The current local deployment is supported. The current AWS application-service Terraform
has mandatory blockers; do not deploy it unchanged.

## 1. System boundaries

### Source-of-truth model

- **JFrog Artifactory** owns package bytes, existence, file information, checksums,
  properties, immutability, and promotion state.
- **PostgreSQL** owns normalized catalog metadata, documentation, APM access assignments,
  full-text search, the durable work queue, retries, dead letters, outbox activation,
  settings, audit records, authenticated traffic analytics, and live-update
  notifications.
- **Microsoft Entra ID and Graph** own identity and group membership.
- **The UI** has no secrets. It receives only session-safe identity data and authorized
  catalog responses from the API.

No OpenSearch, SQS, EventBridge, application S3, or LocalStack is required.

### Deployable processes

| Process | Exposure | Database principal | Important behavior |
|---|---|---|---|
| UI | ALB/public-to-corporate users | none | Static React/Nginx; runtime JSON only |
| API | ALB | `registry_web` | OIDC verification, authorization, reads, settings, webhook intake |
| Indexer | private only | `registry_indexer` | Queue worker, JFrog reads, reconciliation, statistics |
| Migration | one-shot/private | schema owner | Flyway only, then exits |
| Seeder | one-shot/private | `registry_indexer` | Creates governed local repos, mirrors curated content, then exits |
| PostgreSQL | private | role-specific | Only persistent application state |

The API and indexer use the same Java image with different environment and commands. Do
not combine them into one task.

## 2. Handoff input worksheet

The receiving engineer must fill this out before changing Terraform or deployment
configuration. Store real secrets only in approved secret systems.

### Organization

- Application owner:
- Operations/on-call owner:
- Security owner:
- Cost center/tags:
- Environments (`dev`, `prod`, `dr`, other):
- Registry DNS names:
- Support URL:
- Data retention requirements:
- Recovery point/time objectives:

### AWS

- AWS organization/account IDs:
- Primary and DR regions:
- VPC CIDRs:
- Private application/database subnet CIDRs:
- Corporate ingress CIDRs:
- Transit Gateway/private routing:
- NAT strategy or VPC endpoints:
- Route 53 zone IDs:
- ACM certificate ARNs:
- KMS key ownership:
- Secrets Manager naming convention:
- GitHub OIDC provider and allowed repositories/environments:
- Aurora/RDS PostgreSQL sizing and maximum connections:
- Backup vault/retention/cross-region requirements:

### Entra

- Tenant ID:
- Production application/client ID:
- Application owner:
- Client-secret/certificate rotation owner:
- Admin group object ID:
- APM ID to group-object-ID mappings:
- Conditional Access/MFA expectations:
- Local acceptance identities permitted?:

### JFrog

- JPD hostname:
- Cloud, self-hosted, or hybrid:
- Network path from AWS and CI:
- Remote cache repository owners:
- Governed local repository owners:
- Runtime token subject/expiry:
- Seeder/bootstrap token subject/expiry:
- Publisher token model:
- Xray policy:
- Repository immutability/retention:
- Webhook connectivity and signing support:

### GitHub/repository layout

- Monorepo or exported API/UI repositories:
- GitHub organization/repositories:
- Protected branches:
- Protected environments and reviewers:
- AWS plan/apply/image/migration role ARNs:
- Dependency, secret, CodeQL, and container scan policies:

## 3. Prerequisites

For a local Windows handoff:

- Git.
- Docker Desktop with Compose v2 and enough disk for provider/module archives.
- Terraform matching the repository constraints.
- Azure CLI for Entra identity-test authentication.
- JFrog CLI v2.
- PowerShell 7.

Java 25 and Node/Corepack are optional for a Docker-only run, but are required for direct
quality/build work. Use the committed Gradle wrapper and locked pnpm dependencies.

Confirm tools without exposing credentials:

```powershell
git --version
docker version
docker compose version
terraform version
az version
jf --version
```

Clone, create a scoped branch, and check that ignored secret files are ignored:

```powershell
git clone <repository-url> private-registry
Set-Location private-registry
git switch -c codex/destination-deployment
git check-ignore repositories/private-registry-api/.env.artifactory
git check-ignore repositories/private-registry-api/.env.eventing
git check-ignore repositories/private-registry-api/.env.identity-test
```

Do not copy the existing `.terraform` directories, Terraform state, `.env.*`, build
outputs, or local Docker volumes to the next environment.

## 4. Configure a different JFrog Artifactory

### 4.1 Create a JFrog CLI configuration

Create a scoped access token in the destination JPD. Configure it interactively when
possible so the token does not enter shell history:

```powershell
jf c add registry
jf c show registry
```

The server URL should be the JPD base URL, for example
`https://your-company.jfrog.io`. Test identity and reachability:

```powershell
jf rt ping --server-id=registry
```

Export the selected configuration into the ignored Compose file:

```powershell
Set-Location repositories/private-registry-api
.\scripts\export-jfrog-env.ps1 -ServerId registry
```

The script writes `.env.artifactory` with `REGISTRY_JFROG_HOSTNAME` and
`JFROG_ACCESS_TOKEN`. It deliberately does not print the token.

### 4.2 Create public pull-through caches and APM permissions

The JFrog Terraform root creates:

- `iac-modules-public-remote`
- `iac-providers-public-remote`
- APM-aligned JFrog/Entra groups and permission targets configured by the assignment file.

This root currently declares an `azurerm` backend and therefore needs an existing Azure
Storage state container plus Azure authentication. That differs from the AWS S3 backend
used by the application infrastructure. Before the first destination apply, deliberately
choose whether to retain the protected Azure backend or migrate this root to the protected
AWS state backend; do not create an unreviewed local state as a shortcut. The root also
uses the `azuread` provider, so authenticate the Azure CLI to the destination tenant.

From `infrastructure/terraform/jfrog`:

```powershell
az login --tenant <tenant-guid>
Copy-Item backend.hcl.example backend.hcl
Copy-Item apm-packages.tfvars.example.json apm-packages.auto.tfvars.json
$env:JFROG_URL = 'https://your-company.jfrog.io/artifactory'
$env:JFROG_ACCESS_TOKEN = '<obtain-securely>'
$env:REGISTRY_JFROG_HOSTNAME = 'your-company.jfrog.io'
terraform init -backend-config=backend.hcl
terraform fmt -check -recursive
terraform validate
terraform plan -out=jfrog.tfplan
terraform apply jfrog.tfplan
.\scripts\set-apm-properties.ps1
```

Edit the two copied files before planning. Keep them, the plan, and state out of Git. The
remote repositories have `prevent_destroy`; an intentional removal requires a reviewed
code change.

### 4.3 Create governed local repositories and seed packages

The Java seeder uses the official JFrog Artifactory Java Client to idempotently create:

- `iac-provider-release-local`
- `iac-module-release-local`
- `iac-catalog-release-local`

It downloads pinned upstream content, verifies provider checksums and manifest digests,
uploads bytes/properties, extracts documentation and Terraform schemas, and publishes the
catalog-ready manifest last.

The seeder token needs temporary create/deploy/annotate/read/AQL privileges on those
repositories. Replace it after bootstrap with read-only API/indexer tokens. Run:

```powershell
docker compose --profile seed build seeder
docker compose --profile seed run --rm seeder
```

For a recovery subset:

```powershell
$env:REGISTRY_SEED_PACKAGES = 'provider/hashicorp/null'
$env:REGISTRY_SEED_VERSIONS = '3.2.4'
docker compose --profile seed run --rm seeder
Remove-Item Env:\REGISTRY_SEED_PACKAGES
Remove-Item Env:\REGISTRY_SEED_VERSIONS
```

Keep `REGISTRY_SEED_ALLOW_UNPINNED_DIGESTS=false`. Changing a released version in place
must be treated as an integrity conflict, not an update.

### 4.4 Update the static mirror acceptance fixture

Terraform module/provider source addresses are static syntax. Before testing a different
JPD, update the hostname and source strings in
[`infrastructure/terraform/tests/registry-mirror`](../repositories/private-registry-api/infrastructure/terraform/tests/registry-mirror).
Do not assume an environment variable can rewrite a `source` address.

## 5. Configure local event intake

Generate a local HMAC secret after `.env.artifactory` exists:

```powershell
.\scripts\bootstrap-local-eventing-env.ps1
```

The script derives the allowed origin from the configured JFrog hostname. To override:

```powershell
.\scripts\bootstrap-local-eventing-env.ps1 `
  -Origin 'your-company.jfrog.io' `
  -SubscriptionId 'registry-local-acceptance'
```

It will not overwrite an existing file. Delete or move `.env.eventing` only when
intentionally rotating the local secret, then regenerate it and restart the API.

The built-in signed-request simulator is:

```powershell
.\scripts\emit-example-webhook.ps1
```

This proves the application's HMAC, allowlist, and queue acceptance path. It does **not**
prove that a destination JPD sends the exact implemented header and payload contract.
Before production, capture a redacted real webhook request, add it as a test fixture, and
complete blocker 14 in the readiness audit.

If the JPD cannot reach the private AWS endpoint, use a GitHub runner with network access
and an administrator-created sync credential:

```http
POST /api/v1/sync/artifacts
Authorization: Bearer <one-time-visible-sync-secret>
Idempotency-Key: <publisher-generated-stable-id>
Content-Type: application/json

{
  "kind": "module",
  "repository": "iac-module-release-local",
  "path": "namespace/name/provider/version/catalog-manifest.json",
  "action": "DEPLOYED"
}
```

Create, scope, rotate, and revoke sync credentials in the admin settings UI/API. Store the
secret in the publisher repository's GitHub secret store. It is shown only at creation.

## 6. Configure real Entra identity locally

The local path uses Spring OAuth2 against the same real Entra tenant and delegated Graph
API. It does not mock identity or memberships.

Authenticate the Azure CLI:

```powershell
az login --tenant <tenant-guid>
az account show
```

From `infrastructure/terraform/identity-test`, create an ignored `terraform.tfvars`:

```hcl
entra_tenant_id         = "<tenant-guid>"
application_display_name = "Registry Local Acceptance"
local_redirect_uri      = "http://localhost:3000/login/oauth2/code/entra"
local_logout_uri        = "http://localhost:3000/signed-out"
test_user_prefix        = "registry-e2e-unique"
```

Then:

```powershell
terraform init
terraform fmt -check
terraform validate
terraform plan -out=identity.tfplan
terraform apply identity.tfplan
.\scripts\export-compose-env.ps1
```

The root creates only Entra directory objects: a single-tenant application/service
principal, delegated `User.Read`, two APM groups, an admin group, and unlicensed test
users. It does not create an Azure subscription or paid infrastructure.

The export writes `.env.identity-test`. Do not display it: it contains the OIDC client
secret and test-user passwords. Confirm only that required keys exist:

```powershell
$required = 'ENTRA_TENANT_ID','ENTRA_CLIENT_ID','ENTRA_CLIENT_SECRET',
  'REGISTRY_ENTRA_APM_GROUPS','REGISTRY_ENTRA_ADMIN_GROUP_ID'
$names = Get-Content .env.identity-test |
  Where-Object { $_ -match '^[A-Z0-9_]+=' } |
  ForEach-Object { ($_ -split '=', 2)[0] }
$required | ForEach-Object { if ($_ -notin $names) { throw "Missing $_" } }
```

For an existing organization-managed Entra application, configure:

- Web callback `http://localhost:3000/login/oauth2/code/entra`.
- Logout URL `http://localhost:3000/signed-out`.
- Delegated Microsoft Graph `User.Read`.
- Scopes `openid profile email offline_access User.Read`.
- A confidential client secret with an owner and rotation date.
- APM and registry-admin group object IDs.

## 7. Run and prove the complete local stack

From `repositories/private-registry-api`:

```powershell
docker compose config --quiet
docker compose up --build --detach --wait
docker compose --profile seed run --rm seeder
docker compose ps
```

Expected services:

- `postgres` healthy.
- `migrate` and `role-bootstrap` exited successfully.
- `api`, `indexer`, and `ui` healthy/running.
- `seeder` exits successfully when invoked.

### Health and API checks

```powershell
Invoke-RestMethod http://localhost:8080/health/live
Invoke-RestMethod http://localhost:8080/health/ready
Start-Process http://localhost:3000/swagger-ui.html
Start-Process http://localhost:3000
```

`/health/ready` must depend on PostgreSQL, not JFrog. The separated non-web indexer does
not currently expose `/health/worker`; use its container health, logs, queue/reconciliation
state, and the authenticated Artifactory/admin diagnostics locally. A production
PostgreSQL heartbeat contract is a mandatory audit item.

After interactive Entra sign-in, prove:

- APM-A sees only APM-A packages.
- APM-B sees only APM-B packages.
- A user with no mapped group gets an authenticated empty catalog.
- The administrator sees the intended catalog and `/admin`.
- An unauthorized direct detail/docs/governance route returns 404 and leaks no metadata.
- Search, filters, pagination, versions, resources, data sources, inputs, outputs,
  downloads, code copy, breadcrumbs, theme, and SSE refresh work.
- Admin homepage announcement and featured module/provider selections persist.
- Admin sync credential creation/revocation and audit records work.
- Traffic analytics record authenticated access without storing tokens.

Inspect safe operational state:

```powershell
docker compose logs --since=10m api indexer
docker compose exec postgres psql -U registry -d registry -c `
  "select status, count(*) from catalog_event_queue group by status order by status;"
docker compose exec postgres psql -U registry -d registry -c `
  "select count(*) as dead_letters from catalog_event_dead_letters;"
```

Run the signed event simulator and verify a new accepted/processed row:

```powershell
.\scripts\emit-example-webhook.ps1
docker compose exec postgres psql -U registry -d registry -c `
  "select event_id, status, attempts, created_at from catalog_event_queue order by created_at desc limit 5;"
```

Preserve data with:

```powershell
docker compose down
```

Erase local database/cache only when explicitly intended:

```powershell
docker compose down --volumes
```

## 8. Code-quality and security gates

Run the root orchestration and component gates before a deployment handoff:

```powershell
Set-Location <repository-root>
.\scripts\validate-blueprint.ps1

Set-Location repositories/private-registry-api
.\scripts\quality.ps1 pr
.\gradlew.bat bootJar

Set-Location ..\private-registry-ui\web
corepack pnpm install --frozen-lockfile
corepack pnpm quality
```

Run GitHub Actions locally with `act` where a job supports it, but treat hosted Actions,
protected environments, CodeQL, dependency review, and cloud OIDC as final evidence. Do
not make SonarQube or a missing third-party API key a build prerequisite unless the
organization has deliberately enabled that integration.

Before promotion:

- Secret scan the Git history and working tree.
- Generate SBOMs.
- Scan Java/UI dependencies and release images.
- Record source SHA and immutable image digests.
- Confirm no `latest` image tag is deployed.

## 9. Production Entra and ALB configuration

Create or select a single-tenant production Entra application. Its ALB callback is:

```text
https://<registry-host>/oauth2/idpresponse
```

Configure the ALB OIDC action with:

- Entra v2 issuer.
- Tenant authorization and token endpoints.
- A user-info endpoint compatible with the requested scopes.
- `openid profile email offline_access User.Read`.
- Client ID and protected client secret.
- `on_unauthenticated_request=authenticate` for interactive routes.

ALB forwards `x-amzn-oidc-accesstoken`, `x-amzn-oidc-identity`, and
`x-amzn-oidc-data`. The Java API validates the signed data against the exact ALB ARN,
client ID, issuer, expiry, and matching identity before creating a principal. It uses the
delegated access token server-side for Graph group checks; the browser never receives it.

An internal ALB must still reach Entra authorization/token/user-info endpoints. Provide
NAT/egress and DNS as required. Keep client secret rotation in the deployment runbook.

## 10. AWS target architecture

Provision or integrate:

- Terraform state bucket/locking and KMS.
- VPC, corporate routing, private application/database subnets, and controlled egress.
- Route 53 and ACM.
- Internal ALB with TLS, OIDC, WAF, UI/API target groups, and correct path rules.
- ECR for the UI and one immutable Java image, or deliberately mirrored Java repositories.
- ECS cluster/services for UI, API, and indexer, including a private worker-heartbeat
  alarm after that audit blocker is implemented.
- ECS one-shot task definitions for migration and seeder.
- Aurora PostgreSQL or RDS PostgreSQL with backups, TLS, alarms, and tested restore.
- RDS Proxy only after choosing a compatible tested authentication design.
- Secrets Manager/KMS for database roles, JFrog tokens, webhook HMAC, and ALB OIDC secret.
- CloudWatch logs/metrics/alarms, SNS/on-call integration, and audit retention.
- GitHub OIDC roles for plan, apply, image publish, and migration.

Do not enable application services until every mandatory issue in the readiness audit is
closed.

## 11. Corrected two-pass AWS sequence

These are the required phases after the Terraform blockers are fixed.

### Phase A: state and foundation

1. Configure the `bootstrap` root for remote state and locking.
2. Configure `global` resources such as ECR/replication.
3. Copy the target `live/<environment>` backend/example files into ignored real files.
4. Fill network, DNS, certificate, Entra, JFrog, database, GitHub OIDC, backup, and tag
   values.
5. Set `deploy_application_services=false`.
6. Run `terraform fmt -check -recursive`, `validate`, and a reviewed saved plan.
7. Apply only foundation, database, secrets metadata, ECS cluster, and one-shot task
   definitions.

### Phase B: secrets and images

1. Generate distinct database role passwords and the webhook HMAC.
2. Put secret values into Secrets Manager encrypted with the intended KMS keys.
3. Build UI and Java images from one reviewed source SHA.
4. Generate SBOMs and scan the images.
5. Push and record immutable digests.
6. Configure each task with the matching file from `deploy/environment`.

ECS injects secrets at task start. A rotated secret requires new tasks/forced deployment;
an existing process does not automatically receive the new value.

### Phase C: database and catalog

1. Run the migration task as the schema owner and wait for exit code zero.
2. Verify Flyway history and the three runtime roles/grants.
3. Run the seeder with the temporary privileged JFrog token.
4. Verify the three local repositories, expected package counts, properties, and digests.
5. Replace/revoke the bootstrap token and configure runtime read-only tokens.
6. Start the private indexer and wait for reconciliation/queue health.

### Phase D: services and identity

1. Set immutable image digests/tags and enable application services.
2. Apply the reviewed plan.
3. Verify target health, API readiness, indexer logs, and database connections.
4. Sign in through real ALB OIDC as every access class.
5. Verify Graph fail-closed behavior and unauthorized-route 404 behavior.
6. Enable/configure webhook or runner sync only after a reachable route exists.
7. Run browser, latency, load, backup/restore, and operational acceptance.

### Phase E: promotion

1. Attach evidence to the change record.
2. Enable production deletion protection/final snapshots.
3. Confirm alarms/on-call routing and runbooks.
4. Promote traffic gradually.
5. Monitor API latency/errors, DB connections/locks, queue lag, dead letters, JFrog
   failures, Graph failures, ALB authentication failures, and ECS restarts.

## 12. GitHub Actions handoff

There are two possible layouts:

- **Monorepo:** workflows must live under the root `.github/workflows` and use repository
  path filters/working directories.
- **Exported API/UI repositories:** the workflow templates inside each nested repository
  become active after export.

Do not configure both without a reason. Align the GitHub OIDC trust policy's
`sub` conditions with the actual owner, repository, branch, and protected environment.

Minimum protected environments:

- `<env>-plan`
- `<env>-apply`
- `<env>-migrations`
- `<env>-images`

Keep apply and migration approval separate. The current workflow examples need the
production Terraform corrections in the audit before use.

## 13. Settings and operations after deployment

### Admin settings

Registry administrators can use `/admin` to:

- Update the home-page notification.
- Search/select featured providers and modules.
- Inspect operational dashboard/worker state.
- Review authenticated traffic analytics.
- Review audit events.
- Create, scope, rotate, and revoke module/provider sync API credentials.
- Open the Swagger UI.

All admin mutations must produce audit records. Treat an unexpected absence of audit data
as an operational failure.

### Swagger/OpenAPI

- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`

Expose them through the API target group and normal authentication. Do not make the
interactive documentation public merely to simplify routing.

### Configuration changes

| Change | Mechanism | Restart/redeploy |
|---|---|---|
| Homepage/featured packages | Admin UI/API | No |
| APM-to-Entra group mapping | API environment/secret-backed config | API restart |
| Admin group | API environment | API restart |
| JFrog runtime token | Secret store | Force new API/indexer tasks |
| Webhook HMAC | JFrog + secret store together | Force new API tasks |
| UI support URL/environment/JFrog label | UI runtime environment | Force new UI tasks |
| Reconciliation/queue timing | Indexer environment | Force new indexer tasks |
| Database credential | DB + secret store | Coordinated force deployment |
| ALB OIDC client secret | ALB configuration/secret workflow | Listener update; test login |

### Backups and upgrades

- Take/verify automated PostgreSQL backups and perform scheduled restore drills.
- Export/record JFrog repository configuration and rely on JFrog's supported backup/DR
  model for artifact bytes.
- Run Flyway once before new application tasks.
- Roll back application image digests only when schema compatibility is proven; prefer a
  forward-fix for migrations.
- Keep reconciliation enabled during recovery.

## 14. Final acceptance checklist

### Configuration

- [ ] Worksheet completed and reviewed.
- [ ] No original JFrog hostname remains in runtime/test configuration.
- [ ] Three singular governed repository names are consistent.
- [ ] Process environment files match the reference.
- [ ] Secret values are injected, not secret ARNs.
- [ ] No secret appears in Git, plan artifacts, task environment, image layers, or logs.

### Local

- [ ] Compose config resolves.
- [ ] Migration/bootstrap jobs exit zero.
- [ ] PostgreSQL, API, indexer, and UI are healthy.
- [ ] Seeder creates/verifies repositories and catalog.
- [ ] Real Entra/Graph access classes are distinct.
- [ ] Signed local event is processed idempotently.
- [ ] Browser console/network and service logs are clean.
- [ ] API/UI quality and security gates pass.

### AWS

- [ ] Every readiness-audit blocker is closed with tests.
- [ ] Terraform plan is reviewed and policy checks pass.
- [ ] API/indexer/migration use correct database principals.
- [ ] ALB OIDC and API signature verification pass.
- [ ] Required path routes reach the API; webhook is not trapped by interactive OIDC.
- [ ] Real JFrog event or runner-sync contract passes.
- [ ] Backups/restores and alarms are proven.
- [ ] Immutable image digests, SBOMs, scans, and source SHA are recorded.

## 15. Official external references

- [AWS ALB OIDC authentication](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/listener-authenticate-users.html)
- [Amazon ECS Secrets Manager environment injection](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/secrets-envvar-secrets-manager.html)
- [Microsoft Graph checkMemberGroups](https://learn.microsoft.com/en-us/graph/api/directoryobject-checkmembergroups?view=graph-rest-1.0)
- [JFrog predefined webhooks](https://docs.jfrog.com/integrations/docs/predefined-webhooks)
- [JFrog webhook event types](https://docs.jfrog.com/integrations/docs/webhook-event-types)
- [JFrog Artifactory Java Client](https://github.com/jfrog/artifactory-client-java)
