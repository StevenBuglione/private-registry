# Claude Instructions — API and Terraform Repository

## Before coding or deploying

1. Read the root blueprint documentation copied with this repository handoff.
2. Read `docs/project-structure.md`, `docs/architecture.md`, `docs/compatibility-contract.md`, `docs/adapter-implementation.md`, and `docs/deployment-runbook.md`.
3. Replace no placeholders until the corresponding values are approved.
4. Keep JFrog as the package data plane. Do not add package-download proxy endpoints.
5. Keep all browser authorization in the API, not the UI.

## Implementation order

1. Synchronize the pinned upstream OpenAPI contract and implement explicit compatibility DTOs/tests.
2. Extend the PostgreSQL catalog implementation behind `CatalogService` for Aurora IAM authentication.
3. Add production S3 document reads while retaining local Flyway fixtures only in the `local` profile.
4. Add OpenSearch indexing/query behind a focused Spring service using the configured Java client.
5. Add a read-only JFrog client behind a focused Spring service.
6. Implement SQS event handling with idempotency and partial-failure recovery.
7. Implement reconciliation and repair modes.
8. Implement ALB OIDC assertion verification and role mapping.
9. Keep the `local` profile and permit-all security setting out of production task definitions.
10. Add integration, authorization, failure-injection, load, backup, and DR tests.

## Deployment order

1. Bootstrap Terraform state.
2. Plan/apply foundation with application services disabled.
3. Configure JFrog repositories and private connectivity.
4. Build and push immutable images.
5. Run migrations.
6. Install OpenSearch templates/aliases.
7. Enable application services.
8. Run synthetic module/provider installation and portal tests.
9. Connect governed release pipelines to EventBridge.
10. Exercise queue redrive, restore, and regional failover before production.

Never store secrets, private keys, account IDs, internal DNS names, or real endpoints in source control. Use GitHub OIDC, IAM roles, Secrets Manager, and environment-protected workflows.
