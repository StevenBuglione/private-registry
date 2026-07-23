# Enterprise architecture diagram package

This directory contains the authoritative Mermaid source plus extracted SVG and PNG
artifacts for the Registry's implemented PostgreSQL-only architecture and its corrected
AWS production target.

| Diagram | Scope |
|---|---|
| `01-enterprise-system-context` | Trust boundaries, deployable processes, and external authorities |
| `02-aws-production-topology` | Corrected three-zone AWS target after readiness blockers |
| `03-oidc-apm-authorization` | ALB OIDC verification, Graph membership, and APM filtering |
| `04-artifact-publication-ingestion` | JFrog publication, durable queue, validation, activation, and SSE |
| `05-catalog-authorization-data-model` | Normalized catalog, release, taxonomy, ownership, and entitlement schema |
| `06-operations-data-model` | Admin, audit, traffic, queue, quarantine, and reconciliation schema |
| `07-release-deployment-pipeline` | Quality gates and the five-phase production deployment |
| `08-local-compose-topology` | Exact local processes, ignored inputs, real dependencies, and evidence |
| `09-observability-incident-recovery` | Signals, incident control, recovery branches, and return-to-service proof |

## Rendering

The checked-in renderer pins Mermaid CLI `11.16.0` and creates both a scalable SVG and a
high-resolution PNG beside each `.mmd` file:

```powershell
node scripts/render-mermaid-diagrams.mjs
python scripts/validate_diagram_artifacts.py
```

Pass one or more `.mmd` file names to render only selected diagrams during iteration.

The source files are the maintainable authority. Extracted images are committed so
operators, change reviewers, and downstream coding agents can use them without a Mermaid
runtime. Update source, rerun the renderer, visually inspect every changed image, run the
validator, and commit all three files together.

The AWS diagram is deliberately labeled as a target. The current checked-in AWS
application-service Terraform still has mandatory blockers documented in
[`../32-deployment-readiness-audit.md`](../32-deployment-readiness-audit.md); an image does
not override that stop condition.
