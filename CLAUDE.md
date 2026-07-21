# Claude Implementation and Deployment Instructions

This repository is designed to be handed to an implementation agent. Execute the work in this order. Do not collapse the UI and API into one production repository and do not place secrets in source control.

## Non-negotiable architecture

- JFrog Artifactory is the package data plane.
- AWS is the catalog control plane.
- The OpenTofu Registry UI frontend is the UI foundation.
- ECS Fargate runs UI, API, indexer, reconciler, and migration workloads.
- Terraform deploys AWS infrastructure.
- Production uses two repositories: `private-registry-ui` and `private-registry-api`.
- The API repository owns Terraform because it owns the runtime and shared data services.
- Browser traffic never calls JFrog directly.
- Terraform/OpenTofu clients download packages directly from JFrog.

## Execution sequence

### 1. Read the blueprint

Read, in order:

1. `DEPLOYMENT.md`
2. `docs/00-implementation-plan.md`
3. `docs/01-architecture.md`
4. `docs/02-repository-model.md`
5. `docs/03-aws-services.md`
6. `docs/16-required-inputs.md`
7. `docs/20-execution-checklist.md`
8. `docs/18-status.md`

Do not invent organization-specific values. Record unanswered decisions in `docs/16-required-inputs.md` or an issue.

### 2. Export the two production repositories

```bash
./scripts/export-repositories.sh ../exported
```

Create:

```text
private-registry-ui
private-registry-api
```

Apply branch protection, CODEOWNERS, required checks, environments, and least-privilege GitHub OIDC roles before production deployments.

### 3. Create the controlled UI fork

In `private-registry-ui`:

1. Review `UPSTREAM.md` and `.upstream/OPEN_TOFU_COMMIT`.
2. Create an upstream-intake branch.
3. Run `scripts/import-upstream.sh`.
4. Commit imported upstream frontend source.
5. Run `scripts/apply-overlays.sh`.
6. Replace upstream branding and public-registry-only behavior.
7. Preserve required notices and complete legal/open-source review.
8. Implement enterprise components under `app/src/enterprise/`.
9. Point standard registry calls to the compatibility API.
10. Point governance panels to `/api/v1/enterprise`.
11. Run unit, visual, accessibility, security, and API contract tests.

Never build production from an unpinned upstream branch.

### 4. Complete API adapters

In `private-registry-api`, implement interfaces in this order:

1. Configuration and structured logging.
2. Aurora repository and migrations.
3. S3 document repository.
4. OpenSearch query/index adapter.
5. JFrog read-only client.
6. Event validation and idempotency.
7. Ingestion pipeline.
8. Reconciliation pipeline.
9. Compatibility API routes.
10. Enterprise governance routes.
11. ALB OIDC assertion verification and authorization.

The starter server intentionally returns fixture/empty data until these adapters are complete.

### 5. Bootstrap Terraform state

```bash
cd infrastructure/terraform/bootstrap
terraform init
terraform plan -out bootstrap.tfplan
terraform apply bootstrap.tfplan
```

Record the state bucket and KMS ARN in each environment backend configuration. Apply access controls before storing production state.

### 6. Deploy the AWS foundation in pass one

Set:

```hcl
deploy_application_services = false
```

Apply the target environment. Pass one creates networking, endpoints, KMS, ECR, S3, Aurora, RDS Proxy, OpenSearch, queues, event bus, IAM, monitoring, backup, and load-balancer foundations without requiring application images.

### 7. Configure JFrog

Create candidate, release, remote, virtual, and catalog repositories as defined in `docs/07-jfrog.md`. Confirm deployed Artifactory capabilities before automation. Internal providers use origin-registry mode and signed releases. Public providers keep their original source address and use network-mirror behavior.

### 8. Build and push images

Push immutable SHA-tagged images:

```text
registry-web
catalog-api
catalog-indexer
catalog-reconciler
catalog-migrations
```

Promote the same image digest between environments.

### 9. Initialize data services

1. Run the migration ECS task with the database admin secret.
2. Create the application database user and grants.
3. Install OpenSearch templates and aliases.
4. Map API/indexer/reconciler IAM principals to least-privilege OpenSearch roles.
5. Seed approved package fixtures for end-to-end validation.

### 10. Deploy ECS services in pass two

Set:

```hcl
deploy_application_services = true
```

Supply immutable image tags and apply Terraform. Application workflows publish images only; Terraform registers and deploys every ECS task-definition revision.

### 11. Connect package release events

Release pipelines publish versioned events to the EventBridge custom bus after successful promotion in JFrog. Do not expose a public webhook when cross-account `events:PutEvents` is available.

### 12. Production gates

Do not declare production readiness until every item in `docs/14-testing-acceptance.md` passes, including package installation tests, signature validation, authorization, accessibility, penetration, load, queue-failure, backup restoration, and regional recovery exercises.

## Change rules

- Keep upstream UI changes isolated from enterprise extensions.
- Update `.upstream/OPEN_TOFU_COMMIT`, `UPSTREAM.md`, and `PATCHES.md` together.
- Maintain backward compatibility for UI compatibility routes.
- Add a migration for every database schema change.
- Version every manifest and event contract.
- Never edit released package content.
- Never use real internal endpoints, account IDs, or secrets in examples.
- Prefer managed AWS services and existing enterprise shared services over new bespoke infrastructure.
