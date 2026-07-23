# Terraform platform

Terraform is organized into four deployment scopes:

```text
bootstrap/             State bucket, state KMS key, and protective controls
global/                Optional cross-Region DNS, S3 and ECR replication controls
modules/platform/      Complete regional AWS registry control plane
live/dev|prod|dr/      Environment compositions and approved inputs
jfrog/                 JFrog public Terraform mirror configuration
tests/registry-mirror/ Module/provider pull-through smoke test
```

## Two-pass deployment

The regional module supports `deploy_application_services = false` so durable infrastructure and ECR repositories can be created before images exist. After images, migrations, secrets, and search initialization are ready, set the flag to `true` and apply again.

The current application-service definitions have known contract gaps and must not be
enabled unchanged. Review the repository-level
`docs/32-deployment-readiness-audit.md` before using this sequence.

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

## JFrog public registry proof

The `jfrog/` root manages two public pull-through caches in Artifactory:

- `iac-modules-public-remote` for public modules;
- `iac-providers-public-remote` for public providers.

This root declares an Azure Storage backend without embedding account coordinates.
Supply an ignored `backend.hcl` plus Artifactory provider credentials through
`JFROG_URL` and `JFROG_ACCESS_TOKEN`; never put a token in a Terraform file or tfvars
file. The Artifactory provider itself is a bootstrap dependency downloaded from the
public Terraform Registry before the mirrors exist.

The `tests/registry-mirror/` root proves that a pinned public module and provider resolve
through JFrog. Its `mirror.tfrc` excludes `hashicorp/null` from direct installation, so
the smoke test cannot silently fall back to the public provider registry. Supply the
same token through the hostname-derived `TF_TOKEN_*` variable and point
`TF_CLI_CONFIG_FILE` at that file only while running the smoke test. Replace the
non-routable `artifacts.example.invalid` placeholder in the fixture first.

These two Terraform-managed repositories are caches only. The Java seeder owns the three
governed local release repositories and their curated catalog; see the deployment
handoff for the token and process boundary.
