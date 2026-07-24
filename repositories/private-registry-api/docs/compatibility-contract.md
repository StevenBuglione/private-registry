# Registry API contract

The first-party UI consumes only the versioned, same-origin Registry API. The internal database and JFrog models stay behind explicit response DTOs.

Primary interfaces:

- `GET /api/v1/auth/session`
- `POST /api/v1/auth/logout`
- `GET /api/v1/catalog/packages`
- `GET /api/v1/catalog/packages/{kind}/{namespace}/{name}[/{target}][/{version}]`
- version, documentation, and governance subresources beneath the package route
- `GET /api/v1/catalog/events`
- `POST /api/v1/internal/webhooks/jfrog`

Contract rules:

- Every catalog call requires an `AccessContext` and applies APM authorization before projection.
- Unauthorized, revoked, and nonexistent package/version routes return the same 404 response.
- Search, counts, details, versions, documentation, governance, and SSE are authorization filtered.
- Pagination cursors are opaque, deterministic, and tied to the selected sort.
- Markdown is served from normalized immutable documentation with path validation.
- Browser responses never contain OAuth/Graph tokens, JFrog credentials, AWS credentials, or package bytes.
- Contract and browser tests must pass before a response shape changes.
