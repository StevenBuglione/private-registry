# Initial Deployment and Operations Runbook

## Preflight

1. Complete `docs/16-required-inputs.md`.
2. Confirm access to AWS accounts and JFrog.
3. Confirm DNS, certificate, OIDC, and network approvals.
4. Confirm provider signing key/public key workflow.
5. Export/create UI and API repositories.
6. Configure protected branches/environments and GitHub OIDC.

## State bootstrap

```bash
cd private-registry-api/infrastructure/terraform/bootstrap
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform plan -out bootstrap.tfplan
terraform apply bootstrap.tfplan
```

Create backend files for dev/prod/DR and restrict state access.

## Foundation apply

```bash
cd ../live/dev
cp backend.hcl.example backend.hcl
cp terraform.tfvars.example terraform.tfvars
terraform init -backend-config=backend.hcl
terraform plan -var='deploy_application_services=false' -out foundation.tfplan
terraform apply foundation.tfplan
```

Repeat for production only after non-production acceptance.

## JFrog setup

- create repositories and permissions;
- configure public key for internal provider registry;
- create read-only catalog identity and scoped release identities;
- publish sample module/provider candidate;
- run scans/tests;
- promote to release;
- verify CLI installation independently of the catalog.

## Build images

UI:

```bash
docker build -t registry-web:${GIT_SHA} .
```

API command:

```bash
docker build -t catalog-api:${GIT_SHA} .
```

Indexer, reconciler, and dedicated migration images remain roadmap items and must not be published as placeholder API processes.

Push to ECR and record immutable tags/digests.

## Database/search initialization

- run migration task;
- verify application role/grants;
- install OpenSearch templates and aliases;
- map IAM principals;
- run API readiness tests from an approved network location.

## Service apply

Set immutable image tags and:

```bash
terraform plan -var='deploy_application_services=true' -out services.tfplan
terraform apply services.tfplan
```

Verify ALB health, OIDC login, UI/API routing, logs, alarms, and autoscaling targets.

## Event connection

Publish a signed promoted test event. Confirm:

- EventBridge accepted event;
- SQS received message;
- indexer completed;
- Aurora/S3/OpenSearch records agree;
- package appears in UI;
- duplicate event is harmless.

## Routine operations

Daily:

- inspect alarms/DLQ/reconciliation drift;
- verify backup and replication health;
- review security findings.

Release:

- monitor promotion-to-search latency;
- retain event/correlation ID;
- rollback catalog visibility by lifecycle change, not artifact mutation.

Incident:

- freeze promotions when catalog consistency is uncertain;
- keep JFrog package downloads available;
- use report-only reconciliation first;
- require approval for repair/redrive;
- preserve evidence and post-incident actions.
