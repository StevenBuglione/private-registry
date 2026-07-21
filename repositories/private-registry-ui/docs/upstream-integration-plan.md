# Pinned OpenTofu UI integration plan

The approved baseline is stored in `.upstream/OPEN_TOFU_COMMIT`. At that baseline:

- `frontend/src/query.ts` creates the shared `ky` client;
- `frontend/src/main.tsx` bootstraps React;
- `frontend/src/routes/Module/index.tsx` is the module-page shell;
- `frontend/src/routes/Provider/index.tsx` is the provider-page shell;
- standard API requests are relative to the configured data API prefix.

`apply-overlays.sh` now performs deterministic, fail-closed changes to those four files. It loads `/config/runtime.json`, configures the standard API prefix, and mounts organization-neutral governance/source panels. An upstream pin change that moves or rewrites these integration seams causes CI to fail until reviewed.

The remaining manual fork work is deliberately visual/product work rather than hidden plumbing:

- replace logos, naming, community links, GitHub-owner assumptions, and public submission flows;
- replace stars/forks with approved internal recommendation signals;
- integrate the target design system;
- complete WCAG 2.2 AA remediation;
- generate UI API types from the API repository's versioned compatibility contract;
- add visual and end-to-end tests for every route listed in `docs/integration-checklist.md`.
