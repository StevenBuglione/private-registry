# Bootstrap the Two Production Repositories

## Export

From this blueprint repository:

```bash
./scripts/export-repositories.sh ../private-registry-export
```

The command creates:

```text
../private-registry-export/private-registry-ui
../private-registry-export/private-registry-api
```

It refuses unsafe destinations and never deletes the blueprint source.

## Initialize UI repository

```bash
cd ../private-registry-export/private-registry-ui
git init -b main
git add .
git commit -m "Initialize private registry UI"
git remote add origin <UI_REPOSITORY_URL>
git push -u origin main
```

Apply protections before running the release workflow. Then import the pinned upstream frontend on an intake branch; do not change the pin and source code in an unreviewed deployment commit.

## Initialize API repository

```bash
cd ../private-registry-export/private-registry-api
git init -b main
git add .
git commit -m "Initialize private registry API and AWS platform"
git remote add origin <API_REPOSITORY_URL>
git push -u origin main
```

The exported API repository includes reviewed dependency lock files for each Terraform root. Run `terraform init` in a network-enabled environment and review any lock-file change before committing it.

## Required repository settings

- protected `main` and required code-owner approval;
- required CI, code scanning, secret scanning, dependency review, and image/IaC policy checks;
- no direct production push or unreviewed environment deployment;
- GitHub environments with approvers and deployment branch restrictions;
- OIDC role trust constrained to exact owner/repository/environment;
- repository-level `GITHUB_TOKEN` permissions read-only unless a specific workflow needs more;
- immutable releases and documented rollback.

## Cross-repository contract

The API repository is authoritative for OpenAPI and JSON Schemas. Publish them as a versioned release/CI artifact. The UI repository generates TypeScript types from that artifact. Any breaking compatibility-route change requires a coordinated UI/API release and migration plan.
