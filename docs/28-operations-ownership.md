# Operating Model and Ownership

## Service ownership

| Area | Accountable owner | Responsibilities |
|---|---|---|
| product/platform | registry platform team | roadmap, SLO, API/UI, onboarding, incident command |
| AWS runtime | cloud platform team | accounts, network, ECS, managed services, backup/DR |
| JFrog | artifact platform team | repository topology, HA/federation, permissions, Xray, support |
| security | security engineering | threat model, controls, scanning policies, exceptions, testing |
| identity | identity platform team | OIDC client, claims, groups, certificate/reachability |
| package owners | publishing teams | source, tests, docs, versions, support, deprecation |
| database/search | registry platform with specialists | migrations, tuning, recovery, index strategy |

## Support tiers

- **Tier 1:** access, navigation, known onboarding issues, incident intake.
- **Tier 2:** catalog/API/indexer/reconciliation, package metadata, search, release-event diagnosis.
- **Tier 3:** AWS managed-service, network/identity, JFrog, signing, database/search specialist escalation.

## Operational rhythms

- daily: alarms, DLQ, reconciliation drift, failed promotions;
- weekly: capacity, latency, error budget, security findings, stale ownership;
- monthly: dependency/image/IaC patching and restore sample;
- quarterly: upstream UI review, access review, DR readiness, package lifecycle review;
- annually or per policy: penetration, accessibility, full recovery and signing-key exercise.

## Incident priority

JFrog package download availability and provider-signing compromise are higher severity than portal/search degradation. A portal incident must not trigger risky changes to the JFrog data plane. Stop promotion during integrity or regional-consistency incidents until authoritative state is verified.
