# Implementation Work Breakdown

Each epic should be implemented through reviewed pull requests with architecture, security, testing, rollback, and operational evidence.

## Epic 1 — Repository and governance foundation

- export the UI/API repositories;
- configure branch protection, CODEOWNERS, secret scanning, dependency review, code scanning, and protected environments;
- configure GitHub OIDC without static AWS keys;
- record architecture decisions and unresolved inputs;
- complete open-source/trademark review for the UI fork.

**Exit:** both repositories build from clean clones and production changes require approved reviews/environments.

## Epic 2 — OpenTofu UI controlled fork

- import the pinned frontend and preserve provenance/notices;
- apply deterministic runtime/API/package-panel hooks;
- replace public branding, links, popularity, and submission assumptions;
- integrate the selected design system;
- generate TypeScript types from the compatibility contract;
- implement enterprise search filters and governance/security/audit states;
- add unit, contract, visual, accessibility, CSP, and end-to-end tests.

**Exit:** all pinned UI routes render against compatibility fixtures and no browser request targets JFrog directly.

## Epic 3 — Compatibility and enterprise API

- vendor/review the pinned upstream OpenAPI contract;
- create compatibility DTOs separate from internal domain models;
- implement list/package/version/document/search/top-provider routes;
- implement enterprise governance/approval/security/ownership/usage/audit/JFrog routes;
- implement pagination, errors, ETags/cache rules, request IDs, and authorization;
- add generated contract tests and UI consumer tests.

**Exit:** captured pinned-UI requests pass without response-shape exceptions.

## Epic 4 — Catalog persistence and search

- finalize Aurora schema and migrations;
- implement RDS Proxy/TLS/IAM-authenticated repositories;
- implement normalized versioned S3 keys and quarantine controls;
- implement OpenSearch templates, aliases, ranking, filters, and blue/green rebuild;
- add backup/restore, point-in-time recovery, and index-rebuild tests.

**Exit:** Aurora/S3 can reconstruct the complete OpenSearch index.

## Epic 5 — JFrog integration and release governance

- create candidate/release/remote/virtual/catalog repositories;
- configure permissions, immutability, retention, Xray/policy gates, and replication;
- implement read-only catalog client and digest/signature checks;
- implement reusable module/provider release workflows;
- implement provider signing and approval evidence;
- publish validated EventBridge events after promotion.

**Exit:** one module and one signed provider promote and install through both clients.

## Epic 6 — Event ingestion and reconciliation

- implement SQS long-polling, heartbeat/visibility extension, retry classification, idempotency, and DLQ behavior;
- normalize and sanitize documentation with archive limits/path protections;
- commit authoritative data before derived indexing;
- implement incremental/full reconciliation, dry-run reports, and separately authorized repair;
- add duplicate, poison-message, partial-write, redrive, and event-loss tests.

**Exit:** event loss and derived-index failure are repaired without duplicate package versions.

## Epic 7 — AWS platform and ECS deployment

- bootstrap remote state;
- deploy networking/private endpoints/private JFrog path;
- deploy internal ALB/OIDC/WAF and DNS;
- deploy KMS, ECR, S3, Aurora/RDS Proxy, OpenSearch, EventBridge/SQS/Scheduler, IAM, CloudWatch, SNS, and Backup;
- deploy ECS services/tasks, autoscaling, health checks, circuit breakers, and read-only filesystems;
- implement two-pass Terraform and immutable image deployment.

**Exit:** dev can be rebuilt from state/bootstrap instructions and rollback is tested.

## Epic 8 — Security, operations, and recovery

- complete threat model and authorization matrix;
- integrate SIEM/security findings and incident management;
- implement dashboards, SLOs, paging, runbooks, and audit retention;
- perform penetration, accessibility, load, restore, and DR tests;
- validate catalog outage isolation from JFrog package installation;
- onboard pilot teams and transition to an owned service.

**Exit:** all production acceptance criteria have retained evidence and named owners.
