# Terraform Deployment Design

## Ownership

All AWS Terraform lives in `private-registry-api/infrastructure/terraform` so there are only two production repositories.

## Layout

```text
infrastructure/terraform/
├── bootstrap/             state bucket and KMS key
├── modules/platform/      primary-region platform module
├── live/dev/              development root
├── live/prod/             production root
└── live/dr/               gated secondary-region root
```

## State

- S3 backend with KMS encryption, versioning, public-access block, and native lockfile.
- Separate state key per environment/Region.
- State access only through deployment and break-glass roles.
- No secrets in tfvars; sensitive inputs supplied through approved CI/environment mechanisms.
- State bucket is bootstrapped separately and protected from routine destruction.

## Two-pass deployment

### Pass 1 — foundation

```hcl
deploy_application_services = false
```

Creates networking, endpoints, encryption, ECR, S3, Aurora, RDS Proxy, OpenSearch, queues, event bus, IAM, observability, backup, and base load-balancer resources.

### Between passes

- build/push images;
- run migrations;
- install OpenSearch templates/roles;
- create JFrog service credential secret;
- test connectivity;
- supply immutable image tags.

### Pass 2 — services

```hcl
deploy_application_services = true
```

Creates ECS task definitions/services, ALB listener/rules, WAF association, DNS, autoscaling, and reconciliation schedules.

## Environments

`dev` may use smaller service and data capacity, but this reference module intentionally keeps three Availability Zones in every environment. `prod` requires three AZs and deletion protection. `dr` is not a copied writable production stack; it implements Aurora secondary/replication/failover semantics explicitly.

## Provider/version strategy

- pin Terraform CLI in CI;
- constrain provider major versions;
- retain the reviewed `.terraform.lock.hcl` files included for every root module;
- review provider upgrades as pull requests;
- run `terraform fmt`, `validate`, security scanning, and plan on every change;
- require approved environment for apply;
- store plan artifacts and apply exactly the reviewed plan when feasible.

## Bootstrap dependency

The platform cannot depend solely on modules served by the registry it is creating. Keep state/network/identity/bootstrap code locally available until JFrog and the registry are operational.

## DR caveat

Regional recovery requires organization-specific account, Region, DNS, Aurora Global Database, S3 replication, KMS, and JFrog topology decisions. The included DR root is intentionally gated rather than pretending an independent writable copy is safe.
