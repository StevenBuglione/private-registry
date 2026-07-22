# ADR: ECS Fargate for application compute

- Status: Accepted for blueprint
- Date: 2026-07-21

## Decision

Use ECS Fargate for UI, API, indexer, reconciler, and migrations instead of operating an EKS cluster for five services.

## Consequences

The implementation and operational model must preserve this boundary unless a replacement ADR is approved.
