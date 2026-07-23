# Service contracts

## `registry-web`

- Serves the first-party React application.
- Proxies same-origin API traffic.
- Contains no service credentials and performs no security filtering on behalf of the API.

## `registry-api`

- Handles OIDC sessions, Graph entitlements, authorized catalog reads, governance, admin settings, and SSE.
- Queries PostgreSQL directly for metadata, documentation, and search.
- Accepts signed JFrog webhooks into the PostgreSQL event queue.
- Runs queue consumers and scheduled reconciliation in the same Spring Modulith process.
- Uses the official JFrog Artifactory Java Client to validate current artifact state before activation.

## PostgreSQL

- Is the only stateful application dependency.
- Owns catalog, APM grants, documentation, search vectors, incoming events, attempts, dead letters, ingestion/audit history, reconciliation checkpoints, and homepage settings.
- Uses durable tables for truth and `LISTEN`/`NOTIFY` only for low-latency signals.

## JFrog

- Owns immutable package bytes, checksums, governed repositories, and promotion properties.
- Remains independently usable if the catalog is unavailable.
