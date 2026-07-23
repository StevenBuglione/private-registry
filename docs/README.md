# Documentation Map

| Document | Purpose |
|---|---|
| `00-implementation-plan.md` | End-to-end implementation plan and work breakdown |
| `01-architecture.md` | Layered system architecture and request/data flows |
| `02-repository-model.md` | Two-repository model and exact project structures |
| `03-aws-services.md` | AWS account and resource inventory |
| `04-networking-identity.md` | VPC, DNS, PrivateLink, OIDC, and authorization |
| `05-data-search.md` | PostgreSQL data, documents, authorization, and search model |
| `06-events-ingestion.md` | PostgreSQL queue, idempotency, dead letters, and reconciliation |
| `07-jfrog.md` | JFrog topology, package identity, publishing, and promotion |
| `08-ui.md` | First-party Registry UI behavior and verification |
| `09-api.md` | Compatibility API, enterprise API, workers, and security |
| `10-terraform.md` | Terraform structure and two-pass deployment |
| `11-cicd.md` | GitHub Actions and release/deployment workflows |
| `12-security.md` | Enterprise security, supply chain, and audit controls |
| `13-observability-dr.md` | Monitoring, SLOs, backup, and disaster recovery |
| `14-testing-acceptance.md` | Test strategy and production acceptance criteria |
| `15-delivery-roadmap.md` | Phases, staffing, sequencing, and estimates |
| `16-required-inputs.md` | Organization-specific decisions and values needed |
| `17-runbook.md` | Initial deployment and operational runbook |
| `18-status.md` | Scaffolded work and remaining implementation |
| `19-references.md` | Official primary-source references |

## Execution and deployment handoff

- [20 — Execution checklist](20-execution-checklist.md)
- [21 — Configuration matrix](21-configuration-matrix.md)
- [22 — AWS resource map](22-aws-resource-map.md)
- [23 — Two-repository bootstrap](23-two-repository-bootstrap.md)
- [24 — Claude deployment handoff](24-claude-deployment-handoff.md)
- [25 — Compatibility API map](25-compatibility-api-map.md)
- [26 — Work breakdown](26-work-breakdown.md)
- [27 — Service contracts](27-service-contracts.md)
- [28 — Operating model and ownership](28-operations-ownership.md)
- [29 — Delivery effort and team plan](29-effort-and-delivery.md)

## Authoritative deployment documents

Use these for new deployments. They supersede retired OpenSearch/SQS/EventBridge/S3
application-topology guidance in older planning documents.

- [30 — Complete deployment and configuration handoff](30-deployment-configuration-handoff.md)
- [31 — Environment-variable reference](31-environment-variable-reference.md)
- [32 — Deployment-readiness audit and mandatory AWS corrections](32-deployment-readiness-audit.md)
