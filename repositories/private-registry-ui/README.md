# Private Registry UI

Controlled enterprise-neutral frontend fork of the OpenTofu Registry UI. The application keeps the upstream registry-specific experience for modules, providers, versioned documentation, examples, resources, data sources, functions, and search, while replacing the public-registry backend with the private catalog API.

## Responsibility boundary

This repository owns only:

- the controlled upstream frontend import;
- branding and design-system integration;
- runtime configuration;
- standard registry pages and enterprise governance panels;
- browser-side authentication context supplied by the internal ALB;
- the Nginx container and UI ECS deployment workflow;
- UI tests and upstream intake controls.

It does **not** own JFrog, package downloads, the catalog database, search indexing, package governance decisions, AWS shared infrastructure, or API authorization.

## Upstream baseline

The reviewed upstream commit is stored in:

```text
.upstream/OPEN_TOFU_COMMIT
```

Import it into `app/`:

```bash
./scripts/import-upstream.sh
./scripts/apply-overlays.sh
```

Never build a production image from an unpinned branch. Preserve upstream license and notice files copied by the import process.

## Local workflow

```bash
./scripts/import-upstream.sh
./scripts/apply-overlays.sh
cd app
corepack pnpm install --frozen-lockfile
corepack pnpm lint
corepack pnpm run test --run
corepack pnpm build
cd ..
docker build -t private-registry-ui:local .
```

The built container reads non-secret runtime configuration from environment variables and writes `/config/runtime.json` during Nginx startup. The same image is promoted across environments.

## Required runtime values

| Variable | Purpose |
|---|---|
| `REGISTRY_DATA_API_URL` | Same-origin OpenTofu compatibility API prefix, normally `/registry/docs/` |
| `REGISTRY_ENTERPRISE_API_URL` | Enterprise extension base, normally `/api/v1/enterprise` |
| `REGISTRY_JFROG_HOSTNAME` | Hostname used to display login/source guidance |
| `REGISTRY_ENVIRONMENT` | `development`, `test`, `production`, or `dr` |
| `REGISTRY_FEATURE_PROVIDERS` | Feature flag, `true`/`false` |
| `REGISTRY_FEATURE_MODULES` | Feature flag, `true`/`false` |
| `REGISTRY_FEATURE_SECURITY` | Security tab flag |
| `REGISTRY_FEATURE_AUDIT` | Audit tab flag |
| `REGISTRY_SUPPORT_URL` | Internal support URL |

None of these values may contain credentials.

## API routing

The internal ALB routes:

```text
/                       -> registry-web ECS target group
/api/*                  -> catalog-api ECS target group
/registry/docs/*        -> catalog-api ECS target group
/top/*                  -> catalog-api ECS target group
```

This keeps browser calls same-origin. The browser never calls JFrog directly.

## Production gates

- legal/open-source review completed;
- upstream commit and patch inventory recorded;
- corporate branding and accessibility review completed;
- compatibility contract tests pass;
- CSP and Markdown rendering security tests pass;
- no secret or private endpoint is embedded at build time;
- immutable ECR image is deployed by digest or commit SHA;
- visual regression covers module, provider, search, deprecation, revocation, and error states.
