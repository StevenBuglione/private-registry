# Full Implementation Plan

## 1. Product outcome

Deliver an internal registry experience where engineers can:

- search approved Terraform/OpenTofu modules and providers;
- read versioned documentation, examples, resources, inputs, outputs, and changelogs;
- copy correct JFrog-backed usage snippets;
- see ownership, support, lifecycle, approval, compatibility, and security status;
- continue installing packages directly from JFrog when the catalog portal is unavailable.

The solution is a catalog layered over JFrog, not a second package registry.

## 2. Architecture layers

### Layer 1 — Package data plane

JFrog Artifactory provides:

- local candidate repositories;
- immutable release repositories;
- remote repositories for approved public dependencies;
- stable virtual repositories for consumers;
- Terraform/OpenTofu registry protocols;
- checksums, GPG verification, promotion, retention, replication, and Xray scanning.

### Layer 2 — Release and event plane

Module/provider release pipelines:

1. validate and test source;
2. generate documentation and metadata;
3. create SBOM and provenance;
4. sign internal providers;
5. publish candidate artifacts;
6. run security/policy/integration checks;
7. obtain required approvals;
8. promote immutable artifacts to release repositories;
9. publish a versioned `PackagePromoted` event to EventBridge.

### Layer 3 — Ingestion and reconciliation plane

The indexer consumes SQS events and:

1. validates the event and idempotency key;
2. verifies package existence/digest in JFrog;
3. retrieves and validates the documentation bundle;
4. stores raw failure evidence in quarantine on error;
5. normalizes documentation into S3;
6. writes package/catalog records to Aurora in one transaction;
7. indexes searchable package, symbol, and documentation records in OpenSearch;
8. records a durable audit event;
9. deletes the SQS message only after all authoritative writes succeed.

The reconciler independently compares JFrog, Aurora, S3, and OpenSearch to detect event loss or drift.

### Layer 4 — Catalog API plane

The API exposes:

- an OpenTofu Registry UI compatibility surface;
- an enterprise API for governance/security/ownership/audit;
- search through OpenSearch;
- metadata through Aurora;
- versioned Markdown through S3;
- no package-byte download proxy.

### Layer 5 — User interface plane

The UI is a controlled fork of the OpenTofu Registry UI frontend. It retains the registry-specific page model while replacing public-registry assumptions and adding enterprise metadata.

### Layer 6 — AWS platform plane

ECS Fargate runs the UI, API, indexer, reconciler, and migrations. AWS managed services supply durable data, search, events, encryption, identity integration, telemetry, backup, and recovery.

## 3. Repository strategy

The solution uses two production repositories:

- `private-registry-ui`: frontend fork, overlays, container, UI tests, UI deployment.
- `private-registry-api`: services, contracts, migrations, OpenSearch mappings, Terraform, API tests, infrastructure deployment.

This blueprint repository contains both exportable roots because only one remote was supplied for the planning handoff.

## 4. Workstreams

### Workstream A — Architecture and controls

Deliverables:

- approved architecture decision record;
- threat model and trust boundaries;
- data classification and retention matrix;
- ownership/approval/lifecycle model;
- SLO, RTO, and RPO approval;
- JFrog capability/licensing verification;
- OpenTofu UI licensing and trademark review.

Exit criteria: no unresolved blocking decisions in `docs/16-required-inputs.md`.

### Workstream B — UI controlled fork

Deliverables:

- pinned upstream commit and provenance;
- imported frontend source;
- runtime configuration support;
- organization-neutral branding;
- compatibility API wiring;
- enterprise governance components;
- Nginx ECS image;
- visual, accessibility, and security tests;
- documented upstream update process.

Exit criteria: module and provider fixtures render through the compatibility API without direct JFrog calls.

### Workstream C — Catalog API and workers

Deliverables:

- API server and health endpoints;
- compatibility endpoints;
- enterprise endpoints;
- Aurora repository;
- S3 document adapter;
- OpenSearch adapter;
- JFrog read-only adapter;
- SQS indexer;
- scheduled reconciler;
- migration task;
- ALB identity validation and role authorization;
- OpenTelemetry instrumentation.

Exit criteria: a promoted fixture package becomes searchable and readable within the visibility SLO.

### Workstream D — AWS infrastructure

Deliverables:

- remote state bootstrap;
- VPC/subnets/endpoints/private connectivity;
- internal ALB, ACM, Route 53, OIDC, WAF;
- ECS/ECR/task roles/autoscaling;
- Aurora/RDS Proxy;
- OpenSearch;
- S3 buckets;
- EventBridge/SQS/DLQ/Scheduler;
- KMS/Secrets Manager;
- CloudWatch/SNS/Backup;
- GitHub OIDC deployment roles;
- DR scaffolding and regional failover procedures.

Exit criteria: infrastructure can be applied in two passes and supports a successful ECS deployment.

### Workstream E — JFrog and release governance

Deliverables:

- candidate/release/remote/virtual repository topology;
- permission targets and service identities;
- provider public key and signing process;
- module and provider reusable release workflows;
- candidate-to-release promotion;
- Xray/policy gates;
- EventBridge publishing;
- Terraform/OpenTofu installation smoke tests.

Exit criteria: one internal module and one signed internal provider can be promoted and consumed from approved virtual endpoints.

### Workstream F — Production hardening

Deliverables:

- load and soak tests;
- penetration test remediation;
- accessibility conformance;
- backup restore and DR exercises;
- operational dashboards/alarms;
- on-call and support model;
- pilot onboarding;
- production runbooks and rollback.

Exit criteria: all acceptance criteria pass with recorded evidence.

## 5. Recommended sequencing

```text
Architecture gates
  -> state/network/security foundation
  -> JFrog repository/promotion foundation
  -> UI fit spike + API fixture contract
  -> Aurora/S3/OpenSearch + API adapters
  -> event ingestion/reconciliation
  -> ECS deployment
  -> release pipeline integration
  -> pilot packages
  -> hardening and DR
  -> production rollout
```

UI/API development can run in parallel once the OpenAPI contract and fixture set are frozen.

## 6. Definition of done

The product is complete when:

- standard users can discover and read approved packages;
- package versions shown in the portal exist in JFrog;
- copied snippets pass Terraform and OpenTofu installation tests;
- internal providers validate signatures and checksums;
- public provider source identities remain unchanged;
- event loss is repaired by reconciliation;
- search can be rebuilt from Aurora and S3;
- browser/API authorization is enforced server-side;
- no catalog outage affects JFrog package installation;
- the cross-Region recovery procedure is tested;
- two new package repositories can onboard using reusable workflows without platform code changes.
