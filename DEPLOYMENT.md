# Deployment

The authoritative deployment handoff is:

1. [Complete configuration and deployment guide](docs/30-deployment-configuration-handoff.md)
2. [Environment-variable reference](docs/31-environment-variable-reference.md)
3. [Deployment-readiness audit and AWS blockers](docs/32-deployment-readiness-audit.md)

## Supported now

The complete local Docker Compose topology is supported. From
`repositories/private-registry-api`, configure the three ignored environment files, start
Compose, and seed the destination JFrog instance:

```powershell
.\scripts\export-jfrog-env.ps1 -ServerId registry
.\scripts\bootstrap-local-eventing-env.ps1
terraform -chdir=infrastructure/terraform/identity-test init
terraform -chdir=infrastructure/terraform/identity-test apply
.\infrastructure\terraform\identity-test\scripts\export-compose-env.ps1
docker compose up --build --detach --wait
docker compose --profile seed run --rm seeder
```

Never print or commit `.env.artifactory`, `.env.eventing`, `.env.identity-test`, Terraform
state, or saved plans.

## Production warning

The checked-in AWS foundation is a scaffold. Its current ECS environment names,
database-role/authentication model, secret injection, routing, image publication, and
process topology do not yet match the implemented application. Do **not** enable
`deploy_application_services` until every mandatory item in the readiness audit is fixed
and verified.

The production target has only PostgreSQL as application state. Do not deploy the retired
OpenSearch/SQS/EventBridge/S3 application topology described in historical planning
documents.
