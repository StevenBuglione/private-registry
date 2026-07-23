# Compatibility API map

| UI behavior | Java endpoint | Authoritative adapter |
|---|---|---|
| session and APM memberships | `GET /api/v1/auth/session` | Entra/Graph plus server-side session |
| search/list/facets | `GET /api/v1/catalog/packages` | authorized PostgreSQL full-text/trigram query |
| package/version details | package summary/version routes | PostgreSQL |
| documentation | package documentation routes | PostgreSQL validated content |
| governance | package governance route | PostgreSQL |
| live invalidation | `GET /api/v1/catalog/events` | PostgreSQL `LISTEN`/`NOTIFY` plus authorization |
| JFrog change intake | `POST /internal/webhooks/jfrog` | signature validation plus PostgreSQL durable queue |

Unauthorized and nonexistent package routes return the same 404 response. The browser never receives Graph tokens, JFrog credentials, or unauthorized metadata.
