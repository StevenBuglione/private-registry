# ADR: Active/passive catalog DR

- Status: Accepted for blueprint
- Date: 2026-07-21

## Decision

Use cross-Region active/passive recovery to avoid multi-writer catalog conflicts; package availability remains separately owned by JFrog.

## Consequences

The implementation and operational model must preserve this boundary unless a replacement ADR is approved.
