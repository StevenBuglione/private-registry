# Delivery Effort and Team Plan

> Historical estimate. The runtime architecture was consolidated by ADR 0007.

## Recommended team

- 1 product/platform lead;
- 1 solution architect;
- 2 backend engineers;
- 1 frontend engineer;
- 2 platform/DevOps engineers;
- part-time security, JFrog, database/search, identity, UX/accessibility, and legal/open-source reviewers.

## Estimated effort

| Delivery state | Elapsed time with parallel work | Engineering effort |
|---|---:|---:|
| architecture/fit spike | 2–3 weeks | 6–10 engineer-weeks |
| production-capable MVP in dev | 8–12 weeks | 25–40 engineer-weeks |
| production + DR + evidence | 14–20 weeks | 45–75 engineer-weeks |

The main variables are existing AWS/JFrog/network/identity foundations, provider-signing maturity, UI accessibility/branding scope, approval lead time, and DR objectives. Reusing the Registry UI removes substantial registry-specific frontend design work, but does not remove the catalog data model, compatibility translation, governance, security, and operational work.

## Critical path

1. JFrog version/license/private connectivity decision.
2. Registry UI legal/fit approval and frozen compatibility contract.
3. AWS account/network/identity prerequisites.
4. Aurora/S3/OpenSearch/JFrog adapters and event ingestion.
5. signed provider release path.
6. production testing, recovery evidence, and operational handoff.
