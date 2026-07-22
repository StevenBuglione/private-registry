# Registry API map

The UI calls one same-origin hostname. Nginx proxies API, OAuth, login, logout, and webhook paths to the Java application.

| UI need | Route | Backing data |
|---|---|---|
| session and entitled APMs | `GET /api/v1/auth/session` | Entra OIDC session + Graph membership cache |
| logout | `POST /api/v1/auth/logout` | Spring session + Entra logout target |
| search/list/facets | `GET /api/v1/catalog/packages` | PostgreSQL authorization + OpenSearch projection |
| package summary | `GET /api/v1/catalog/packages/{kind}/{namespace}/{name}[/{target}][/{version}]` | PostgreSQL |
| versions | `.../versions` | PostgreSQL |
| documentation | `.../documentation` | S3-normalized documentation |
| governance | `.../governance` | PostgreSQL |
| live updates | `GET /api/v1/catalog/events` | PostgreSQL activation notification + SSE |
| JFrog change intake | `POST /internal/webhooks/jfrog` | signature validation + EventBridge |

Every catalog route resolves an `AccessContext` first. Unauthorized metadata is removed before response construction; direct inaccessible routes return the same 404 as nonexistent records.
