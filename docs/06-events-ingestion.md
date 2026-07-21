# Events, Ingestion, and Reconciliation

## Event contract

All events use EventBridge with:

```text
source: private-registry.release
detail-type: PackagePromoted | PackageDeprecated | PackageRevoked
detail.schemaVersion: 1
```

The event includes package identity, version, JFrog repository/path, package digest, manifest location/digest, publication timestamp, source commit, and correlation ID.

## Ingestion queue

Use a standard SQS queue because ordering across unrelated packages is unnecessary. Idempotency handles duplicate delivery. Configure:

- long polling;
- KMS encryption;
- visibility timeout greater than maximum expected processing time;
- message retention long enough for incident recovery;
- DLQ with a higher retention period;
- alarm on any DLQ message;
- alarm on oldest message age and backlog.

## Idempotency

Recommended key:

```text
{kind}/{namespace}/{name}/{target-or-provider}/{version}/{packageDigest}
```

The indexer inserts an ingestion record with a unique constraint before side effects. Replays either resume an incomplete state machine or return success for an already completed event.

## Processing state machine

```text
received
 -> validated
 -> jfrog_verified
 -> documentation_downloaded
 -> normalized
 -> authoritative_data_committed
 -> search_indexed
 -> completed
```

Failures record stage, reason, retryability, correlation ID, and evidence location. Do not delete the queue message until completion.

## Security controls

- validate JSON Schema before network access;
- reject unexpected repositories and hostnames;
- verify package and manifest digests;
- enforce archive/file limits;
- reject symlinks and traversal;
- sanitize Markdown;
- never execute package examples;
- use a read-only JFrog credential;
- use quarantine for failed or suspicious content.

## Reconciliation

Incremental reconciliation runs every 15 minutes and full reconciliation runs nightly. It compares:

```text
JFrog package versions
JFrog documentation bundles
Aurora package versions
S3 normalized documents
OpenSearch records
```

Detect:

- JFrog versions missing from catalog;
- catalog versions missing from JFrog;
- digest mismatch;
- missing or invalid documentation;
- missing owner/approval/security metadata;
- stale latest-version selection;
- missing or extra search records;
- revoked versions still searchable;
- broken source/support links.

Default mode is report-only. Repair mode is an explicit operator action with change record and immutable audit output.
