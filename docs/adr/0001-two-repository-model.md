# ADR: Two production repositories

- Status: Accepted for blueprint
- Date: 2026-07-21

## Decision

Use separate UI and API repositories. Terraform lives in the API repository to avoid a third production repository.

## Consequences

The implementation and operational model must preserve this boundary unless a replacement ADR is approved.
