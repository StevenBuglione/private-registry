# CI/CD and Release Workflows

## GitHub to AWS authentication

Use GitHub Actions OIDC and short-lived AWS roles. Do not store AWS access keys. Trust policies restrict:

- organization and repository;
- GitHub environment;
- audience `sts.amazonaws.com`;
- protected branches/tags where applicable.

## UI CI

On pull request:

1. verify pinned upstream provenance and overlays;
2. install with locked dependencies;
3. lint and type-check;
4. run unit and contract tests;
5. run accessibility tests;
6. build production assets;
7. build and scan container;
8. run visual regression tests;
9. produce SBOM.

Release:

1. assume deployment role;
2. build once;
3. push SHA-tagged immutable image to ECR;
4. scan/sign according to policy;
5. register ECS task revision;
6. deploy to dev;
7. promote same digest to higher environments with approval;
8. verify health and rollback automatically on circuit-breaker failure.

## API CI

On pull request:

1. compile and test the Java 25 Spring Boot API with the Gradle Wrapper;
2. validate OpenAPI and JSON Schemas;
3. validate migrations;
4. validate OpenSearch templates;
5. build all command images;
6. scan dependencies/images and generate SBOM;
7. run Terraform fmt/validate/security checks;
8. create environment plan artifacts for infrastructure changes.

## API release

Build and push separate images using one Dockerfile build argument:

```text
api
indexer
reconciler
migrations
```

Run migrations before deploying code that requires the new schema. Migrations are forward-compatible with the currently running API during rolling deployment.

## Terraform deployment

- pull request: plan only;
- main/approved environment: apply reviewed plan;
- production: manual/environment approval and change record;
- no unreviewed `-auto-approve`;
- preserve plan, logs, commit SHA, actor, and outputs as evidence;
- use state locking and concurrency groups.

## Package release workflows

Modules:

- fmt/validate/test;
- terraform-docs and examples;
- policy/security scans;
- candidate publish;
- integration test against JFrog;
- promotion and event.

Providers:

- unit/acceptance tests;
- multi-platform builds;
- tfplugindocs;
- checksums and GPG signature;
- SBOM/provenance;
- candidate publish;
- signature/install tests;
- promotion and event.
