# Deployment Guide

This guide turns the blueprint into two production repositories and deploys the AWS catalog control plane. Read `docs/18-status.md` first: the repository contains production-oriented architecture and scaffolding, but the API adapters and final UI branding/compatibility work must be completed and tested before production.

## 1. Validate and export

```bash
git clone https://github.com/StevenBuglione/private-registry.git
cd private-registry
./scripts/validate-blueprint.sh
./scripts/export-repositories.sh ../private-registry-export
```

Create and protect two repositories from the exported directories:

```text
private-registry-ui
private-registry-api
```

The optional `scripts/bootstrap-github-repositories.sh` prints or executes the `gh` commands for this step.

## 2. Complete the UI intake

```bash
cd ../private-registry-export/private-registry-ui
./scripts/import-upstream.sh
./scripts/apply-overlays.sh
./scripts/verify-upstream.sh
python3 scripts/check-runtime-template.py
cd app
npm ci
npm run lint --if-present
npm run test --if-present -- --run
npm run build
```

The approved upstream SHA is pinned. The patch step fails closed if the reviewed integration seams move. Complete branding, design-system, compatibility, visual-regression, accessibility, CSP, and legal-review tasks before releasing an image.

## 3. Freeze the compatibility contract

In the API repository, synchronize the exact upstream API contract from the same pin:

```bash
cd ../private-registry-api
./scripts/sync-opentofu-contract.sh
```

Commit the generated contract after review. Build explicit domain-to-compatibility DTO adapters and contract tests. Enterprise fields stay under `/api/v1/enterprise`.

## 4. Supply approved environment inputs

Resolve every item in `docs/16-required-inputs.md` from the blueprint. In the exported API repository, create protected backend and tfvars inputs for `dev`, `prod`, and `dr`. Never commit real credentials or sensitive tfvars.

Required categories include:

- AWS account IDs and primary/DR Regions;
- VPC CIDRs, private routing, DNS zones, and certificate ARNs;
- OIDC endpoints/client configuration and immutable group mappings;
- JFrog hostname, PrivateLink service, repository keys, and read-only credential secret;
- KMS key ownership and external secret-key ARNs;
- GitHub OIDC owner/repository/environment constraints;
- SLO, RTO, RPO, retention, backup, and Object Lock decisions.

## 5. Bootstrap Terraform state

```bash
cd infrastructure/terraform/bootstrap
terraform init
terraform fmt -check
terraform validate
terraform plan -out=bootstrap.tfplan
terraform apply bootstrap.tfplan
```

Create environment `backend.hcl` files from the examples and initialize each root with `terraform init -backend-config=backend.hcl`.

## 6. Deploy AWS foundation — pass one

Set:

```hcl
deploy_application_services = false
```

Apply `live/dev` first. This creates the VPC/subnets/endpoints, internal ALB/OIDC/WAF, ECS cluster, ECR, KMS, S3, Aurora/RDS Proxy, OpenSearch, EventBridge/SQS/DLQ, Scheduler, IAM, CloudWatch/SNS, AWS Backup, and GitHub OIDC roles without requiring application images.

```bash
cd infrastructure/terraform/live/dev
terraform init -backend-config=backend.hcl
terraform plan -var-file=terraform.tfvars -out=foundation.tfplan
terraform apply foundation.tfplan
```

## 7. Configure JFrog

Create the candidate, release, remote, virtual, and catalog repositories from `docs/07-jfrog.md`. Confirm the deployed JFrog version and license support the selected module/provider modes. Configure least-privilege service identities, Xray/policy gates, immutable promotion, provider GPG public keys, and private connectivity.

Prove one module and one signed internal provider manually with both Terraform and OpenTofu before automating release events.

## 8. Complete and test production adapters

Implement the interfaces documented in `private-registry-api/docs/adapter-implementation.md`:

- Aurora/RDS Proxy persistence and migrations;
- S3 document/quarantine/evidence stores;
- SigV4 OpenSearch query/indexing;
- read-only JFrog validation client;
- ALB JWT verification and server-side authorization;
- SQS indexer with idempotency/visibility extension/quarantine;
- dry-run and authorized repair reconciliation;
- readiness and OpenTelemetry instrumentation.

Production must not activate the Spring `local` profile or set `REGISTRY_SECURITY_PERMIT_ALL=true`.

## 9. Build immutable images

Build and push SHA-tagged images through GitHub OIDC:

```text
private-registry-<environment>-ui
private-registry-<environment>-api
```

Add worker images only after those workloads have real implementations.

Record source SHA, image digest, SBOM, provenance, scan result, and signature/attestation. Image workflows publish only; Terraform remains the ECS deployment authority.

## 10. Initialize data services

1. Run the migration ECS task.
2. Verify least-privilege database roles and IAM authentication.
3. Install OpenSearch templates and aliases.
4. Configure fine-grained role mappings.
5. Load a fixture through the same event/ingestion path used by releases.

## 11. Deploy services — pass two

Set `deploy_application_services = true`, supply immutable image tags, plan, review, and apply. Validate target health, spread across three Availability Zones, circuit-breaker rollback, readiness, queue processing, scheduled reconciliation, and alarms.

## 12. Connect release pipelines

A release pipeline may emit `PackagePromoted`, `PackageDeprecated`, or `PackageRevoked` only after the corresponding JFrog operation is complete. Validate events against the versioned JSON Schemas before `events:PutEvents`.

## 13. Production and DR

Repeat the regional deployment in production and the DR Region. Apply `infrastructure/terraform/global` after regional outputs are known. Exercise Aurora Global Database recovery, S3/ECR replication, OpenSearch activation/rebuild, DNS failover, JFrog continuity, and full reconciliation.

Do not declare production readiness until `docs/14-testing-acceptance.md` and the exported API repository's production checklist have retained evidence.
