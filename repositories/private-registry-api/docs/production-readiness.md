# Production readiness checklist

- [ ] Fixture mode disabled and blocked in production.
- [ ] PostgreSQL, JFrog, identity, and authorization adapters verified.
- [ ] OpenAPI compatibility verified against the UI.
- [ ] Package/event schemas validated in release and ingestion pipelines.
- [ ] PostgreSQL backup, point-in-time recovery, snapshot copy, and restore tested.
- [ ] Database queue retry, dead-letter inspection, duplicate delivery, stale-claim recovery, and poison-message tests pass.
- [ ] Incremental and full reconciliation tests pass.
- [ ] Search relevance, authorization, and `pg_trgm`/GIN query plans meet latency goals.
- [ ] ALB JWT signature and authorization tests pass.
- [ ] Penetration, dependency, image, IaC, secret, and license scans pass.
- [ ] Load test meets approved p95 and availability goals.
- [ ] JFrog remains usable during a complete catalog outage; catalog reads remain usable during a JFrog outage.
