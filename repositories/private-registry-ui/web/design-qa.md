**Source visual truth**

- Live source: `https://registry.terraform.io/providers/hashicorp/azurerm/latest` captured on July 22, 2026.
- Source screenshots:
  - `D:/Users/steve/private-registry/.codex-artifacts/azurerm-parity-20260722/01-official-overview.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/azurerm-parity-20260722/03-official-documentation-index.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/azurerm-parity-20260722/04-official-resource-group.png`
- The source is a structural and interaction reference. Terraform/HashiCorp marks, public download statistics, public authentication controls, and public-marketplace copy are intentionally replaced by Registry identity, governed private counts, Entra identity, APM authorization, and JFrog-backed package metadata.

**Implementation evidence**

- Local implementation: `http://localhost:3000/providers/hashicorp/azurerm/4.37.0`
- Final screenshots:
  - `D:/Users/steve/private-registry/.codex-artifacts/azurerm-parity-20260722/13-final-overview.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/azurerm-parity-20260722/14-final-documentation-index.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/azurerm-parity-20260722/15-final-resource-group.png`
- Browser viewport: 1280 x 720 CSS pixels at device pixel ratio 1 for both source and implementation. Captured page pixels are 1265 x 712 for both sides; no density normalization was required.
- Final side-by-side comparison evidence:
  - `D:/Users/steve/private-registry/.codex-artifacts/azurerm-parity-20260722/16-overview-side-by-side-final.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/azurerm-parity-20260722/17-docs-side-by-side-final.png`
  - `D:/Users/steve/private-registry/.codex-artifacts/azurerm-parity-20260722/18-resource-side-by-side-final.png`
- States compared: provider Overview, provider Documentation index, filtered `resource group` results, and selected `azurerm_resource_group` documentation.
- Focused crops were not needed because three state-specific, original-size comparisons make the header, rail, article, table of contents, metadata, and callout details readable without scaling.

**Comparison history**

- Initial P1: the Overview used a compressed identity block, oversized two-column module cards, and a long raw Artifactory download script where the source uses a dense module list and compact supporting rail.
  - Fix: matched the source header height, action placement, metadata rhythm, lifecycle pill, tab position, one-column module rows, helpful-links rail, provider-version panel, and concise Terraform configuration panel while retaining truthful private metadata.
  - Post-fix evidence: `16-overview-side-by-side-final.png`.
- Initial P1: all documentation symbols were permanently expanded, making the provider navigator materially denser and harder to scan than the source.
  - Fix: added collapsible document groups, automatic filtered expansion, full provider-prefixed resource names, matching-result counts, selected state, and a sticky navigation header/filter.
  - Post-fix evidence: `17-docs-side-by-side-final.png` and `18-resource-side-by-side-final.png`.
- Initial P1: source admonitions rendered as literal `-> Note:` paragraphs and the page title was incorrectly repeated in the table of contents.
  - Fix: added source-like information/warning callouts, heading permalinks, code-copy controls, and an H2/H3-only table of contents.
  - Post-fix evidence: `18-resource-side-by-side-final.png`.
- Initial P2: documentation article and right-rail proportions drifted from the source.
  - Fix: normalized the detail container and documentation grid to the source geometry at the comparison viewport.
  - Post-fix evidence: all three final comparisons.

**Required fidelity surfaces**

- Fonts and typography: both sides use compact sans-serif UI type with matching hierarchy, weights, line heights, link treatment, code styling, and readable truncation. Dynamic private copy is shorter in the header but retains the source hierarchy.
- Spacing and layout rhythm: header, tabs, documentation rail, article start, table-of-contents rail, module list, borders, radii, and above-the-fold density align closely in the final comparisons.
- Colors and tokens: black shell, white package header, light-gray content surface, blue active/link states, muted metadata, and restrained borders map to the source. Private approval and risk content uses existing semantic tokens.
- Image quality and asset fidelity: the real AzureRM provider asset is used at the source-like scale. UI icons come from the existing Phosphor icon library; no CSS art, text glyph, placeholder image, or handcrafted SVG was introduced.
- Copy and content: package versions, counts, owners, lifecycle, risk, APM access, source repository, approved modules, and Artifactory path are backend-derived private data. No public download statistic or official-provider claim is fabricated.
- Behavior and accessibility: version switching preserves the active documentation query, documentation groups expand/collapse, filtering exposes provider-prefixed resources, direct resource selection loads real Markdown, copy buttons report `Copied`, heading links and table-of-contents links work, controls have accessible names, and the updated unit test passes axe checks.

**Residual P3 differences**

- The private header has Entra/APM controls instead of Terraform's Publish/Sign In actions.
- Governed package metadata replaces public download panels and counts.
- The mirrored AzureRM version is 4.37.0 while the live public source capture is 4.81.0.

These are required product and data differences, not visual defects.

**Verification**

- `npm run lint`: passed.
- `npm test`: 13 tests passed.
- `npm run build`: passed.
- Docker Compose UI rebuild and health wait: passed.
- In-app browser: Overview, Documentation, filtered resource navigation, selected resource content, copy success, group collapse, and version switching verified.
- Browser DOM inspection confirmed real `azurerm_resource_group` arguments, attributes, timeouts, import content, two rendered Note callouts, and a title-free table of contents.

final result: passed
