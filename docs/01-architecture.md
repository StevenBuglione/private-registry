# Architecture

```text
Browser -> Registry UI -> Java 25 Spring Modulith -> PostgreSQL
                                  |                    |
                                  |                    +-- catalog/search/documents
                                  |                    +-- event queue/retries/DLQ
                                  |                    +-- audit/settings/NOTIFY
                                  +-> JFrog Java Client -> governed artifacts
```

The browser authenticates through Entra OIDC and receives only a secure server-side session. Every catalog query is filtered in PostgreSQL using the principal's APM `AccessContext`.

JFrog is authoritative for immutable provider/module artifacts, checksums, and governance properties. PostgreSQL is authoritative for every application projection and workflow state. The API remains readable during a JFrog outage.

Signed webhook events are inserted into `catalog_event_queue`. Horizontally scaled Java workers use `FOR UPDATE SKIP LOCKED`, then validate current JFrog state and activate catalog rows transactionally. Scheduled reconciliation repairs missed events, while the Compose seeder reconciles before exiting. PostgreSQL notifications feed authorized SSE refresh without becoming a durability dependency.

One API image contains HTTP, worker, and reconciliation modules. Local Compose adds only PostgreSQL and the UI.
