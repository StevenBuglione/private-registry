# Delivery Roadmap, Staffing, and Effort

## Phase 0 — Fit and governance (2–3 weeks)

- OpenTofu UI technical/legal fit spike;
- JFrog version/edition/repository-mode validation;
- architecture review and threat model;
- AWS accounts/Regions/network/identity decisions;
- package metadata, lifecycle, approval model;
- SLO/RTO/RPO approval.

## Phase 1 — AWS and JFrog foundation (3–5 weeks)

- Terraform state and deployment roles;
- VPC, endpoints, DNS, certificate, OIDC;
- ECR, KMS, S3, EventBridge/SQS;
- Aurora/RDS Proxy and OpenSearch;
- JFrog repository topology and permissions;
- test module/provider publication.

## Phase 2 — UI foundation (3–5 weeks)

- controlled fork/import;
- branding/runtime config;
- compatibility fixtures;
- enterprise components;
- container and CI/CD;
- accessibility and visual baseline.

## Phase 3 — API/data platform (5–8 weeks)

- database model/migrations;
- compatibility and enterprise APIs;
- JFrog/S3/OpenSearch adapters;
- event indexer/idempotency/quarantine;
- reconciliation;
- identity/authorization;
- telemetry.

## Phase 4 — Release integration (4–6 weeks)

- module/provider reusable workflows;
- signing, SBOM, Xray/policy gates;
- promotion and EventBridge events;
- installation smoke tests;
- pilot package onboarding.

## Phase 5 — Hardening and rollout (4–6 weeks)

- load/soak/penetration/accessibility testing;
- backup/DR exercise;
- runbooks and on-call;
- pilot feedback/remediation;
- staged production launch.

## Expected delivery

With mature AWS, JFrog, networking, identity, and CI foundations, phases can overlap:

```text
Expected elapsed delivery: 14–20 weeks
Estimated effort:          45–75 engineer-weeks
```

A smaller first release limited to modules and basic governance can be delivered earlier, but internal providers, formal approvals, DR, and full hardening should not be represented as a few-day effort.

## Recommended team

- product/platform owner;
- solution architect;
- two backend engineers;
- one frontend engineer;
- two platform/DevOps engineers;
- part-time security engineer;
- part-time JFrog administrator;
- part-time database/search specialist;
- part-time UX/accessibility support.

## Critical path

The most likely blockers are identity endpoint compatibility with ALB OIDC, JFrog provider origin-registry/signing behavior, network/PrivateLink approvals, source metadata governance, and cross-account/Region recovery decisions.
