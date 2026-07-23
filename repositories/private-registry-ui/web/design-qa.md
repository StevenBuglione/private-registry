# Registry browse design QA

final result: passed

## Reference and implementation

- Reference: `https://registry.terraform.io/browse/providers` and `https://registry.terraform.io/browse/modules`.
- Implementation: `http://localhost:3000/providers` and `http://localhost:3000/modules` from the production Docker image.
- Desktop comparisons used the same 1440 × 1000 browser viewport (1425 × 990 content capture), signed-out reference state, signed-in Registry administrator state, light theme, default filters, and the top of each page.
- Mobile comparisons used the same 390 × 844 browser viewport (375 × 812 content capture), light theme, and both collapsed and expanded provider-filter states.
- The Azure module-card regression comparison used one in-app browser tab at a 1100 × 1110 CSS viewport, device scale 1, and equal 1085 × 1095 pixel captures after both pages reached their populated state. The reference used `provider=azure`; the implementation used its equivalent `provider=azurerm` server filter.

## Evidence

| State | Reference | Implementation | Combined comparison |
| --- | --- | --- | --- |
| Providers, desktop | `design-qa/terraform-provider-desktop.jpg` | `design-qa/registry-provider-desktop.jpg` | `design-qa/provider-desktop-comparison.jpg` |
| Modules, desktop | `design-qa/terraform-module-desktop.jpg` | `design-qa/registry-module-desktop.jpg` | `design-qa/module-desktop-comparison.jpg` |
| Providers, mobile | `design-qa/terraform-provider-mobile.jpg` | `design-qa/registry-provider-mobile.jpg` | `design-qa/provider-mobile-comparison.jpg` |
| Provider filters, mobile | `design-qa/terraform-provider-mobile-filters.jpg` | `design-qa/registry-provider-mobile-filters.jpg` | `design-qa/provider-mobile-filters-comparison.jpg` |
| Azure modules, desktop | `design-qa/terraform-modules-azure-1100x1110-source.jpg` | `design-qa/registry-modules-azurerm-1100x1110-fixed.jpg` | `design-qa/compare-modules-azure-1100x1110-fixed.jpg` |
| Azure module-card spacing, focused | same source capture | same implementation capture | `design-qa/compare-modules-azure-card-spacing-1100x1110-fixed.jpg` |
| Providers, dark theme | n/a | `design-qa/registry-provider-dark.jpg` | n/a |

## Comparison history

1. Baseline: the local browse page used generic Approval, Lifecycle, and Risk controls, a different content width, an extra All Packages tab, a sort control, and a non-matching mobile filter flow. Classified as P1 because the primary browse experience did not match the requested source.
2. First correction: matched the 336px filter rail, 24px content inset, tab height, headings, provider grid, one-column module cards, result count, and responsive filter button. Replaced the generic filters with Terraform Registry provider tiers/categories and module Partner/provider options. Remaining P2 drift was the shortened tier copy and mobile vertical spacing.
3. Final correction: matched the visible tier copy, info marker, category/provider ordering, filter spacing, mobile header inset, mobile content rhythm, checkbox sizing, card density, and light-theme colors. Combined desktop and mobile comparison images show no unresolved P0, P1, or P2 visual defects.
4. Azure-card regression: the reusable icon defaulted to 70px while the module card reserved a 48px grid track, placing the content 6px inside the icon bounds. Classified as P1 because the overlap affected every module using a provider logo. The module-card icon is now constrained to the official 48px slot with overflow protection and a 16px content gutter. The matched full-view and focused comparisons show the logo, title, and description are separated with no unresolved P0, P1, or P2 defect.

Registry branding, the authenticated user/admin control, APM authorization, truthful internal catalog metadata, and dark mode are intentional product differences. Unsupported Terraform Registry product areas such as Run Tasks, Policies, and publishing are not presented as working Registry features.

## Required fidelity review

- Fonts and typography: the existing Terraform-compatible family, weights, 16px module title, muted description, and metadata hierarchy remain unchanged; the fix introduces no wrapping or truncation drift.
- Spacing and layout rhythm: module logos now occupy a 48px fixed track with a 16px gutter, matching the reference card rhythm and removing the measured negative gap.
- Colors and visual tokens: card surfaces, borders, icon tile, text, and light-theme contrast remain aligned with the reference.
- Image quality and asset fidelity: the supplied Azure provider asset remains sharp and uses `object-fit: contain`; constraining its tile does not crop or stretch it.
- Copy and content: package names, descriptions, versions, and authorization-filtered results are unchanged.

## Functional verification

- Selecting Networking produced `/providers?category=networking` and returned only the 3 PostgreSQL-backed matching providers.
- Selecting AWS produced `/modules?provider=aws` and returned 1–9 of 11 matching modules.
- Tier/category/provider selections use server-side query parameters and remain within the caller's existing APM-authorized access context.
- The mobile Filter Providers control expands and collapses the same filter taxonomy in document flow.
- Dark mode is available from the authenticated user menu, not the global header.
- A browser regression assertion verifies that module logos render at 48px and retain at least a 16px gap before module content at the reported 1100px desktop viewport.
- The frontend unit/component suite, axe checks, strict TypeScript, typed ESLint, Stylelint, dependency-cruiser, Knip, production build, and bundle budget pass.
