# Database design

PostgreSQL is the Registry's only application-state boundary. JFrog remains
authoritative for immutable package bytes; PostgreSQL owns searchable metadata,
authorization, documentation, event delivery, reconciliation, administration,
traffic reporting, and audit history.

## Normalized ownership

| Concern | Authoritative relation | Important dependents |
| --- | --- | --- |
| Package identity | `packages` | versions, owners, APM access, categories, homepage features |
| Immutable releases | `package_versions` | documents, symbols, approvals, download snapshots |
| APM vocabulary | `apm_contexts` | group entitlements and package access |
| Browse taxonomy | `registry_categories` | `package_categories` |
| Homepage curation | `registry_homepage_features` | singleton presentation settings |
| Authenticated visitors | `registry_traffic_identities` | immutable page views |
| Durable delivery | `catalog_event_queue` | ingestion journal and quarantine records |

Every many-to-many association has its own junction table and foreign keys.
Ordered associations use a scoped unique position. A package version referenced
by a lifecycle event must belong to the same package. Homepage feature kind must
match package kind.

Migration V120 removes the legacy homepage CSV columns, package category array,
and duplicated page-view identity fields after V119 has copied their data into
the normalized relations. Runtime writes now target only those authoritative
relations.

## State invariants

PostgreSQL rejects:

- active revoked versions;
- negative or duplicate owner positions;
- reconciliation counts where repaired work exceeds discrepancies;
- terminal queue or ingestion rows without completion timestamps;
- completed ingestion without a verified package digest;
- processing queue rows without a claim timestamp;
- unsafe or missing symbol document paths;
- input-only metadata on unrelated symbol kinds;
- unknown or duplicate browse categories;
- homepage links with only a label or only a URL; and
- APM references outside the normalized APM vocabulary.

Historical rows are repaired before each new constraint is validated. Released
migrations are never edited.

## Search and pagination

Authorized search is one PostgreSQL query. It combines a generated weighted
`tsvector`, GIN full-text search, trigram identity matching, stable ranking, and
the caller's APM predicate before returning identifiers. Cursor values encode the
sort tuple; no separately synchronized search service or capped ID pre-query is
used. Download sorting aggregates the latest JFrog counter snapshot per active
version as a set projection only when that sort is requested.

## Event delivery

`catalog_event_queue` is the durable source of truth. A commit-time
`LISTEN`/`NOTIFY` signal wakes a worker immediately; a 30-second poll is the
fallback. Claims use `FOR UPDATE SKIP LOCKED`.

Two unique identities prevent duplicate work:

- the transport `event_id`; and
- a SHA-256 semantic key over schema version, action, repository, path,
  occurrence time, and sorted properties.

Every processing claim has a unique lease token. Failed and abandoned claims can
be reclaimed, but an old claimant cannot complete or retry the replacement
lease. Reusing a transport ID with a changed semantic key is rejected; reusing
the semantic key with a new transport ID remains idempotent. Completed rows,
dead letters, ingestion journal rows, and quarantine records use explicit
retention windows. Malformed legacy and runtime payloads are terminally
dead-lettered so they cannot poison ordered queue consumption.

## Database authority

Flyway connects as the schema owner only in a one-shot migration container.
The one-shot role bootstrap assigns local development passwords without
committing them to a migration or application image. Compose then splits the
runtime into two Spring Modulith processes. The public API authenticates
directly as `registry_web`,
which can read the catalog and write only audit, analytics, homepage settings,
synchronization credentials, and incoming event rows. The unexposed worker
assumes `registry_indexer` for catalog, entitlement bootstrap, and ingestion
DML. Local acceptance therefore exercises production-shaped grants rather than
bypassing them accidentally.

Production should use separately managed credentials for migration and each
runtime role; the application processes never receive the migration credential.
`registry_app` remains the strictly read-only role for reporting and diagnostic
consumers.
