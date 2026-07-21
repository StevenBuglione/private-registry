# Production readiness checklist

- [ ] Fixture mode disabled and blocked in production.
- [ ] Aurora, S3, OpenSearch, JFrog, SQS, identity, and authorization adapters complete.
- [ ] OpenAPI compatibility verified against the pinned UI.
- [ ] Package/event schemas validated in release and ingestion pipelines.
- [ ] Three-AZ ECS/API and OpenSearch topology verified.
- [ ] Database backup, point-in-time recovery, snapshot copy, and restore tested.
- [ ] S3 replication/Object Lock requirements tested.
- [ ] Queue retry, DLQ, redrive, duplicate delivery, and poison message tests pass.
- [ ] Reconciliation dry-run and repair tests pass.
- [ ] ALB JWT signature and authorization tests pass.
- [ ] Penetration, dependency, image, IaC, secret, and license scans pass.
- [ ] Load test meets approved p95 and availability goals.
- [ ] DR Region activation and Route 53 failover exercised.
- [ ] JFrog remains usable during complete catalog outage.
