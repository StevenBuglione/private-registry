# Testing Strategy and Production Acceptance

## Test layers

### Unit

- domain/version/lifecycle logic;
- event and manifest validation;
- authorization decisions;
- source snippet generation;
- Markdown/path safety;
- idempotency and reconciliation comparison.

### Contract

- OpenAPI request/response validation;
- UI generated client compatibility;
- JSON Schema positive/negative fixtures;
- EventBridge event versioning;
- JFrog response fixtures.

### Integration

- API with PostgreSQL, S3-compatible fixture, and OpenSearch test domain/container;
- SQS event to completed ingestion;
- duplicate event idempotency;
- partial failure/retry and DLQ;
- JFrog candidate/release fixture verification;
- migration upgrade/downgrade policy.

### End-to-end

- login through ALB OIDC;
- search and filters;
- module/provider details and versions;
- copy snippet;
- Terraform and OpenTofu init against JFrog;
- deprecated/revoked behavior;
- hidden package authorization;
- promotion to visible package within SLO.

### Non-functional

- WCAG 2.2 AA accessibility;
- visual regression;
- SAST/SCA/container/IaC scanning;
- penetration testing;
- load/soak/failure injection;
- backup restoration;
- cross-Region DR exercise.

## Production acceptance criteria

1. The UI is built from a documented pinned upstream commit.
2. Required license notices are preserved and branding is approved.
3. Modules and providers are searchable with versioned docs.
4. Every displayed version exists in JFrog with matching digest.
5. Copied snippets pass Terraform and OpenTofu installation tests.
6. Internal provider signatures/checksums validate.
7. Public provider identities remain unchanged.
8. Candidate packages are inaccessible to normal consumers.
9. Release versions cannot be overwritten by publishers.
10. Browser code contains no AWS/JFrog credentials.
11. Protected APIs enforce server-side authorization.
12. Search respects visibility and does not leak hidden package existence.
13. Duplicate events do not create duplicate versions.
14. Failed ingestion enters the DLQ/quarantine with evidence.
15. Reconciliation detects and can repair catalog drift.
16. OpenSearch can be rebuilt from Aurora/S3.
17. Production services span three Availability Zones.
18. All production data is encrypted with approved KMS keys.
19. Observability dashboards and alarms are operational.
20. Backup restore and regional recovery are successfully exercised.
21. A portal outage does not prevent package installation.
22. A new module can onboard using a reusable workflow without platform code.
23. A new provider can onboard using the signed-release workflow without platform code.
24. Accessibility, security, load, and resiliency evidence is approved.
25. Runbooks, ownership, on-call, and rollback procedures are approved.
