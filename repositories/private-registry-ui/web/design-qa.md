# Terraform Registry visual-parity QA

## Source visual truth

- Live source: `https://registry.terraform.io/`, captured in the in-app browser on July 22, 2026.
- Source states: home, provider browse, module browse, AzureRM overview, AzureRM documentation index, `azurerm_resource_group`, module README, module Inputs, and module Outputs.
- Primary source screenshots:
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/source-home-desktop-full.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/source-provider-resource-desktop.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/source-module-inputs-desktop.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/source-home-mobile.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/source-provider-resource-mobile.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/source-module-inputs-mobile.png`
- Captured source geometry and tokens: system sans-serif stack, 16/24 body text, 30/38/700 H1, 36-pixel controls, five-pixel control radii, black application shell, white content surfaces, restrained gray borders, blue active states, and pale purple notices.
- Terraform and HashiCorp marks, public-marketplace claims, public authentication actions, and public download statistics are not copied. They are intentionally replaced with Registry identity, Entra identity, APM authorization, truthful mirrored counts, JFrog paths, lifecycle, approval, and risk data.

## Implementation evidence

- Local implementation: `http://localhost:3000/`
- Compared local states:
  - `/`
  - `/providers`
  - `/providers/hashicorp/azurerm/4.37.0?tab=docs&doc=resources%2Fresource_group.md`
  - `/modules/Azure/avm-res-network-virtualnetwork/azurerm/0.8.1?tab=inputs`
- Viewports: 1440 x 1000, 1024 x 900, 768 x 900, and 390 x 844 CSS pixels.
- Final light screenshots:
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/final-home-light-1440.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/final-provider-resource-light-1440.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/final-module-inputs-light-1440.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/final-home-light-1024.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/final-providers-light-768.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/final-home-light-390.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/final-provider-resource-light-390.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/final-module-inputs-light-390.png`
- Final dark screenshots:
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/final-home-dark-1440.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/final-provider-resource-dark-1440.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/final-module-inputs-dark-1440.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/final-home-dark-390.png`
- Required same-viewport side-by-side boards:
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/compare-home-light-1440.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/compare-provider-resource-light-1440.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/compare-module-inputs-light-1440.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/compare-home-light-390.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/compare-provider-resource-light-390.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/terraform-ui-reference/compare-module-inputs-light-390.png`

## Comparison history

1. Initial P1: the private home, provider docs, and module Inputs used a looser product-specific layout, module tables, and a generic documentation navigator.
   - Fix: matched the official shell, search band, notice, hero, contextual strip, card grid, detail header, tabs, three-column documentation layout, and definition-list Inputs/Outputs anatomy.
2. Initial P1: AzureRM resources were not organized or filtered like the official provider documentation.
   - Fix: added service-grouped navigation, nested Resources/Data Sources/List Resources sections, automatic filtered expansion, matching-result counts, selected states, and an on-this-page rail.
3. Initial P1: the module Inputs and Outputs omitted the official required/optional structure and presented incomplete mirrored descriptions literally.
   - Fix: added Required Inputs and Optional Inputs groups, descriptions, defaults, type badges, copy controls, and safe `No description published.` normalization.
4. Initial P2: the first combined home comparison showed notice, hero, and access-context bands taller than the source; the module header also carried excess vertical space.
   - Fix: restored the measured 74-pixel notice, 320-pixel hero, 54-pixel context band, and tightened the module detail header.
5. Final combined comparison: home, AzureRM resource documentation, module Inputs, and mobile states have no remaining P0, P1, or P2 visual defects.

## Required fidelity surfaces

- Typography: system sans-serif type, source-like hierarchy, weights, line heights, code treatment, and compact metadata.
- Geometry: matching black shell, full-width search, measured home bands, three-column cards, detail header, underline tabs, provider documentation rail/article/table-of-contents, and module content/sidebar proportions.
- Controls: matching 36-pixel buttons and selects, restrained radii and borders, blue active states, searchable documentation navigation, version controls, copy controls, and responsive menu behavior.
- Data fidelity: 12 providers and 30 modules are backend-derived. Versions, owners, lifecycle, risk, APM access, source repositories, and Artifactory paths remain private and truthful.
- Asset fidelity: existing real provider assets and the first-party Registry emblem are used. Interface icons come from Phosphor; no CSS art, emoji, text-glyph substitutes, placeholder boxes, handcrafted SVGs, or remote hotlinks were introduced.
- Responsive behavior: the mobile implementation preserves the official information hierarchy while preventing the source site's horizontal clipping from hiding primary controls or placing the install rail before the Inputs content.
- Dark mode: the official Registry has no dark theme to capture. The requested dark mode therefore preserves the measured layout and interaction model while applying a neutral charcoal inversion, accessible contrast, matching borders, and the same blue/purple semantics. The selection persists across reloads and respects the system preference for first use.

## Interaction and runtime verification

- Real Entra-authenticated session loaded as `Registry E2E Administrator`; no mock identity or Graph response was used.
- Browse menu, authorized global search, provider/module navigation, APM context, version switching, documentation filtering, tabs, copy feedback, and mobile menu were exercised in the in-app browser.
- AzureRM `resource_group` documentation rendered real mirrored notes, example content, arguments, timeouts, and import sections.
- Theme selection persisted across a reload.
- The browser error/warning log was empty after the final Compose rebuild.
- Docker Compose reported API, indexer, LocalStack, OpenSearch, PostgreSQL, and UI healthy.
- `pnpm lint`: passed.
- `pnpm test`: 6 files and 13 tests passed, including accessibility checks.
- `pnpm build`: passed.

## Intentional product differences

- Registry and its emblem replace Terraform/HashiCorp marks.
- Entra user/APM controls replace public Publish and Sign In actions.
- Private governance and Artifactory installation data replace public download panels, marketplace tiers, and public package statistics.
- A light/dark toggle is added because it is an explicit product requirement.
- The mirrored AzureRM version is 4.37.0 while the captured public source version is 4.81.0.

These are required identity, authorization, and data differences rather than visual defects.

final result: passed
