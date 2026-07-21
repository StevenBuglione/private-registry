# Terraform platform

Terraform is organized into four deployment scopes:

```text
bootstrap/             State bucket, state KMS key, and protective controls
global/                Optional cross-Region DNS, S3 and ECR replication controls
modules/platform/      Complete regional AWS registry control plane
live/dev|prod|dr/      Environment compositions and approved inputs
```

## Two-pass deployment

The regional module supports `deploy_application_services = false` so durable infrastructure and ECR repositories can be created before images exist. After images, migrations, secrets, and search initialization are ready, set the flag to `true` and apply again.

## State

Use S3 native state locking (`use_lockfile = true`) with bucket versioning, SSE-KMS, access logging, and tightly scoped deployment roles. Do not use local state for shared environments.

## Validation

```bash
terraform fmt -recursive
terraform -chdir=bootstrap init -backend=false
terraform -chdir=bootstrap validate
terraform -chdir=modules/platform init -backend=false
terraform -chdir=modules/platform validate
```

Provider installation and validation must run in CI with network access before deployment. Service/engine versions and instance classes must be confirmed in each target Region.
