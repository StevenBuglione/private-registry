# Enterprise patch inventory

Keep this file current whenever imported upstream source is changed.

| ID | Area | Purpose | Preferred isolation |
|---|---|---|---|
| UI-001 | Runtime configuration | Load `/config/runtime.json` before API clients initialize | deterministic changes to `app/src/main.tsx` and `app/src/query.ts` |
| UI-002 | Branding | Replace public logos, names, footer, community and submission links | wrapper/layout components and design tokens |
| UI-003 | API base URL | Point standard pages to the private compatibility API | runtime API client configuration |
| UI-004 | Governance | Show owner, support, lifecycle, approval and risk badges | deterministic mounts in module/provider shells plus enterprise components |
| UI-005 | JFrog snippets | Render generated module/provider source and login guidance | `RegistrySourceSnippet.tsx` |
| UI-006 | Search filters | Add owner, support, approval, lifecycle, compatibility and risk filters | enterprise search adapter |
| UI-007 | Authorization UX | Hide unavailable actions while relying on API enforcement | enterprise identity/context layer |
| UI-008 | Security | Enforce sanitized Markdown, external-link policy and CSP-compatible behavior | centralized renderer/configuration |
| UI-009 | Accessibility | Bring all modified views to the approved accessibility standard | wrappers, components, test suite |

## Reviewed source hooks at the pinned commit

`scripts/patch-upstream.py` modifies exactly these upstream files and fails if their reviewed text has moved:

- `app/src/query.ts` — exposes `configureApi`;
- `app/src/main.tsx` — loads runtime configuration before rendering;
- `app/src/routes/Module/index.tsx` — mounts `ModuleEnterprisePanel`;
- `app/src/routes/Provider/index.tsx` — mounts `ProviderEnterprisePanel`.

Branding, recommendation ranking, search filters, and design-system work remain intentionally reviewed source changes. Record every additional path here before merging an intake branch.
