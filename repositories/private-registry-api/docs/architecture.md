# API architecture

## Request path

```text
Browser -> OIDC/session -> Registry API -> PostgreSQL
                                  |
                                  +-------> JFrog Artifactory (ingestion only)
```

PostgreSQL is the Registry's only stateful application dependency. Every catalog query applies the caller's APM authorization in SQL; the UI is never trusted to hide protected data. JFrog remains authoritative for governed package bytes and checksums, but an Artifactory outage does not prevent catalog reads.

## Search

Package search uses PostgreSQL generated `tsvector` content with a GIN index and `pg_trgm` identity matching. Search results are joined to active versions and APM grants before package identifiers are returned. There is no separately synchronized search index.

## Event path

```text
JFrog webhook -> signed intake -> catalog_event_queue -> NOTIFY -> Java worker
Java worker -> JFrog validation -> PostgreSQL transaction -> NOTIFY -> authorized SSE
```

The webhook transaction inserts an idempotent JSON event into
`catalog_event_queue` and returns `202` only after the database accepts it.
Commit-time `NOTIFY` wakes a virtual-thread listener immediately, while a
30-second scheduled poll remains the durability fallback. Workers claim batches
with `FOR UPDATE SKIP LOCKED`, retry transient failures with bounded backoff,
recover stale claims, expire terminal records according to retention policy, and
move terminal failures into the `catalog_event_dead_letters` view. The same API
image runs request handling, event processing, and reconciliation.

## Documents and reconciliation

Validated UTF-8 documentation is stored in `documentation_pages.content` in the same transaction as package metadata and symbols. The reconciler enumerates ready JFrog manifests every 15 minutes and performs a nightly full comparison. The Compose seeder performs a full reconciliation before exiting, which also repairs installations upgraded from the earlier object-store design.

## Deployment units

- `registry-api`: stateless Java 25 application instances; each can process database queue work.
- `registry-web`: static first-party UI that proxies API requests.
- PostgreSQL: authoritative catalog, search, documents, event queue, DLQ, audit, and notification state.
- `catalog-migrations`: the API image run as a one-off migration task where production policy requires it.
