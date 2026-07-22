# API and Worker Design

## Compatibility API

Keep the standard UI surface close to the upstream contract:

```text
GET /registry/docs/modules/index.json
GET /registry/docs/modules/{namespace}/{name}/{target}/index.json
GET /registry/docs/modules/{namespace}/{name}/{target}/{version}/index.json
GET /registry/docs/modules/{namespace}/{name}/{target}/{version}/README.md
GET /registry/docs/modules/{namespace}/{name}/{target}/{version}/modules/{submodule}/README.md
GET /registry/docs/modules/{namespace}/{name}/{target}/{version}/examples/{example}/README.md
GET /registry/docs/providers/index.json
GET /registry/docs/providers/{namespace}/{name}/index.json
GET /registry/docs/providers/{namespace}/{name}/{version}/index.json
GET /registry/docs/providers/{namespace}/{name}/{version}/index.md
GET /registry/docs/providers/{namespace}/{name}/{version}/{kind}s/{document}.md
GET /registry/docs/search
GET /top/providers
```

The API translates this contract to Aurora, S3, and OpenSearch.

## Enterprise API

```text
GET /api/v1/enterprise/packages/{id}
GET /api/v1/enterprise/packages/{id}/governance
GET /api/v1/enterprise/packages/{id}/approvals
GET /api/v1/enterprise/packages/{id}/security
GET /api/v1/enterprise/packages/{id}/owners
GET /api/v1/enterprise/packages/{id}/dependencies
GET /api/v1/enterprise/packages/{id}/audit
GET /api/v1/enterprise/packages/{id}/jfrog
```

Administrative writes, if later required, use separate routes, stronger roles, CSRF protection, explicit audit records, and change-control approval. Version one should remain read-oriented.

## API requirements

- OpenAPI 3 contract;
- pagination on collections;
- stable error schema and correlation IDs;
- ETag/cache-control where safe;
- timeouts and circuit breakers for JFrog/S3/OpenSearch;
- no package download endpoint;
- no secret exposure;
- visibility-aware search;
- ALB identity assertion verification;
- server-side authorization;
- structured JSON logs and OpenTelemetry.

## Adapters

```text
server handlers
  -> catalog service
     -> Aurora repository
     -> OpenSearch repository
     -> S3 document repository
     -> JFrog reader
```

Workers share domain contracts but not HTTP handler code.

## Health endpoints

- `/health/live`: process is running; no dependency calls.
- `/health/ready`: required dependencies reachable within a bounded timeout.
- `/health/startup`: migrations/configuration complete where platform probes require it.

## Error behavior

- 400 invalid request;
- 401 missing/invalid identity assertion;
- 403 authorized identity lacks permission;
- 404 package absent or intentionally hidden;
- 409 inconsistent/reconciling state where exposure is safe;
- 429 rate limited;
- 503 required dependency unavailable.

Do not distinguish hidden from nonexistent packages to unauthorized users.
