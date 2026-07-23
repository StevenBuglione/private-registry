# ADR: EventBridge and SQS ingestion

- Status: Superseded by ADR 0007
- Date: 2026-07-21

## Decision

Publish versioned promotion events to EventBridge and buffer processing with SQS/DLQ; reconciliation repairs event loss.

## Consequences

The implementation and operational model must preserve this boundary unless a replacement ADR is approved.
