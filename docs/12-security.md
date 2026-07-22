# Security, Supply Chain, and Audit Controls

## Principles

- private networking and least privilege;
- no long-lived cloud credentials in source control;
- immutable released artifacts and images;
- separation of author, approver, promoter, and administrator;
- signed internal providers and verified checksums;
- explicit visibility/authorization filtering;
- complete audit evidence and tested recovery.

## Package governance

Lifecycle states:

```text
draft -> candidate -> approved -> maintenance -> deprecated -> revoked -> archived
```

Support levels:

```text
supported | maintenance | experimental | deprecated | revoked | archived
```

Every package version records:

- owner and support channel;
- source repository/commit/tag;
- package and manifest digest;
- build identity and provenance;
- approval decisions and policy version;
- security scan result;
- Terraform compatibility;
- release timestamp and lifecycle.

## Separation of duties

Authors cannot approve/promote their own package. Publishers cannot overwrite release versions. Security exceptions require authorized approval, expiration, reason, and evidence.

## Application security

- sanitize Markdown and disable unsafe raw HTML;
- enforce CSP and other browser headers;
- reject archive traversal/symlinks/decompression bombs;
- limit body/file sizes;
- validate all contract fields and enum values;
- protect administrative writes against CSRF;
- verify ALB-signed identity data;
- implement authorization in API/service layer;
- avoid sensitive values in logs and traces;
- rate-limit expensive search endpoints.

## AWS controls

- KMS encryption at rest;
- TLS in transit;
- private subnets and endpoints;
- Secrets Manager rotation where supported;
- CloudTrail and Config centrally enabled;
- GuardDuty, Security Hub, Inspector, Access Analyzer;
- WAF and ALB access logging;
- ECR enhanced scanning;
- S3 Object Lock for evidence where required;
- AWS Backup and cross-account vault controls.

## Signing key custody

Baseline: encrypted OpenPGP private key in an approved secret store, decryptable only by an isolated signing role with full audit. Higher assurance: approved HSM-backed OpenPGP integration. AWS KMS alone does not emit the OpenPGP file format required by provider registry signing.

## Audit events

Record:

- authentication/authorization failures;
- package publication/promotion/revocation;
- approval decisions;
- signing and signature failures;
- Terraform applies and permission changes;
- ingestion/reconciliation discrepancies;
- DLQ redrive/repair actions;
- break-glass use;
- backup restore and DR activation.

Send operational/security events to the enterprise SIEM without exposing package content or secrets unnecessarily.
