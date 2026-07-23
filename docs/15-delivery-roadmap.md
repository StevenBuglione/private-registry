# Delivery roadmap

> Historical blueprint. Runtime service topology in this document is superseded by ADR 0007 and the executable implementation status.

## Starter acceptance

- Build and test Java and UI artifacts.
- Start the complete Compose environment with health waits.
- Seed three governed JFrog repositories from the checked-in curated manifest.
- Reconcile PostgreSQL, S3, and OpenSearch.
- Verify Entra/Graph authorization for APM-A, APM-B, administrator, and no-entitlement states.
- Exercise signed webhook ingestion and measure UI-visible latency.
- Complete responsive, accessibility, console, and network browser QA.

## Production enablement

- Supply environment-owned AWS/network/DNS/certificate/KMS inputs.
- Store all credentials in the approved secret manager.
- Build signed immutable images with SBOM and provenance.
- Deploy migrations, API, worker, and UI through the reviewed Terraform workflow.
- Configure reachable signed JFrog webhooks and keep scheduled reconciliation enabled.
- Run load, penetration, backup-restore, failover, and on-call readiness exercises.

The local starter does not create paid Azure or AWS resources.
