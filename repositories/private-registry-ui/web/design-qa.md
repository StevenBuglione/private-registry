# Registry design QA

final result: passed

## Module and provider detail parity audit — 2026-07-23

### Comparison target

- Module source: `https://registry.terraform.io/modules/terraform-aws-modules/iam/aws/6.2.1`.
- Module implementation: `http://localhost:13000/modules/terraform-aws-modules/iam/aws/6.2.1`.
- Provider source: `https://registry.terraform.io/providers/hashicorp/azurerm/4.37.0`.
- Provider implementation: `http://localhost:13000/providers/hashicorp/azurerm/4.37.0`.
- Resources comparison: `D:/Temp/registry-detail-deep-audit-2026-07-23/42-comparison-resources-current.png`.
- Dependencies comparison: `D:/Temp/registry-detail-deep-audit-2026-07-23/39-comparison-dependencies-current.png`.
- Provider comparison: `D:/Temp/registry-detail-deep-audit-2026-07-23/33-comparison-provider-final.png`.
- Dark-mode implementation: `D:/Temp/registry-detail-deep-audit-2026-07-23/29-local-module-resources-final-dark-verified.png`.
- Mobile implementation: `D:/Temp/registry-detail-deep-audit-2026-07-23/36-local-module-resources-final-mobile-verified.png`.
- Desktop source and implementation states were captured at the same 1440 × 1000 CSS viewport. The mobile implementation was captured at 390 × 844.

### Findings and corrections

1. Baseline P1: Resources and Dependencies used generic bordered cards with internal implementation metadata. Terraform Registry uses explanatory copy, section dividers, inline code, and compact lists. Both tabs now use the source information architecture and spacing.
2. Baseline P1: the no-root-configuration warning appeared only on Readme. The source displays it on every affected module tab. The implementation now does the same.
3. Baseline P2: the IAM detail header used the AWS provider tile instead of the module namespace avatar. The checked-in namespace asset now matches the source in light mode and retains a white tile for dark-mode legibility.
4. Baseline P2: breadcrumbs used chevrons, the latest version label included an extra suffix, download totals lacked the source pill treatment, and the AzureRM page omitted its Public Cloud category. These details now match the source.
5. Mobile P1: a long resource identifier expanded the root document from 375px to 479px. Resource identifiers now wrap safely; the final 390px viewport has equal 375px client and document scroll widths. The tab strip remains independently scrollable.
6. Provider and populated Inputs comparisons confirmed the shared grid, tabs, cards, docs navigation, definition typography, and controls already matched. Remaining count, publisher, description, and catalog-card differences are truthful private Artifactory data, not visual defects.

### Required fidelity review

- Fonts and typography: system UI and monospace stacks, 30px package title, 18px content headings, 14px body copy, 22px line height, and inline-code treatment match the measured source.
- Spacing and layout rhythm: warning, content rail, sidebar, tab strip, dividers, lists, and provider module cards align in the equal-state comparisons.
- Colors and themes: light surfaces and borders match the source. Dark mode uses the same hierarchy with verified readable text, warning, code, and namespace artwork.
- Content and behavior: no-root warnings, provider dependencies, resource inventory, version selection, breadcrumbs, child menus, tabs, downloads, install controls, provider category, and internal navigation are functional.
- Responsiveness and accessibility: the 390px page has no root horizontal overflow, controls remain keyboard-accessible, semantic regions/lists are retained, and axe-backed component tests pass.

### Functional and quality verification

- Browser console errors and warnings: none observed during matching states.
- Docker UI and preview containers: healthy after the final production-image rebuild.
- Frontend tests: 71 passed.
- Static quality: Biome, strict TypeScript, typed ESLint with zero warnings, Stylelint, dependency-cruiser, and Knip passed.
- Delivery quality: the production Vite build passed. Bundle budgets passed at 168.96kB initial and 237.58kB total brotli-compressed JavaScript.
- No unresolved P0, P1, or P2 visual defect remains in the audited module Resources, Dependencies, Readme, populated Inputs, provider Overview, provider Documentation, light, dark, desktop, or mobile states.

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

## Module HCL code-block QA — 2026-07-23

### Comparison target

- Source visual truth: `D:/Temp/registry-code-block-audit/01-official-light.png`, captured from `https://registry.terraform.io/modules/terraform-aws-modules/iam/aws/latest?tab=readme#iam-group`.
- Rendered implementation: `D:/Temp/registry-code-block-audit/05-local-after-light.png`, captured from `http://localhost:13000/modules/terraform-aws-modules/iam/aws/6.2.1?tab=readme#iam-group`.
- Light-state full-view comparison: `D:/Temp/registry-code-block-audit/08-official-vs-local-light.png`.
- Light-state focused comparison: `D:/Temp/registry-code-block-audit/09-official-vs-local-focused.jpg`.
- Dark-state implementation: `D:/Temp/registry-code-block-audit/06-local-after-dark.png`.
- Mobile dark-state implementation: `D:/Temp/registry-code-block-audit/07-local-after-mobile-dark.png`.
- Source and desktop implementation were captured at the same 922 × 1017 CSS viewport. Browser scrollbar exclusion produced equal 907 × 1000 pixel images at device scale 1 and 72 DPI. The focused comparison uses equal 817 × 700 pixel crops without resizing.
- State: authenticated local Registry administrator, IAM Group readme anchor, production Docker image, light and dark themes. The official source does not expose this product's dark theme, so dark mode was checked for internal token consistency, contrast, and layout rather than false pixel equivalence.

### Findings and comparison history

1. Baseline P1: `.documentation.source-readme code` applied inline-code borders, backgrounds, and magenta text to the fenced `<code>` element. Because that element was inline, the border and background were painted around every wrapped line fragment, producing the reported pink ruled-paper effect and inflating the block.
2. Baseline P1: the local fenced block used 27.2px line height, a rounded bordered container, icon-based 10px copy control, token underlines, and a generic monospace stack. The official block uses a borderless square surface, 14.08px SFMono/Consolas stack, 22.4px line height, neutral properties, purple values, and a text-only 35px copy button.
3. First correction: fenced code received a higher-specificity reset, deterministic HCL tokenization, official typography, colors, padding, button geometry, and trailing-newline removal. The focused comparison showed the code itself aligned, but the 922px implementation still kept a 290px installation rail beside the document, narrowing the block and forcing avoidable horizontal scrolling. This remained P2.
4. Final correction: package overview layouts now collapse to one column at 1100px, matching the reference's medium-width document behavior. The 922px block measures 817px wide, contains the complete example without page overflow, and retains block-local horizontal scrolling for genuinely narrow screens. No actionable P0, P1, or P2 findings remain.

### Required fidelity review

- Fonts and typography: exact official code stack, 14.08px size, 500 weight, and 22.4px line height. The rendered example has the same 27 lines as the source with no inherited decoration or token boxes.
- Spacing and layout rhythm: 14.08px preformatted padding, 50px control offset, zero radius, zero border, 35px copy control, and single-column medium layout. The focused comparison aligns line starts and vertical rhythm.
- Colors and visual tokens: light surface `rgb(247, 248, 250)`, base text `rgb(31, 33, 36)`, properties `rgb(82, 87, 97)`, and strings/booleans `rgb(92, 78, 229)` match the measured source values. Dark mode uses the existing Registry surface tokens with high-contrast neutral and purple syntax colors.
- Image quality and asset fidelity: this region contains no image assets or icons. The copy control intentionally contains no synthetic icon, matching the source.
- Copy and content: the mirrored Artifactory readme text is unchanged. The renderer strips only the Markdown fence's final newline, so copied HCL is exact and does not gain a trailing blank line.
- Responsiveness and accessibility: at 390 × 844, document width equals viewport content width with no page-level horizontal overflow; the preformatted block scrolls internally (`333px` client width, `572px` scroll width). The copy control is a native button with an accessible text label and exposes a visible `Copied` confirmation.

### Functional and quality verification

- Copy interaction changed exactly one IAM Group control from `Copy` to `Copied`; the rendered control returned to its idle label on the existing timer.
- Browser console warnings and errors: none.
- Frontend verification: 70 tests passed, including HCL semantic-token and plain-code fallback coverage.
- Static quality: Biome, strict TypeScript, typed ESLint with zero warnings, Stylelint, dependency-cruiser, and Knip passed.
- Delivery quality: production Vite build and both bundle budgets passed (168.9kB initial JavaScript; 237.14kB total JavaScript).
- Focused comparison was required because the full screenshots include different sticky-header scroll states; the equal-size focused crop isolates the actual code renderer without hiding that full-view state difference.

## Module download selector QA — 2026-07-23

### Comparison target

- Source visual truth: `D:/Temp/codex-clipboard-105cf84c-e81b-43b6-8c29-434070519be4.png`.
- Rendered implementation, narrow light state: `D:/Temp/registry-download-card-audit/06-after-narrow-light-full.png`.
- Rendered implementation, desktop light state: `D:/Temp/registry-download-card-audit/04-after-desktop.png`.
- Rendered implementation, desktop dark state: `D:/Temp/registry-download-card-audit/05-after-desktop-dark.png`.
- Focused before/after comparison: `D:/Temp/registry-download-card-audit/07-before-vs-after-focused.png`.
- The supplied source is 436 × 346 pixels at 96 DPI. The narrow implementation is a 395 × 482 pixel browser content capture from a 410 × 500 CSS viewport at device scale 1 and 72 DPI. The focused card comparison normalizes both cards to 374 pixels wide without changing their aspect ratios.
- State: authenticated local Registry administrator, IAM module download statistics card, “All versions” selected, production Docker image. Both light and dark themes were checked.

### Findings and comparison history

1. Baseline P1: the native select was hard-coded to 106px while its left/right padding and native chevron reserved approximately 42px. Only about 62px remained for the selected label, causing “All versions” to be cut to “All versi” under the source browser’s font rendering and DPI.
2. Final correction: the select now reserves 132px. At the fixed 340px desktop sidebar, the 145.66px heading, 12px gap, and 132px select fit within the 304px header. At the 410px narrow viewport, the card is 363px wide, the full label is visible, and the document has no horizontal overflow.
3. No actionable P0, P1, or P2 findings remain.

### Required fidelity review

- Fonts and typography: the existing 12px system-font select styling is unchanged; only the text-bearing width changed, so the label no longer truncates.
- Spacing and layout rhythm: the wider control preserves the 12px header gap, aligns with the statistics rows, and fits both the 340px sidebar and 363px narrow card.
- Colors and visual tokens: light and dark backgrounds, borders, foregrounds, and native chevron treatment remain consistent with the existing Registry tokens.
- Image quality and asset fidelity: no raster assets are present. The existing Phosphor download icon remains sharp and correctly aligned.
- Copy and content: “All versions,” “Version 6.2.1,” and “Version 6.2.0” remain unchanged and are fully selectable.
- Accessibility and behavior: the native select keeps its “Download statistics version” accessible label. Selecting Version 6.2.1 changed the all-time count to 14; restoring All versions restored the aggregate selection.

### Functional and quality verification

- Browser console warnings and errors: none.
- Light narrow viewport: 410 × 500 CSS pixels, no page overflow.
- Light and dark desktop viewport: 1280 × 720 CSS pixels, no page overflow.
- Frontend verification: all 70 tests passed.
- Stylelint, production build, dependency audit, and both JavaScript bundle budgets passed.

## Global header and search margins QA — 2026-07-23

### Comparison target

- Source visual truth: `D:/Temp/registry-header-audit/01-official-before.png`, captured from `https://registry.terraform.io/modules/terraform-aws-modules/iam/aws/latest`.
- Baseline implementation: `D:/Temp/registry-header-audit/02-local-before.png`.
- Corrected implementation: `D:/Temp/registry-header-audit/04-local-after.png`, captured from `http://localhost:13000/modules/terraform-aws-modules/iam/aws/6.2.1?tab=readme`.
- Baseline full-view comparison: `D:/Temp/registry-header-audit/03-before-comparison.png`.
- Corrected full-view comparison: `D:/Temp/registry-header-audit/05-after-comparison.png`.
- Corrected focused header comparison: `D:/Temp/registry-header-audit/06-after-header-focused.png`.
- Source and implementation were captured at the same 1440 × 900 CSS viewport. Browser scrollbar exclusion produced equal 1425 × 900 pixel captures at device scale 1; no density normalization or resizing was applied.
- State: source Registry module page and authenticated local Registry administrator module page, light theme, top-of-page scroll position, production Docker images.

### Findings and comparison history

1. Baseline P2: the official search form measured 1344px wide at x=40.5px, while the implementation stopped at 1152px and began at x=136.5px. This introduced approximately 96px of excess margin on each side at the 1440px desktop viewport and made the two-tier header visibly narrower than the content below it.
2. The black application bar was measured before changing it. Both implementations were already exactly 60px tall, and both brand links measured 179.94–179.97px by 35px at x=16px and y=12.5px. Reducing the bar would have created new drift, so its dimensions were retained.
3. Final correction: the desktop search maximum is now 1344px with the same `calc(100% - 80px)` outer-margin behavior as the source. The corrected implementation measures 1344px at x=40.5px, with a 42px search control inside the unchanged 62px search row. No actionable P0, P1, or P2 header-spacing finding remains.

### Required fidelity review

- Fonts and typography: the header uses the existing Terraform-compatible UI family, weight, line height, and truncation behavior. The change does not alter text rendering.
- Spacing and layout rhythm: the 60px application bar, 62px search row, 42px search control, 40.5px desktop margins, and 16px responsive margins now match their intended measured geometry.
- Colors and visual tokens: the black application bar, neutral search surface, borders, foregrounds, and focus treatment are unchanged and remain aligned with the source palette.
- Image quality and asset fidelity: the existing Registry mark remains a sharp source asset with unchanged 23 × 26px geometry; no synthetic asset was introduced.
- Copy and content: the private Registry search placeholder and authenticated controls are intentional product differences. Search behavior and catalog content are unchanged.
- Responsiveness and accessibility: the existing 800px media override continues to provide a 358px search control with 16px margins at a 390px viewport. The native search input, clear control, keyboard shortcut indicator, and labels remain intact.

### Functional and quality verification

- Browser DOM geometry was measured directly after the production image rebuild: official and local desktop search forms both resolve to x=40.5px, width=1344px, height=42px.
- The desktop focused crop was required because the exact horizontal alignment is the primary fidelity surface and is difficult to judge from the scaled full-page comparison alone.
- Browser console warnings and errors: none observed during the comparison.
- The frontend quality suite, production build, authenticated Compose rebuild, and health checks passed.

## Dark-mode package metadata contrast QA — 2026-07-23

### Comparison target

- Source issue: `D:/Temp/codex-clipboard-d04fa8c8-ad51-4cfe-92b4-39f7c795b57b.png`.
- Corrected implementation: `D:/Temp/registry-dark-contrast-audit/02-corrected-dark-normalized.png`.
- Equal-size full-view comparison: `D:/Temp/registry-dark-contrast-audit/03-before-after-full.png`.
- Focused package-header comparison: `D:/Temp/registry-dark-contrast-audit/04-before-after-focused.png`.
- The source and normalized implementation captures are both 1082 × 445 pixels. The implementation came from a 1097 × 445 CSS viewport at device scale 1; the six missing bottom pixels in the browser capture were extended with the unchanged page surface without resizing the rendered page.
- State: authenticated local Registry administrator, IAM module version 6.2.1, dark theme, top-of-page scroll position, production Docker image. The equivalent AzureRM provider metadata and light-theme module state were also checked.

### Findings and comparison history

1. Baseline P1: the module description inherited a light-theme `rgb(79, 85, 97)` foreground on the `rgb(21, 22, 26)` dark surface, producing only 2.41:1 contrast. Strong metadata values used `rgb(70, 75, 86)` and produced only 2.07:1 contrast.
2. Final correction: package descriptions and metadata labels now use the dark-theme muted token `rgb(168, 173, 183)` for 8.03:1 contrast. Strong values use the dark-theme text token `rgb(213, 215, 219)` for 12.55:1 contrast. Links retain their existing `rgb(118, 167, 255)` treatment at 7.53:1.
3. The same token correction applies to provider package facts so the module and provider detail headers remain visually consistent. The light-theme values remain byte-for-byte unchanged.
4. No actionable P0, P1, or P2 finding remains.

### Required fidelity review

- Fonts and typography: font family, size, weight, and line height are unchanged; the fix changes only dark-theme foreground and border tokens.
- Spacing and layout rhythm: header geometry, facts-row alignment, tabs, breadcrumbs, and version control dimensions are unchanged.
- Colors and contrast: description and secondary labels now meet WCAG AA and AAA contrast for normal text; primary values and links also exceed AAA contrast.
- Image quality and asset fidelity: provider and module marks are unchanged and remain sharp at their existing geometry.
- Copy and content: package description, source, provider, download, version, publisher, and manager text are unchanged.
- Responsiveness and accessibility: the fix uses existing semantic theme tokens, preserving the same responsive wrapping and focus behavior in both themes.

### Functional and quality verification

- Theme switching was exercised in the browser. Returning to light mode restored the original description `rgb(79, 85, 97)`, label `rgb(115, 120, 132)`, and value `rgb(70, 75, 86)` colors.
- The AzureRM provider page was checked in dark mode: secondary facts resolve to `rgb(168, 173, 183)`, primary values to `rgb(213, 215, 219)`, and links to `rgb(118, 167, 255)`.
- Browser console errors checked during module/provider navigation and theme toggles: none observed.
- Frontend verification: all 70 tests passed.
- Static quality: Biome, strict TypeScript, typed ESLint with zero warnings, Stylelint, dependency-cruiser, and Knip passed.
- Delivery quality: production Vite build and both JavaScript bundle budgets passed (168.9kB initial JavaScript; 237.13kB total JavaScript).
- The preview and primary Compose UI containers were rebuilt from the corrected production image and reached healthy state.
