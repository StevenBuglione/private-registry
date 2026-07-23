# Provider and module detail design QA

- Official provider reference: `https://registry.terraform.io/providers/hashicorp/azurerm/4.37.0`
- Local provider implementation: `http://localhost:3000/providers/hashicorp/azurerm/4.37.0`
- Official module reference: `https://registry.terraform.io/modules/Azure/avm-res-web-site/azurerm/0.16.0`
- Local module implementation: `http://localhost:3000/modules/Azure/avm-res-web-site/azurerm/0.16.0`
- Primary viewport: 1440 x 1000 CSS pixels; both captures render at 1425 x 990 pixels after the browser scrollbar.
- Responsive viewport: 390 x 844 CSS pixels.
- State: authenticated Registry administrator with the approved private JFrog catalog.

## Current-run side-by-side evidence

- Provider overview source: `.codex-artifacts/provider-audit-2026-07-23-2/03-official-provider-full.jpg`
- Provider overview implementation: `.codex-artifacts/provider-audit-2026-07-23-2/12-local-provider-final-1440-full.jpg`
- Provider overview comparison: `.codex-artifacts/provider-audit-2026-07-23-2/13-provider-side-by-side-final.jpg`
- Provider documentation: `.codex-artifacts/perfection-audit-2026-07-23/22-provider-docs-side-by-side-final.jpg`
- Module detail: `.codex-artifacts/perfection-audit-2026-07-23/34-module-side-by-side-verified.jpg`
- Provider mobile source: `.codex-artifacts/provider-audit-2026-07-23-2/14-official-provider-mobile.jpg`
- Provider mobile implementation: `.codex-artifacts/provider-audit-2026-07-23-2/16-local-provider-mobile-final.jpg`
- Module mobile: `.codex-artifacts/perfection-audit-2026-07-23/30-module-mobile-side-by-side-final.jpg`
- Provider dark mode: `.codex-artifacts/perfection-audit-2026-07-23/35-local-provider-dark-1440.jpg`
- Module dark mode: `.codex-artifacts/perfection-audit-2026-07-23/36-local-module-dark-1440.jpg`

The source and implementation captures were taken in the same browser run, at the same viewport and page state, then combined at 1:1 density. The final comparisons were checked for typography, position, dimensions, wrapping, borders, colors, controls, content density, and responsive overflow.

## Findings

No actionable P0, P1, or P2 visual differences remain.

- Header and search stack: the 59px black navigation row, brand lockup, controls, search field, borders, and total 123px stack match the official page geometry.
- Provider identity: breadcrumb, provider icon, title, Official badge, namespace, description, metadata, version selector, and View Source action align with the official desktop and mobile positions.
- Provider overview: the two-column Top downloaded modules grid, count, Helpful Links, Provider Downloads card, All versions selector, installation panel, and governance extension follow the official content grid and visual hierarchy.
- Top downloaded modules: the API sorts authorized azurerm modules by aggregated JFrog download totals, and each card uses the official clock/download metadata pattern instead of displaying a version.
- Helpful Links: the bordered two-column panel now matches the official seven-link order, labels, leading external-link icons, report icon, spacing, border, typography, and destinations.
- Provider documentation: left document navigation, content typography, active state, resource headings, and right installation rail match the official Registry flow.
- Module identity: breadcrumb, Azure icon, title, Partner badge, source path, metadata, version and source actions, Examples control, and Readme/Inputs/Outputs/Dependencies/Resources tabs follow the official layout.
- Module content: README typography and spacing, requirement lists, download statistics, All versions selection, and provision panel match the source structure while keeping the Artifactory-specific install source truthful.
- Responsive behavior: provider and module pages collapse to the same source order without body-level horizontal overflow. At 390px, the provider grid resolves to one 327px column, the sidebar follows the modules and Helpful Links, and the long source link wraps within the mobile metadata rail.
- Themes: light and dark modes preserve the same geometry and contrast. The theme control is present only inside the authenticated user menu and was exercised in both package types.
- Loading behavior: provider module discovery now displays an in-place skeleton instead of briefly claiming there are no matching packages.
- Runtime: browser console warnings and errors were 0 on both final package pages.

## Intentional product differences

These are required product behavior, not visual drift:

- Oremus Labs replaces HashiCorp in the brand lockup.
- The authenticated administrator menu replaces public Publish and Sign in actions.
- JFrog mirror counts, approved versions, package descriptions, and publication data are shown instead of copying public Registry values.
- Approved lifecycle and governance metadata remain visible for the private catalog.
- Provision instructions point at the governed Artifactory artifact.

## Functional verification

- Provider Overview and Documentation navigation: passed.
- Provider download ranking and cursor-compatible `downloads` sorting: passed against the running PostgreSQL-backed API.
- Seven-link Helpful Links structure and source-derived issue URL: passed.
- Provider resource/document navigation: passed.
- Module Readme, Inputs, Outputs, Dependencies, and Resources navigation: passed.
- Version selectors, Examples menu, View Source links, copy action, and download-statistics selector: passed.
- User-menu light/dark switching: passed.
- Desktop and mobile accessibility/browser flows: 5 Playwright scenarios passed, including axe checks.
- Static quality gates: Biome, strict TypeScript, typed ESLint, Stylelint, dependency-cruiser, and Knip passed.
- Unit/component tests: 42 passed.
- Coverage: 83.89% statements, 71.51% branches, 84.97% functions, and 85.48% lines.
- Production build and 250 kB compressed bundle budget: passed at 236.99 kB.
- Java `qualityLocal`, catalog sorting tests, and Docker Compose API/UI health: passed.

final result: passed
