# UI repository instructions

1. Keep the application first-party and product-neutral: the runtime product name is `Registry`.
2. Work in `web/`; do not introduce an upstream clone, patch layer, or build-time source download.
3. Treat the Java API as the authorization boundary. The UI may select an APM context but must not attempt client-side security filtering.
4. Use same-origin `/api/v1`, `/oauth2`, `/login`, and `/logout` routes. The browser must never receive Microsoft Graph or Artifactory credentials.
5. Use the generated assets in `public/assets/`; do not replace the Registry mark with CSS or handcrafted SVG art.
6. Keep Markdown sanitized and do not load fonts, logos, avatars, scripts, or styles from third-party origins at runtime.
7. Preserve loading, empty, no-entitlement, session-expired, identity-outage, revoked, not-found, and API-outage states.
8. Run lint, unit/a11y tests, production build, runtime template validation, and container build before release.
