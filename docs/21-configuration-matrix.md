# Configuration Matrix

> Historical blueprint matrix. ADR 0007 and the checked-in Compose/Terraform configuration are authoritative for the PostgreSQL-only stateful runtime.

## GitHub repository configuration

| Repository | Setting | Required value or source |
|---|---|---|
| UI | `AWS_IMAGE_ROLE_ARN` | Terraform `github_role_arns.image` or separately approved image role |
| UI | protected environments | `dev`, `prod`, `dr`; production and DR require approvers |
| API | `AWS_IMAGE_ROLE_ARN` | least-privilege ECR push role |
| API | `AWS_TERRAFORM_PLAN_ROLE_ARN` | read/plan role; no mutation permissions |
| API | `AWS_TERRAFORM_APPLY_ROLE_ARN` | protected infrastructure deployment role |
| API | `AWS_MIGRATION_ROLE_ARN` | role allowed to run only the migration task definition |
| API | `AWS_REGION` | environment-specific Region |
| API | `TF_BACKEND_CONFIG` | protected multiline secret containing the environment `backend.hcl` values |
| API | `TF_VARS` | protected multiline secret containing the non-OIDC environment variable file |
| API | `OIDC_CLIENT_SECRET` | protected secret exposed to Terraform as `TF_VAR_oidc_client_secret` |
| both | GitHub OIDC `sub` | restrict by owner, repository, branch/environment |

No workflow stores long-lived AWS access keys.

## Required protected CI values

| Value | Storage | Notes |
|---|---|---|
| OIDC client secret | protected Terraform environment or split listener stack | Terraform state is sensitive; apply strict state controls |
| backend configuration | protected file/artifact or workflow-generated file | bucket, key, Region, KMS key, role ARN |
| Terraform sensitive variables | environment secrets | pass as `TF_VAR_*`; never commit real values |
| JFrog publication credentials | package release platform | separate from the catalog read-only credential |
| provider signing key | approved signing service | do not place in either application repository |

## AWS Secrets Manager inputs

| Secret | Consumer | Required contents |
|---|---|---|
| JFrog catalog reader | indexer/reconciler | read-only token or supported short-lived credential material |
| authorization mapping | API | immutable group IDs to application roles/visibility scopes |
| RDS Proxy backend secret | RDS Proxy/migration task | regional application credential; replicate separately to DR |
| OIDC client secret | ALB listener configuration | client credential supplied through protected Terraform execution |

When external secrets use external CMKs, set the matching `*_secret_kms_key_arn` Terraform variables.

## Terraform environment matrix

| Concern | dev | prod | dr |
|---|---:|---:|---:|
| Regions | primary | primary | secondary |
| Availability Zones | 3 | 3 | 3 |
| NAT | one allowed for cost | per-AZ or central egress | per-AZ or central egress |
| Aurora instances | at least 2 | at least 3 | at least 3 warm readers |
| OpenSearch data nodes | 3 | 6+ divisible by 3 | warm domain or documented rebuild |
| UI/API desired tasks | 1/1 | 3/3 | 0 until activation or approved warm count |
| indexer tasks | 1 | 2+ | 0 until activation |
| deletion protection | optional | on | on |
| Object Lock | optional | policy decision, normally on for evidence | same as production |
| Route 53 record | regional test record | owned by `global/` | owned by `global/` |
| Spring `local` profile | allowed locally only | prohibited | prohibited |

## Runtime environment variables

### UI

`REGISTRY_API_BASE_URL` (set to `/api/v1`), `REGISTRY_JFROG_HOSTNAME`,
`REGISTRY_ENVIRONMENT`, and `REGISTRY_SUPPORT_URL`. The container creates
`/config/runtime.json` at startup.

### API/workers

- service: environment, log level, HTTP address;
- database: RDS Proxy host/port/name/user and secret ARN metadata;
- S3: documentation, quarantine, audit, and reconciliation buckets;
- OpenSearch endpoint;
- JFrog base URL and reader-secret ARN;
- SQS queue URL and EventBridge bus name;
- expected ALB signer ARN and OIDC issuer;
- authorization configuration secret ARN.

## Values that must never be guessed

Account IDs, Regions, CIDRs, DNS zones, certificates, IdP endpoints/claims, JFrog mode/repository encoding, security approval taxonomy, KMS key ownership, retention, signing custody, and DR traffic-management behavior.
