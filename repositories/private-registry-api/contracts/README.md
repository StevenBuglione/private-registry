# Versioned contracts

These contracts are copied into `private-registry-api/contracts/` and are the shared source of truth for package releases, EventBridge events, ingestion, reconciliation, and catalog display.

## Compatibility rules

- Never change the meaning of an existing field in-place.
- Add optional fields for backward-compatible revisions.
- Increment `schemaVersion` for breaking changes.
- Consumers must reject unsupported major schema versions and quarantine invalid events.
- A package version is visible only after package bytes, documentation, manifest, approval evidence, and the completion marker exist.
- `packageDigest` and `documentationDigest` are immutable identity evidence.

## Files

- `package-manifest.schema.json` — module/provider release manifest.
- `package-promoted-event.schema.json` — release visibility event.
- `package-deprecated-event.schema.json` — lifecycle transition.
- `package-revoked-event.schema.json` — emergency removal from recommendations.
- `examples/` — non-sensitive examples used by tests and implementation agents.
