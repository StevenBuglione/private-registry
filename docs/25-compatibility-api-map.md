# OpenTofu UI Compatibility API Map

The UI calls one same-origin hostname. The ALB routes compatibility and enterprise paths to the API service.

| UI need | Route family | Backing data |
|---|---|---|
| search | `GET /registry/docs/search` | OpenSearch plus visibility filters |
| module list | `GET /registry/docs/modules/index.json` | Aurora |
| module package/versions | `GET /registry/docs/modules/{namespace}/{name}/{target}/index.json` | Aurora |
| module version metadata | `GET .../{version}/index.json` | Aurora/S3-derived structured metadata |
| module README | `GET .../{version}/README.md` | S3 |
| module submodule/example docs | `GET .../{version}/modules|examples/.../README.md` | S3 |
| provider list | `GET /registry/docs/providers/index.json` | Aurora |
| provider package/versions | `GET /registry/docs/providers/{namespace}/{name}/index.json` | Aurora |
| provider version metadata | `GET .../{version}/index.json` | Aurora/S3-derived structured metadata |
| provider overview | `GET .../{version}/index.md` | S3 |
| resource/data-source/function/guide docs | `GET .../{version}/{kind}/{document}.md` | S3 |
| recommended providers | `GET /top/providers` | enterprise ranking derived from approved catalog/authorized usage aggregates |
| governance/security/ownership/audit | `/api/v1/enterprise/packages/{id}/...` | Aurora/S3/authorized integrations |

## Compatibility rules

- Preserve response shapes expected by the pinned UI, including omission/null behavior.
- Return versioned immutable documents.
- Apply visibility and field authorization before search/list/detail results.
- Use stable package identifiers independent of mutable display names.
- Serve Markdown as `text/markdown; charset=utf-8` with path validation.
- Do not return S3/JFrog credentials or package download bytes.
- Contract-test every route with captured UI fixtures before an upstream UI update.

The scaffold OpenAPI is an initial enterprise contract, not proof of byte-for-byte compatibility. During the UI fit spike, capture the pinned UI's exact requests/responses and update the contract/tests before production coding.
