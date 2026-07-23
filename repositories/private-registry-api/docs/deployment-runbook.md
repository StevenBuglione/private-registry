# Deployment runbook

## Prerequisites

- approved runtime network, PostgreSQL database, ingress, and container platform;
- OIDC client/issuer/scopes and Entra group mappings;
- JFrog endpoint, private connectivity where required, and read-only runtime secret;
- protected CI environment and immutable image registry;
- approved PostgreSQL backup and restore policy.

> **Production status:** this component runbook describes the target operating sequence.
> The checked-in AWS application-service Terraform is not deployable unchanged. Review
> the repository-level `docs/32-deployment-readiness-audit.md` first.

## Data initialization

1. Build and publish the API image by digest.
2. Run Flyway migrations with the approved migration identity.
3. Store JFrog and OIDC values in the platform secret manager.
4. Verify TLS connectivity to PostgreSQL and JFrog.
5. Run a full reconciliation to populate catalog metadata and documentation.

## Service rollout

Deploy the UI, public API, and private indexer as distinct processes. The API uses
`registry_web` with webhook intake enabled and ingestion disabled. The indexer uses
`registry_indexer`, has ingestion enabled, and is not attached to the public load
balancer. Both Java processes may use the same immutable image digest with different
environment and commands. Wait for `/health/ready`; use indexer process health,
queue/reconciliation state, and logs to verify JFrog-dependent processing separately.
The production readiness audit requires a PostgreSQL-backed worker heartbeat before
production rollout.

## Verification

- authenticated portal login and real Graph membership;
- PostgreSQL-only API readiness;
- signed fixture webhook enters and leaves `catalog_event_queue`;
- terminal fixture failure appears in `catalog_event_dead_letters`;
- package is searchable and its documentation renders;
- unauthorized users cannot receive restricted metadata;
- SSE reflects activation without a page reload;
- rollback to the previous image succeeds.

## Rollback

Application rollback uses the previous image digest. Database migrations must be forward-compatible; destructive rollback requires an approved PostgreSQL restore. Do not remove governed JFrog releases during application rollback.
