# Deployment guide

## Local acceptance

From `repositories/private-registry-api`:

```powershell
.\scripts\export-jfrog-env.ps1
.\scripts\bootstrap-local-eventing-env.ps1
terraform -chdir=infrastructure/terraform/identity-test init
terraform -chdir=infrastructure/terraform/identity-test apply
.\infrastructure\terraform\identity-test\scripts\export-compose-env.ps1
docker compose up --build --detach --wait
docker compose --profile seed run --rm seeder
docker compose restart indexer
```

Verify API readiness, worker dependencies, JFrog repositories, normalized documents, database state, OpenSearch documents, queue/DLQ state, and the authenticated UI. The three ignored `.env.*` files and Terraform state contain secrets and must never be printed or committed.

## Production sequence

1. Review and apply the existing AWS foundation Terraform with application services disabled.
2. Provision secret-manager values for ALB signer/client/issuer verification, Graph entitlement mappings, JFrog access, webhook validation, and AWS service endpoints.
3. Build Java and UI images from an immutable source SHA; attach SBOM, provenance, scan results, and signatures.
4. Run Flyway migrations as a dedicated task.
5. Install the OpenSearch index template and create the read/write aliases.
6. Bootstrap the three governed local JFrog repositories with the Java client seeder.
7. Seed or reconcile the curated catalog and validate digests/properties before catalog-ready activation.
8. Deploy API, indexer, and UI services behind the internal ALB.
9. Configure signed JFrog webhooks only after the endpoint is reachable; keep scheduled reconciliation enabled.
10. Run the full identity, authorization, webhook-to-search latency, accessibility, load, backup, and recovery acceptance gates.

Production must never enable `REGISTRY_SECURITY_PERMIT_ALL`, expose the worker publicly, or make Artifactory a catalog API readiness dependency.
