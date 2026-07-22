# Observability, SLOs, Backup, and Disaster Recovery

## Telemetry

Use structured JSON logs with timestamp, service, environment, correlation ID, trace ID, package identity where safe, operation, duration, status, and error class.

Use OpenTelemetry for HTTP, database, OpenSearch, S3, SQS, and JFrog client spans. Avoid recording tokens, Markdown bodies, or secret values.

## Dashboards

- portal/API availability and latency;
- search latency, errors, and result counts;
- SQS backlog/age/DLQ;
- ingestion success and stage duration;
- reconciliation drift;
- Aurora connections, CPU, latency, replica lag;
- OpenSearch health, JVM pressure, storage, rejected operations;
- ECS task count/restarts/deployments;
- JFrog connectivity and verification failures;
- backup/replication status;
- security findings and WAF blocks;
- cost/capacity trends.

## Alarms

At minimum:

- API/UI 5xx or target unhealthy;
- p95 latency breach;
- any DLQ message;
- oldest queue message exceeds visibility SLO;
- ingestion failure rate;
- reconciliation mismatch;
- Aurora failover/high connections/storage;
- OpenSearch red/yellow state/JVM/storage;
- JFrog private connectivity failure;
- signature failure;
- failed backup or replication lag;
- ECS deployment rollback.

## Proposed objectives

| Capability | Objective |
|---|---:|
| JFrog package availability | contracted JFrog SLA, target 99.95%+ |
| Catalog API and portal | 99.9% |
| Search p95 | < 500 ms |
| Package detail p95 | < 300 ms |
| Promotion-to-search visibility | < 5 minutes |
| Primary catalog RPO | 15 minutes |
| Catalog regional RTO | 2 hours |
| Search regional RTO | 4 hours, rebuildable |

These are proposed engineering targets requiring stakeholder approval.

## Backup

- Aurora PITR and scheduled AWS Backup snapshots;
- cross-account and cross-Region snapshot copies;
- S3 versioning and replication;
- Object Lock for evidence;
- ECR replication;
- Terraform state versioning and backup;
- JFrog replication/federation according to selected product topology.

## DR model

Active/passive:

- primary Aurora writer in primary Region;
- Aurora Global Database secondary or approved alternate;
- replicated S3/ECR;
- standby ECS/network/ALB capacity defined in Terraform;
- warm OpenSearch or rebuild from Aurora/S3;
- Route 53 failover;
- package promotions paused during failover;
- full reconciliation before resuming publication.

## Recovery sequence

1. declare incident and freeze promotions;
2. confirm JFrog package plane availability;
3. promote Aurora secondary;
4. scale DR ECS services;
5. validate S3/ECR replication;
6. activate/rebuild OpenSearch;
7. run migrations if required;
8. run full report-only reconciliation;
9. switch portal DNS;
10. run synthetic browser and Terraform tests;
11. authorize repair and resume publication.
