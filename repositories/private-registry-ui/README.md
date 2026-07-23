# Registry UI

First-party React application for browsing infrastructure providers and modules approved for a signed-in user’s APM groups.

## Responsibilities

The UI owns navigation, search and filter controls, package documentation, governance presentation, version selection, install snippets, access-context selection, and live catalog refresh. The API remains the authorization boundary: every response must already be filtered for the authenticated user and selected APM.

## Local development

```bash
cd web
corepack pnpm install --frozen-lockfile
corepack pnpm dev
```

The Vite development server reads the catalog through same-origin `/api/v1` paths. In the Docker Compose environment, Nginx proxies API and OAuth routes to the Java service.

## Verification

```bash
cd web
corepack pnpm install --frozen-lockfile
corepack pnpm test:e2e:install
corepack pnpm quality:full
cd ..
python scripts/check-runtime-template.py
docker build -t registry-ui:local .
```

The full gate covers Biome, strict TypeScript, typed ESLint, React Hooks, JSX accessibility, Stylelint, dependency-cruiser, Knip, Vitest/Testing Library/axe with coverage, a production build, bundle budgets, Playwright, Lighthouse, and the production dependency audit. See [web/QUALITY.md](web/QUALITY.md) for thresholds, architecture rules, CI cadence, mutation testing, CodeQL, SBOM generation, and local `act` usage.

## Runtime values

| Variable | Purpose |
|---|---|
| `REGISTRY_API_BASE_URL` | Same-origin catalog API base, normally `/api/v1` |
| `REGISTRY_JFROG_HOSTNAME` | Hostname displayed in approved source guidance |
| `REGISTRY_ENVIRONMENT` | Environment label shown in the footer |
| `REGISTRY_SUPPORT_URL` | Optional internal support destination |

Runtime configuration is public browser configuration. It must never contain credentials, tokens, client secrets, group membership data, or internal database values.

## Brand assets

`public/assets/registry-mark.svg` is the canonical header, favicon, PWA, and social-card
mark. Regenerate every derived asset together after changing it:

```bash
cd web
corepack pnpm assets:brand
```

Do not hand-edit the generated PNG or ICO files. The HTML and web manifest use a versioned
asset query so a release can invalidate long-lived immutable browser caches.

## Security boundary

- The browser never calls Microsoft Graph or Artifactory directly.
- Entra tokens remain server-side.
- The API filters list, count, search, detail, documentation, governance, and SSE responses.
- Unauthorized and nonexistent package routes receive the same not-found experience.
- Markdown is sanitized before rendering.
