**Source visual truth**

- Primary references: `.codex-artifacts/terraform-home-desktop-top.png`, `terraform-browse-providers-1440.png`, `terraform-provider-detail-1440.png`, and `terraform-provider-docs-1440.png`.
- Responsive references: `.codex-artifacts/terraform-home-mobile-top.png`, `terraform-home-mobile-menu.png`, `terraform-browse-providers-mobile-filter.png`, and `terraform-provider-docs-mobile.png`.
- The screenshots are structural references only. Terraform and HashiCorp marks, names, product copy, public counts, and unauthenticated controls are intentionally replaced with Registry identity, governed catalog data, and enterprise identity controls.

**Verified implementation evidence**

- Side-by-side source/local comparisons were inspected at original size for home, provider catalog, provider detail, and mobile navigation.
- Authenticated local captures: `.codex-artifacts/local-home-desktop-final.png`, `local-providers-1440-final.png`, `local-provider-detail-1440-final.png`, `local-home-mobile-final.png`, `local-home-mobile-menu-final.png`, and `local-provider-detail-mobile-final.png`.
- Responsive browser inspection passed at 1440, 1024, 768, and 390 CSS pixels. Every inspected page reported no horizontal overflow.
- Real Entra administrator OIDC sign-in and Graph-backed session recovery passed after service restarts.
- Global authorized suggestions returned the governed AWS provider and only matching authorized modules.
- Provider/module details, version selection, documentation navigation, legacy singular redirects, Artifactory copy actions, private 404 behavior, filter drawer, and mobile menu passed.
- Clipboard inspection proved the copied provider instructions contain the governed JFrog repository URL and SHA-256 verification command.
- Browser console warning/error scan returned zero entries.
- Runtime catalog showed truthful 12-provider/30-module counts from the real JFrog seed.

**Comparison history and fixes**

- Replaced the original custom visual direction with the Terraform Registry's black header, full-width search, announcement band, centered hero, compact tabs, filter rail/drawer, provider grid, module rows, and package documentation layout.
- Replaced source branding and public-marketplace actions with the Registry emblem, Entra identity, access context, governed counts, lifecycle/risk metadata, and Artifactory instructions.
- Fixed the mobile navigation from a white dropdown to the source-like full black navigation surface.
- Fixed a tablet/mobile package-detail overflow caused by the long immutable Artifactory URL; the code panel now remains within its grid/card at 768 and 390 pixels.
- Preserved a usable true-responsive mobile layout where the reference site itself renders a desktop-width surface at 390 pixels.

**Final findings**

- No open P0, P1, or P2 visual or interaction defects.
- APM-A/APM-B authorization permutations remain covered by backend/UI automated tests; the browser pass used the real Entra administrator session and did not expose or reuse test-user passwords.

final result: passed
