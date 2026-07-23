# Security model

## Trust boundaries

- browser identity terminates at the internal ALB OIDC action;
- API verifies the ALB-signed assertion and applies authorization to every query/field;
- ECS tasks use distinct IAM roles and private subnets;
- GitHub uses OIDC roles constrained by repository/ref/environment;
- JFrog publishing, catalog reading, and end-user download permissions are separate;
- PostgreSQL is authoritative for catalog, document, search, queue, dead-letter, and audit state; JFrog is authoritative for governed package bytes.

## Required controls

- TLS, KMS encryption, private endpoints/routing, no public task IPs;
- immutable images/releases and protected promotion;
- provider checksums/signatures and signing-key custody;
- sanitized Markdown, bounded archives, path traversal/symlink rejection;
- secret retrieval at runtime, no browser/service credentials in source or runtime JSON;
- least privilege, separation of duties, approval evidence, audit export;
- CloudTrail/Config/GuardDuty/Security Hub/Inspector/SIEM integration;
- penetration, dependency, container, IaC, secret, license, accessibility, restore, and DR testing.

UI hiding is never authorization. Search, list, detail, security, usage, and audit results must be filtered server-side.
