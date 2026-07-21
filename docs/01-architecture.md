# Layered Solution Architecture

## Context

The public registry experience combines two different concerns: machine package distribution and human documentation/search. This design separates them so each can scale and fail independently.

## Runtime request flows

### Browser flow

```text
Corporate browser
  -> enterprise network
  -> Route 53 private record
  -> internal ALB HTTPS listener
  -> ALB OIDC authentication
  -> / routes to registry-web
  -> /api, /registry/docs, /top routes to catalog-api
  -> Aurora / OpenSearch / S3
```

The API validates the ALB-signed identity assertion and applies authorization. UI hiding is never treated as authorization.

### CLI package flow

```text
Terraform/OpenTofu CLI
  -> JFrog hostname
  -> JFrog virtual repository
  -> approved release repository or approved remote cache
```

The catalog is not in this path.

### Publication flow

```text
Source tag
  -> build/test/docs/SBOM/sign
  -> JFrog candidate
  -> scans/integration/approval
  -> JFrog release promotion
  -> EventBridge PackagePromoted
  -> SQS
  -> indexer
  -> Aurora + S3 + OpenSearch
```

## Service boundaries

### `registry-web`

Stateless Nginx container serving the customized OpenTofu frontend. No service credentials. Runtime configuration contains non-secret API paths, environment, JFrog hostname, and feature flags.

### `catalog-api`

Stateless read-oriented HTTP service. Reads metadata from Aurora, queries OpenSearch, reads Markdown from S3, validates identity/authorization, and creates correct usage snippets from catalog metadata.

### `catalog-indexer`

Long-running SQS consumer. Owns release validation, normalization, indexing, idempotency, quarantine, and ingestion audit records.

### `catalog-reconciler`

Scheduled Fargate task. Enumerates JFrog and compares all derived stores. Defaults to report-only; repair mode requires explicit authorization and recorded change control.

### `catalog-migrations`

One-shot ECS task. Runs schema migrations using a privileged database secret unavailable to normal services.

## Data authority

| Data | Authority | Derived copies |
|---|---|---|
| Package bytes/version existence | JFrog | Aurora catalog reference |
| Documentation release bundle | JFrog catalog repo | S3 normalized pages, OpenSearch text |
| Package owner/lifecycle/approval | Aurora | OpenSearch filters |
| Search records | None; derived | OpenSearch only |
| Runtime images | ECR | ECS task definitions |
| Audit evidence | S3 Object Lock / central logging | Aurora summaries |

## Availability model

- JFrog is independently highly available according to the selected JFrog deployment/SLA.
- UI/API/indexer are spread across three Availability Zones.
- Aurora uses one writer and multiple readers; RDS Proxy reduces connection churn.
- OpenSearch uses Multi-AZ with Standby in production.
- SQS buffers release bursts and transient outages.
- S3 and Aurora are replicated/backed up to a DR Region.
- The catalog uses active/passive cross-Region recovery to avoid multi-writer complexity.

## Trust boundaries

1. Browser to ALB: authenticated HTTPS.
2. ALB to ECS: private subnets and security groups.
3. ECS to AWS services: IAM task roles and VPC endpoints.
4. Indexer/reconciler to JFrog: read-only service identity over PrivateLink or approved private network.
5. CI to AWS: GitHub OIDC and short-lived role sessions.
6. CI to JFrog: short-lived scoped token.
7. Provider signing: isolated signing role and protected key custody.
