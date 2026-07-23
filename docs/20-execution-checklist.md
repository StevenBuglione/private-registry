# End-to-End Execution Checklist

> Historical blueprint checklist. Runtime topology and gates involving separate search, object storage, and brokers are superseded by ADR 0007.

This checklist is the implementation order for a deployment agent. Each stage has a hard exit gate. Do not advance by bypassing a failed gate.

## Stage 0 — Create the two production repositories

1. Run `./scripts/export-repositories.sh ../exported` from this blueprint.
2. Create `private-registry-ui` and `private-registry-api` in the target GitHub organization.
3. Push the corresponding exported directory to each repository.
4. Configure protected `main`, CODEOWNERS, required status checks, secret scanning, Dependabot, and environment approvals.
5. Create GitHub environments: `dev`, `prod`, `dr`, plus `*-plan`, `*-apply`, and `*-migrations` where used by workflows.

**Exit gate:** both repositories build from clean clones and no static AWS access key exists in GitHub.

## Stage 1 — Resolve required inputs

Complete every field in `docs/16-required-inputs.md`. At minimum, record:

- AWS accounts and Regions;
- VPC CIDRs, corporate routes, private DNS, and egress model;
- ACM certificate and OIDC endpoints/client;
- JFrog hostname, PrivateLink service, repository keys, and service identities;
- GitHub owner/repository names and OIDC roles;
- KMS administrators and external secret-key ARNs;
- production SLO/RTO/RPO and DR ownership.

**Exit gate:** environment tfvars contain no `REPLACE`, `.invalid`, fake account ID, or unresolved null that is required by the selected topology.

## Stage 2 — UI upstream intake and fit check

In `private-registry-ui`:

```bash
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

Complete the integration hooks in `PATCHES.md`, remove upstream branding that is not approved, preserve notices, and document every source-level modification.

**Exit gate:** the pinned module/provider fixture pages render against the API compatibility fixture and the built Nginx image serves `/healthz` and `/config/runtime.json`.

## Stage 3 — Bootstrap Terraform state

In `private-registry-api/infrastructure/terraform/bootstrap`:

```bash
terraform init
terraform plan -out=bootstrap.tfplan
terraform apply bootstrap.tfplan
```

Populate each `backend.hcl` from its example and initialize each root with `-backend-config`.

**Exit gate:** state is in the encrypted/versioned S3 bucket, native lockfile behavior works, and only approved plan/apply/break-glass roles can access it.

## Stage 4 — Deploy regional foundations

Set `deploy_application_services = false` in dev. Apply the regional root.

Expected foundation resources:

- VPC, subnets, route tables, flow logs, endpoints, and optional JFrog PrivateLink;
- KMS keys;
- internal ALB, WAF, OIDC listener, target groups, logging bucket;
- ECS cluster and ECR repositories;
- Aurora, RDS Proxy, OpenSearch, and S3 buckets;
- EventBridge, SQS/DLQ, Scheduler group, IAM, CloudWatch, SNS, and AWS Backup;
- GitHub OIDC roles.

**Exit gate:** Terraform has no drift immediately after apply; ECR, database, search, S3, queue, and JFrog connectivity checks pass from the VPC.

## Stage 5 — Configure JFrog

Create and secure the topology in `docs/07-jfrog.md`. Test one module and one signed provider manually before automating promotion.

**Exit gate:** Terraform installs the test module/provider from approved JFrog endpoints, candidate repositories are not readable by normal users, and released versions cannot be overwritten.

## Stage 6 — Complete production adapters

Implement the boundaries in `private-registry-api/docs/adapter-implementation.md`:

- Aurora repository and IAM-token/RDS Proxy connection management;
- S3 document/quarantine/evidence storage;
- SigV4 OpenSearch query and bulk indexing;
- read-only JFrog client;
- ALB JWT verification and server-side authorization;
- SQS indexer and reconciliation engine;
- migration runner and OpenSearch bootstrap.

**Exit gate:** the Spring Boot API starts successfully without the `local` profile, and local fixture migrations plus permit-all security remain excluded from production.

## Stage 7 — Build immutable images

Run the UI and API image workflows. They publish SHA-tagged images only; Terraform remains the ECS deployment authority.

Record for each image:

- source commit;
- ECR repository;
- image tag;
- image digest;
- SBOM/provenance reference;
- scan/signature result.

**Exit gate:** all five image digests are available and pass policy.

## Stage 8 — Initialize data stores

1. Run the migration task.
2. Create/verify `registry_app` and `registry_indexer` IAM-authenticated database roles.
3. Install OpenSearch templates and aliases.
4. Configure fine-grained OpenSearch IAM role mappings.
5. Load a known fixture package through the same ingestion path used by releases.

**Exit gate:** the API readiness check validates Aurora, S3, and OpenSearch; migration history and index aliases are correct.

## Stage 9 — Deploy application services

Set `deploy_application_services = true` and supply immutable tags. Apply Terraform.

Verify:

- UI/API target health;
- ECS deployment circuit breaker;
- task spread across three Availability Zones;
- API authentication and authorization;
- indexer queue consumption;
- scheduled reconciler task invocation;
- alarms and dashboards.

**Exit gate:** all services are healthy and a rollback to the previous task definition has been tested.

## Stage 10 — Connect governed release pipelines

Package release automation must publish EventBridge events only after JFrog artifact and documentation promotion completes. Validate event schemas before `PutEvents`.

**Exit gate:** one promoted module and one promoted signed provider become searchable in the portal within the visibility SLO, and duplicate event delivery does not create duplicate state.

## Stage 11 — Production and DR

Repeat the regional foundation/application process for production and DR. Apply `global/` only after both regional outputs exist.

Test:

- Aurora Global Database recovery;
- S3/ECR replication;
- OpenSearch warm-standby activation or rebuild;
- Route 53/enterprise DNS failover;
- JFrog continuity;
- full reconciliation after regional activation.

**Exit gate:** every item in `docs/14-testing-acceptance.md` and `private-registry-api/docs/production-readiness.md` has retained evidence.
