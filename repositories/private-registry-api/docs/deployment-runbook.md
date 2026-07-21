# Deployment runbook

## Prerequisites

- approved AWS accounts and primary/DR Regions;
- internal DNS zone and ACM certificate or private CA process;
- OIDC client/issuer/scopes and group claims;
- GitHub OIDC provider and protected environments;
- JFrog hostname, PrivateLink endpoint service, repository keys, and read-only secret;
- approved KMS key administrators and backup account;
- Terraform state bucket and lockfile access.

## Pass 1: foundation

Set `deploy_application_services = false` and apply the target live environment. Verify VPC endpoints, ALB, ECR, buckets, queue/DLQ, EventBridge bus, Aurora/RDS Proxy, OpenSearch, ECS cluster, IAM roles, logs, alarms, and backup plans.

## Data initialization

1. Build/push `catalog-migrations`.
2. Run the migration task in a private subnet.
3. Run OpenSearch initialization from an authorized task.
4. Store JFrog and OIDC values in Secrets Manager.
5. Validate PrivateLink/DNS and JFrog artifact reads.

## Pass 2: services

Build SHA-tagged API/indexer/reconciler/UI images. Set `deploy_application_services = true`, supply immutable image tags, and apply. Wait for target health, queue metrics, and dependency readiness.

## Verification

- authenticated portal login;
- health and readiness;
- package fixture event reaches search;
- module/provider documentation renders;
- copied source blocks pass `terraform init` and `tofu init` against JFrog;
- unauthorized users cannot access restricted metadata;
- DLQ/redrive and reconciliation dry run succeed;
- rollback to previous task definition succeeds.

## Rollback

Application rollback uses the previous task definition/image digest. Database migrations must be forward-compatible; destructive rollback requires an approved restore procedure. Infrastructure rollback must not delete release packages, audit evidence, state, KMS keys, or protected backups.
