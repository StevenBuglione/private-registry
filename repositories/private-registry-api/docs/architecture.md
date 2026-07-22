# API architecture

## Request path

```text
Browser -> internal ALB/OIDC -> API ECS service
                             -> Aurora through RDS Proxy
                             -> OpenSearch VPC endpoint
                             -> S3 gateway endpoint
```

The API validates the ALB-signed assertion and then applies package visibility and endpoint authorization. The UI is not trusted to filter protected data.

## Event path

```text
Governed release -> JFrog promotion -> EventBridge -> SQS -> indexer
Indexer -> JFrog digest validation -> S3 normalization -> Aurora transaction -> OpenSearch
```

The SQS message is deleted only after authoritative state is committed. If OpenSearch fails after Aurora/S3 succeed, the ingestion record remains repairable and reconciliation completes the derived index.

## Reconciliation

The scheduled reconciler enumerates approved JFrog repositories and compares them with Aurora, S3, and OpenSearch. It supports `dry-run` and separately authorized `repair` modes, writes a signed report to S3, and emits metrics/audit records.

## Deployment units

- `catalog-api`: minimum three tasks across Availability Zones.
- `catalog-indexer`: minimum two tasks, autoscaled from queue backlog/oldest age.
- `catalog-reconciler`: scheduled Fargate task with no public endpoint.
- `catalog-migrations`: one-off Fargate task with elevated database rights.
- `registry-web`: separate image/repository, deployed into shared ECS/ALB infrastructure.
