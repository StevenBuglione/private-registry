# Claude Deployment Handoff

> Historical handoff. Do not follow its former multi-service adapter sequence; ADR 0007 and `CLAUDE.md` are authoritative.

Use this file as the implementation-agent contract.

## Objective

Turn the two exported templates into a production-ready private registry catalog without changing the architectural boundaries: JFrog serves package bytes; AWS serves discovery/documentation/governance; ECS Fargate runs application workloads; Terraform owns AWS deployment.

## Required read order

1. root `CLAUDE.md`;
2. `docs/00-implementation-plan.md`;
3. `docs/01-architecture.md`;
4. `docs/02-repository-model.md`;
5. `docs/16-required-inputs.md`;
6. `docs/20-execution-checklist.md`;
7. each exported repository's `CLAUDE.md`;
8. `docs/18-status.md` so scaffold boundaries are not mistaken for completed adapters.

## Non-negotiable constraints

- Keep two production repositories.
- Do not implement package download/protocol endpoints in the catalog API.
- Do not expose JFrog/AWS service credentials to browser code.
- Do not rename public providers under the private hostname.
- Do not let application pipelines mutate ECS outside Terraform.
- Do not enable the Spring `local` profile or permit-all security in production.
- Do not invent unresolved organization inputs.
- Do not weaken immutable release, approval, signing, audit, or server-side authorization controls to make a demo pass.

## First coding sequence

1. Complete the UI fit spike at the pinned upstream commit.
2. Freeze compatibility fixtures and OpenAPI.
3. Implement Aurora and migration runner.
4. Implement S3 and OpenSearch adapters.
5. Implement JFrog read client.
6. Implement ALB JWT verification/authorization.
7. Implement SQS indexer with idempotency and quarantine.
8. Implement reconciler/report/repair authorization.
9. Deploy dev foundation, build images, initialize data, deploy services.
10. Connect one module and one signed-provider release pipeline.
11. Produce test evidence before production/DR.

## Required evidence per pull request

- changed architecture/contract and why;
- tests and static checks run;
- Terraform plan impact where applicable;
- migration compatibility;
- security/authorization impact;
- rollback strategy;
- documentation and status updates.

## Stop conditions

Stop deployment and open a decision issue when the deployed JFrog edition/version does not support the assumed repository mode; identity endpoints cannot be reached by the internal ALB; required AWS service/instance types are unavailable in a selected Region; a migration is not rolling-deployment compatible; a public-provider identity would change; or DR would create two writable catalog authorities.
