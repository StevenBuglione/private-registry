# Provider and module detail design QA

- Official provider reference: `https://registry.terraform.io/providers/hashicorp/azurerm/4.37.0`
- Local provider implementation: `http://localhost:3000/providers/hashicorp/azurerm/4.37.0`
- Official module reference: `https://registry.terraform.io/modules/Azure/avm-res-web-site/azurerm/0.16.0`
- Local module implementation: `http://localhost:3000/modules/Azure/avm-res-web-site/azurerm/0.16.0`
- Official IAM module reference: `https://registry.terraform.io/modules/terraform-aws-modules/iam/aws/6.2.1`
- Local IAM module implementation: `http://localhost:3000/modules/terraform-aws-modules/iam/aws/6.2.1`
- Official IAM child reference: `https://registry.terraform.io/modules/terraform-aws-modules/iam/aws/6.2.1/submodules/iam-account`
- Local IAM child implementation: `http://localhost:3000/modules/terraform-aws-modules/iam/aws/6.2.1/submodules/iam-account`
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
- IAM root comparison: `.codex-artifacts/module-iam-audit-2026-07-23/20-side-by-side-iam-root.jpg`
- IAM submodule comparison: `.codex-artifacts/module-iam-audit-2026-07-23/21-side-by-side-iam-account.jpg`
- IAM root mobile: `.codex-artifacts/module-iam-audit-2026-07-23/19-local-iam-root-mobile-390.jpg`
- IAM submodule mobile: `.codex-artifacts/module-iam-audit-2026-07-23/18-local-iam-account-mobile-390.jpg`
- IAM submodule dark mode: `.codex-artifacts/module-iam-audit-2026-07-23/17-local-iam-account-dark-1440.jpg`
- Breadcrumb module comparison: `.codex-artifacts/breadcrumb-audit-2026-07-23/08-module-breadcrumb-comparison.jpg`
- Breadcrumb provider comparison: `.codex-artifacts/breadcrumb-audit-2026-07-23/09-provider-breadcrumb-comparison.jpg`
- Breadcrumb namespace destination: `.codex-artifacts/breadcrumb-audit-2026-07-23/06-local-namespace-after-click.jpg`

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
- IAM child navigation: the root Submodules and Examples controls occupy the official position and every entry targets a canonical internal Registry route. The `iam-account` submodule renders its Artifactory-backed README locally instead of navigating to GitHub.
- IAM child metadata: the submodule exposes the official 12 Inputs, 1 Output, 1 Dependency, and 2 Resources. The example page exposes Readme, 0 Inputs, and 1 Output; the root exposes Readme, 0 Inputs, 0 Outputs, 2 Dependencies, and 26 Resources.
- IAM root metadata: the public upstream description and August 26, 2025 publication date are present, with eight submodules and eight examples extracted from the pinned source archive.
- Responsive behavior: provider and module pages collapse to the same source order without body-level horizontal overflow. At 390px, the provider grid resolves to one 327px column, the sidebar follows the modules and Helpful Links, and the long source link wraps within the mobile metadata rail.
- Themes: light and dark modes preserve the same geometry and contrast. The theme control is present only inside the authenticated user menu and was exercised in both package types.
- Loading behavior: provider module discovery now displays an in-place skeleton instead of briefly claiming there are no matching packages.
- Breadcrumb behavior: provider, module, and child-module breadcrumbs now use the same browse, publisher namespace, package, and terminal version semantics as the official Registry. Publisher links open an authorized, server-filtered namespace catalog.
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
- Desktop and mobile accessibility/browser flows: 6 Playwright scenarios passed, including axe checks and exact breadcrumb navigation.
- Static quality gates: Biome, strict TypeScript, typed ESLint, Stylelint, dependency-cruiser, and Knip passed.
- Unit/component tests: 46 passed.
- Coverage: 84.28% statements, 71.29% branches, 84.89% functions, and 86% lines.
- Production build and 250 kB compressed bundle budget: passed at 237.97 kB.
- Java `qualityPr` (Error Prone, NullAway, Checkstyle, PMD baseline, SpotBugs/FindSecBugs, tests, coverage, architecture, and SBOM): passed.
- Docker Compose has exactly PostgreSQL, API, and UI services; all three are healthy.
- PostgreSQL contains the real IAM 6.2.1 metadata extracted from the JFrog-seeded artifact: 8 submodules, 8 examples, 160 inputs, 150 outputs, 62 dependencies, 30 resources, and 34 data sources across the root and child scopes.

final result: passed
