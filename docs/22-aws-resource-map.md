# AWS Resource-to-Code Map

All application AWS infrastructure is owned by `repositories/private-registry-api/infrastructure/terraform`.

| Layer | Terraform file/root | Principal resources |
|---|---|---|
| State | `bootstrap/` | S3 state/log buckets, KMS key, public access block, versioning, protective bucket policy |
| Network | `modules/platform/network.tf` | VPC, three subnet tiers, NAT/TGW routes, route tables, VPC Flow Logs |
| Security groups | `security-groups.tf` | ALB, UI, services, endpoints, Aurora/RDS Proxy, OpenSearch groups/rules |
| Private endpoints | `endpoints.tf` | S3 gateway; ECR, Logs, Monitoring, Secrets Manager, KMS, SQS, EventBridge, STS, SSM Messages, X-Ray interface endpoints; optional JFrog endpoint/DNS |
| Encryption | `kms.tf` | separate data, logs, queue/alarms, and backup CMKs/aliases with service policy statements |
| Registry images | `ecr.tf` | immutable KMS-encrypted ECR repositories and lifecycle policies |
| Documents/evidence | `storage.tf` | S3 documentation, quarantine, audit, reconciliation, and ALB-log buckets; encryption/versioning/lifecycle/Object Lock |
| Database | `database.tf` | Aurora PostgreSQL, optional Global Database, instances, parameter groups, RDS Proxy, monitoring role, managed secret integration |
| Search | `search.tf` | VPC-only OpenSearch Multi-AZ with Standby, logs, access policy, encryption, fine-grained security |
| Eventing | `eventing.tf` | EventBridge bus/rule/archive, SQS ingestion/DLQ, queue policies, redrive settings |
| Compute | `ecs.tf` | ECS cluster, Fargate task definitions/services, log groups, deployment circuit breakers |
| Scheduling | `scheduler.tf` | incremental/full reconciliation schedules with retry and DLQ |
| Scaling | `autoscaling.tf` | UI/API/indexer targets, CPU policies, queue-based indexer step scaling |
| Ingress | `alb.tf` | internal ALB, target groups, TLS/OIDC listener, path routing, WAF, optional regional DNS |
| IAM | `iam.tf` | task execution, per-service task, RDS/monitoring, Scheduler, flow-log, OpenSearch admin roles/policies |
| GitHub deployment | `github-oidc.tf` | optional OIDC provider plus plan/apply/image/migration roles and policy attachments |
| Observability | `observability.tf` | CloudWatch logs/alarms/dashboard, encrypted SNS topic/policy |
| Backup | `backup.tf` | encrypted AWS Backup vault, service role, plan, Aurora selection, optional copy action |
| Invariants | `checks.tf` | three-AZ, image, production-capacity, DR, Object Lock, and GitHub OIDC assertions |
| Cross-Region | `global/` | account-level ECR replication, selected S3 replication, failover DNS |
| Environment roots | `live/dev`, `live/prod`, `live/dr` | module composition, backend, providers, approved environment inputs |

## Shared organization services not recreated

The application integrates with, but does not own, AWS Organizations/Control Tower, central CloudTrail, Config aggregation, GuardDuty/Security Hub administration, Direct Connect/VPN/Transit Gateway hubs, Route 53 Resolver, centralized egress/firewall, enterprise PKI, SIEM, and incident management.

## Deployment ownership rule

Application image workflows publish immutable images only. Terraform registers task definitions and changes ECS services. Avoid a second deployment mechanism that mutates ECS behind Terraform.
