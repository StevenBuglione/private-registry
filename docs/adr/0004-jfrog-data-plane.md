# ADR: JFrog as package data plane

- Status: Accepted for blueprint
- Date: 2026-07-21

## Decision

JFrog remains authoritative for package bytes and CLI registry protocols; the catalog never proxies package downloads.

## Consequences

The implementation and operational model must preserve this boundary unless a replacement ADR is approved.
