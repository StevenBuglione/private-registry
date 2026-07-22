# ADR: First-party Registry UI

- Status: Accepted
- Date: 2026-07-22

## Decision

Build and maintain a tracked first-party React application. Use the public Terraform Registry as the interaction and layout reference while retaining only our own Registry name, assets, catalog data, authorization model, and support links.

The application must not import, patch, or fetch another registry UI at build time or runtime. Legal provenance for previously evaluated sources remains in `NOTICE.md` and the license inventory.

## Consequences

- UI behavior and API contracts are owned by this repository.
- Visual changes require authenticated desktop and mobile browser comparison.
- Runtime browser configuration is non-secret and same-origin API access is authorization filtered.
- Upstream clone, overlay, patch, and synchronization machinery is obsolete and removed.
