# Frontend quality contract

The Registry UI uses independent quality gates for formatting, type safety, semantic correctness, React behavior, accessibility, architecture, dead code, tests, security, and performance. A single green linter is not treated as proof of quality.

## Required local gate

Install the pinned package manager and browser once, then run the complete gate:

```powershell
corepack pnpm install --frozen-lockfile
corepack pnpm test:e2e:install
corepack pnpm quality:full
```

`quality:full` runs the PR-equivalent static analysis, coverage tests, production build, bundle budget, Chromium journeys, axe checks, Lighthouse budgets, and the production dependency audit. The individual commands are available when iterating:

| Concern | Command | Enforcement |
|---|---|---|
| Deterministic formatting | `pnpm format:check` | Biome; no Prettier or ESLint formatting overlap |
| Type correctness | `pnpm typecheck` | Strict TypeScript with unchecked-index and exact-optional checks |
| Semantic and React correctness | `pnpm lint` | Typed ESLint, React Hooks, JSX accessibility, and import rules; zero warnings |
| Authored CSS | `pnpm lint:css` | Stylelint correctness, specificity, nesting, and `!important` constraints |
| Dependency boundaries | `pnpm architecture` | dependency-cruiser cycles, layer direction, test leakage, and generated-output rules |
| Dead code | `pnpm deadcode` | Knip unused file, export, type, and dependency checks |
| Component behavior | `pnpm test:ci` | Vitest, Testing Library, axe, and Istanbul thresholds |
| Browser behavior | `pnpm test:e2e` | Playwright production-build journeys and rendered axe scans |
| Mutation readiness | `pnpm test:mutation:dry` | Stryker instrumentation, TypeScript checker, and complete baseline test run |
| Mutation strength | `pnpm test:mutation` | Scheduled Stryker score gate for runtime, theme, URL, and date boundaries |
| Bundle size | `pnpm bundle:check` | Total initial Brotli JavaScript must remain at or below 250 kB |
| Page quality | `pnpm lighthouse` | Performance, accessibility, best-practice, SEO, Core Web Vital, and script-transfer budgets |
| Supply chain | `pnpm audit:prod` | High or critical production advisories fail |

## Type and runtime boundaries

`strict` is augmented by `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`, `noPropertyAccessFromIndexSignature`, `noImplicitReturns`, `noFallthroughCasesInSwitch`, unused-code checks, and unreachable-code checks. ESLint uses the TypeScript project service and rejects unsafe values, floating promises, promise misuse, unnecessary conditions, non-exhaustive switches, explicit `any`, and deprecated APIs.

Browser responses and runtime configuration are untrusted values. They are parsed through Zod before entering the application model. Type assertions are not a substitute for runtime validation.

## Architecture policy

The current application is intentionally compact, so dependency-cruiser protects concrete dependency direction without forcing empty feature folders:

```text
main/router -> routes -> components
                    -> hooks/context -> API/runtime/model

API/runtime/model -X-> React routes or components
components        -X-> route modules
production        -X-> tests or generated output
all modules       -X-> dependency cycles
```

As a feature grows, expose its public API through one entry module and add a dependency-cruiser rule before moving internals. Deep cross-feature imports are not accepted by convention alone.

## Test and budget policy

The pull-request coverage floor is 80% statements, 80% functions, 80% lines, and 70% branches. Branch coverage is deliberately recorded as a ratchet: raise it to 75% once the next meaningful branch-focused tests land; never reduce a threshold to make a change pass. Stryker separately enforces a 60% breaking floor against deterministic runtime configuration, theme, canonical-link, and date-formatting boundaries; the current baseline is 86.36%.

The browser suite proves:

- desktop and mobile accessibility;
- persisted light/dark theme behavior;
- canonical and legacy provider routes;
- provider resource documentation navigation;
- module input and output visibility;
- APM context propagation on every catalog request; and
- absence of unauthorized catalog metadata in browser responses.

Lighthouse runs against the production build. It requires at least 95 accessibility, 90 best practices, 80 performance, and 90 SEO; LCP at most 2.5 seconds; CLS at most 0.1; total blocking time at most 300 ms; and script transfer at most 256 kB. Size Limit separately caps generated Brotli JavaScript at 250 kB.

## Developer and CI enforcement

`simple-git-hooks` installs a pre-commit hook during dependency installation. `lint-staged` repairs Biome formatting and runs ESLint or Stylelint only for applicable staged files. Hooks are an iteration aid; GitHub Actions remains authoritative.

- `ui-quality.yml`: every PR and main push; static gates, coverage, build, bundle, audit, Chromium/axe, and Lighthouse.
- `ui-codeql.yml`: JavaScript/TypeScript and GitHub Actions analysis with `security-extended` queries.
- `ui-security.yml`: UI container scan and CycloneDX SBOM.
- `ui-nightly.yml`: full Stryker mutation run plus Chromium, Firefox, WebKit, and Lighthouse.
- Dependabot: weekly npm, UI container, and workflow updates.
- SonarQube: optional new-code dashboard and quality gate only when `SONAR_ENABLED=true`; no API key is required for normal builds.

To exercise the main job locally with `act`:

```powershell
act pull_request -W .github/workflows/ui-quality.yml -j quality
```

The checked-in `.actrc` selects the compatible medium Ubuntu runner. Upload steps are skipped when `ACT=true`, so local Actions runs do not require GitHub artifact credentials.

## Suppression policy

Suppressions must be narrow, adjacent to the reason, and reviewed like production code. The current intentional exceptions are:

- React Fast Refresh is disabled only for `router.tsx`, which exports React Router's stable router object by framework convention.
- `!important` is allowed only inside the reduced-motion accessibility override, where it must win over animation declarations.
- Knip ignores `@lhci/cli` because the cross-platform Lighthouse wrapper resolves its executable dynamically on Linux CI.

No source file uses `@ts-ignore`, `@ts-nocheck`, explicit `any`, double assertions, or ESLint disable comments.
