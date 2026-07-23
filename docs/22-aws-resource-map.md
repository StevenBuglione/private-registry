# AWS resource map

| Concern | Terraform | Resources |
|---|---|---|
| State bootstrap | `bootstrap/` | Terraform state/log buckets and encryption; deployment tooling only |
| Network | `modules/platform/network.tf` | VPC, subnets, routes, flow logs |
| Ingress | `alb.tf` | internal ALB, OIDC/TLS listeners, target groups, WAF |
| Compute | `ecs.tf` | UI, combined API/worker, and migration task |
| Database | `database.tf` | Aurora PostgreSQL, RDS Proxy, backups and monitoring |
| Images | `ecr.tf` | immutable UI, API, and migration repositories |
| Private connectivity | `endpoints.tf` | endpoints needed for ECS/ECR/logging/secrets plus optional JFrog PrivateLink |
| Security | `iam.tf`, `kms.tf`, `security-groups.tf` | least-privilege roles, encryption, network controls |
| Operations | `observability.tf`, `backup.tf` | application/database metrics, alarms, PostgreSQL backup |
| Cross-Region | `global/` | ECR replication and failover DNS; database replication is owned by Aurora configuration |

There are no application-owned OpenSearch, S3 data, SQS, EventBridge, or Scheduler resources. PostgreSQL owns catalog search, documentation, queue, dead-letter, and reconciliation state.
