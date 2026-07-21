# ADR: Managed catalog data services

- Status: Accepted for blueprint
- Date: 2026-07-21

## Decision

Use Aurora for authoritative catalog metadata, S3 for normalized documentation/evidence, and OpenSearch as a rebuildable search index.

## Consequences

The implementation and operational model must preserve this boundary unless a replacement ADR is approved.
