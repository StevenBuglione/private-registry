# Upstream integration checklist

- [ ] Runtime config is loaded before API clients are created.
- [ ] Standard registry requests use `runtimeConfig.dataApiUrl`.
- [ ] Enterprise metadata requests use `runtimeConfig.enterpriseApiUrl`.
- [ ] Home/search/module/provider/version/docs routes still work.
- [ ] Package pages render governance badges and generated source snippets.
- [ ] Public submission, popularity, donation, and community-only behavior is removed or replaced.
- [ ] Public provider addresses remain their original addresses.
- [ ] Markdown is sanitized and external links use safe attributes.
- [ ] UI does not display restricted fields returned for another role.
- [ ] Loading, empty, unauthorized, deprecated, revoked, and API outage states are designed.
- [ ] Keyboard, screen-reader, zoom, contrast, and reduced-motion checks pass.
- [ ] Open-source notices and source-availability process are approved.
