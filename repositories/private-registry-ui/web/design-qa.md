# Terraform Registry visual-parity QA

## Source and implementation

- Source of visual truth: `https://registry.terraform.io/`, captured in the in-app browser on July 22, 2026.
- Local implementation: `http://localhost:3000/` from the production Docker Compose stack.
- Compared authenticated states: home, AzureRM provider documentation, and Helm module Outputs.
- Comparison viewport: 1440 x 900 CSS pixels at DPR 1. The browser capture area is 1425 x 891 pixels because application chrome is excluded.
- Responsive verification: 1440, 1024, 768, and 390 CSS-pixel widths; the automated browser suite asserts no horizontal overflow at the intermediate widths and exercises the mobile navigation at 390 pixels.

The public Terraform/HashiCorp marks, Publish action, public sign-in, public download figures, and marketplace claims are intentionally excluded. Registry identity, Entra identity, APM authorization, truthful mirrored counts, governance metadata, and Artifactory installation data replace them without changing the source layout model.

## Measured visual system

- Font stack: `-apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif, "Apple Color Emoji", "Segoe UI Emoji", "Segoe UI Symbol"`.
- Body type: 16-pixel font with 24-pixel line height.
- Application header: 60 pixels high; global search band: 63 pixels high.
- Home H1: 30/38 pixels at weight 700; hero copy: 14/20 pixels.
- Buttons and selects: 36 pixels high with five-pixel radii.
- Provider cards: three-column desktop grid with source-matched border, padding, icon geometry, and compact metadata.
- Detail pages: compact breadcrumb/header metadata, underline tabs, provider documentation tree/article/on-this-page columns, and module content/sidebar proportions.

## Side-by-side evidence

These images combine the public reference and local implementation in the same input before judgment:

- Home: `C:/Users/steve/.codex/visualizations/2026/07/21/019f86b6-4327-7230-8f11-3fbfde162fa1/terraform-registry-audit/comparison-home-1440x900.png`
- AzureRM documentation: `C:/Users/steve/.codex/visualizations/2026/07/21/019f86b6-4327-7230-8f11-3fbfde162fa1/terraform-registry-audit/comparison-azurerm-docs-1440x900.png`
- Helm module Outputs: `C:/Users/steve/.codex/visualizations/2026/07/21/019f86b6-4327-7230-8f11-3fbfde162fa1/terraform-registry-audit/comparison-module-outputs-1440x900.png`

Final local captures:

- Light home: `C:/Users/steve/.codex/visualizations/2026/07/21/019f86b6-4327-7230-8f11-3fbfde162fa1/terraform-registry-audit/local-final-home-light-full-1440.png`
- Light AzureRM documentation: `C:/Users/steve/.codex/visualizations/2026/07/21/019f86b6-4327-7230-8f11-3fbfde162fa1/terraform-registry-audit/local-final-azurerm-docs-light-1440x900.png`
- Light module Outputs: `C:/Users/steve/.codex/visualizations/2026/07/21/019f86b6-4327-7230-8f11-3fbfde162fa1/terraform-registry-audit/local-final-module-outputs-light-1440x900.png`
- Dark home: `C:/Users/steve/.codex/visualizations/2026/07/21/019f86b6-4327-7230-8f11-3fbfde162fa1/terraform-registry-audit/local-final-home-dark-1440x900.png`
- Dark AzureRM documentation: `C:/Users/steve/.codex/visualizations/2026/07/21/019f86b6-4327-7230-8f11-3fbfde162fa1/terraform-registry-audit/local-final-azurerm-docs-dark-1440x900.png`
- Dark module Outputs: `C:/Users/steve/.codex/visualizations/2026/07/21/019f86b6-4327-7230-8f11-3fbfde162fa1/terraform-registry-audit/local-final-module-outputs-dark-1440x900.png`

## Comparison history

1. The application used an APM dropdown and a top-level theme control, unlike the requested authenticated shell.
   - Fixed: all entitled APMs are aggregated and enforced server-side; the selector was removed. Theme selection now lives only inside the signed-in user menu.
2. The Registry emblem escaped its header container.
   - Fixed: a measured 25 x 25 frame clips the 25 x 25 image. Browser inspection confirmed identical frame and image bounds, hidden overflow, and no internal scroll overflow.
3. Home content diverged with Featured Modules and a standalone Docs route.
   - Fixed: Featured Modules became the Terraform/provider/module explainer section, `/docs` redirects home, and Docs was removed from desktop and mobile navigation.
4. The provider latest route omitted the resource documentation hierarchy and the module latest route omitted Inputs/Outputs.
   - Fixed: latest-version symbol enrichment now returns the same provider groups and module definitions as pinned versions. Browser checks confirmed AzureRM groups and the Helm `deployment` output.
5. Homepage notice text and featured-provider selection were static.
   - Fixed: registry administrators can update the notice and choose up to six featured providers from the signed-in menu. Settings persist in PostgreSQL and changes are audited.
6. Final combined comparisons show source-matched typography, density, geometry, controls, and information hierarchy with no remaining P0, P1, or P2 visual defects.

## Interaction and runtime verification

- User-menu light/dark selection worked and persisted across reloads.
- Admin homepage settings loaded and saved successfully; the final home rendered six selected providers.
- `/docs` redirected to `/`.
- No APM selector is present and catalog requests contain no client-selected `apm_id`.
- AzureRM latest documentation exposed Guides, Functions, AAD B2C, Active Directory Domain Services, Advisor, and subsequent resource groups.
- Helm latest Outputs exposed the `deployment` output.
- The header brand measured 25 x 25 pixels, was fully contained, and did not overflow.
- Browser console inspection found no local application errors.
- Playwright, axe-core, and responsive overflow checks passed at the required widths.

## Intentional private-registry differences

- Registry identity replaces Terraform/HashiCorp branding.
- The authenticated Entra user menu replaces Publish and public Sign In actions.
- APM authorization is server-side and aggregate; there is no client access-context dropdown.
- Private governance and Artifactory provisioning replace public download analytics.
- Dark mode is an added requirement; it preserves the measured layout while applying accessible charcoal surface tokens.
- Mirrored package versions differ from the live public versions and remain truthful to the local catalog.

final result: passed
