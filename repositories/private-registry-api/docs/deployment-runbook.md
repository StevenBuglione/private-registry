# Deployment runbook

## Prerequisites

- approved runtime network, PostgreSQL database, ingress, and container platform;
- OIDC client/issuer/scopes and Entra group mappings;
- JFrog endpoint, private connectivity where required, and read-only runtime secret;
- protected CI environment and immutable image registry;
- approved PostgreSQL backup and restore policy.

## Data initialization

1. Build and publish the API image by digest.
2. Run Flyway migrations with the approved migration identity.
3. Store JFrog and OIDC values in the platform secret manager.
4. Verify TLS connectivity to PostgreSQL and JFrog.
5. Run a full reconciliation to populate catalog metadata and documentation.

## Service rollout

Deploy the UI and API images. The API image contains request handling, database event workers, and reconciliation. Wait for `/health/ready`; use `/health/worker` to verify JFrog-dependent processing separately.

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
