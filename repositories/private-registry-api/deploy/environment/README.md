# Process environment templates

These templates document the environment contract for each deployable process. Copy the
relevant file into the deployment system; do not source every template into every process.

| Template | Process | Database role |
|---|---|---|
| `api.env.example` | Public Spring MVC API | `registry_web` |
| `indexer.env.example` | Private ingestion/reconciliation worker | `registry_indexer` |
| `migrations.env.example` | One-shot Flyway migration | schema owner |
| `seeder.env.example` | One-shot JFrog repository/catalog bootstrap | `registry_indexer` |
| `ui.env.example` | Nginx runtime configuration | none |

Values ending in `_PASSWORD`, `_SECRET`, `_CLIENT_SECRET`, or `_ACCESS_TOKEN` must be
provided from the deployment platform's secret store. The application reads secret
**values**, not AWS Secrets Manager ARNs.

The current AWS Terraform module is a scaffold and does not yet satisfy this contract.
Read [`../../../../docs/32-deployment-readiness-audit.md`](../../../../docs/32-deployment-readiness-audit.md)
before deploying it.
