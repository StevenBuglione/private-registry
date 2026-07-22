# AWS deployment sequence

## Bootstrap

Apply `infrastructure/terraform/bootstrap` once per approved state boundary. Protect the bucket/key from routine destroy and commit reviewed lockfiles after network-enabled initialization.

## Regional pass one

Apply `live/<environment>` with `deploy_application_services=false`. This creates all managed services, networking, ECR, roles, and load-balancer foundations while image tags can remain `not-deployed`.

## Build and initialize

- publish SHA-tagged images to the environment-specific ECR repositories;
- run the migration task;
- install OpenSearch templates/aliases and role mappings;
- configure JFrog private connectivity and reader secret;
- verify Aurora/S3/OpenSearch/JFrog connectivity from private tasks.

## Regional pass two

Set `deploy_application_services=true`, pass immutable image tags, review the Terraform plan, and apply. Terraform alone changes ECS task definitions and services.

## Cross-Region

Deploy production primary and DR regional roots first. Then apply `global/` with the regional bucket/repository/DNS outputs. Do not create two writable Aurora authorities. Test failover before enabling package-release events in production.
