# AWS resource inventory

The Terraform platform owns:

- VPC, ingress/application/data subnets, routes, NAT or TGW integration, flow logs;
- S3 gateway and interface endpoints for ECR, Logs, Monitoring, Secrets Manager, KMS, SQS, EventBridge, STS, SSM Messages, and X-Ray;
- optional JFrog PrivateLink endpoint and private DNS alias;
- internal ALB, target groups, TLS/OIDC listener, path rules, WAF, access logs, Route 53;
- ECS cluster, UI/API/indexer services, reconciler/migration tasks, autoscaling, Container Insights, ECS Exec controls;
- immutable KMS-encrypted ECR repositories;
- Aurora PostgreSQL, optional Global Database, RDS Proxy, managed secrets, monitoring, backups;
- VPC-only OpenSearch Multi-AZ with Standby, logs, encryption, fine-grained security;
- versioned encrypted S3 documentation/quarantine/audit/reconciliation buckets and optional Object Lock;
- EventBridge custom bus/archive/rules, encrypted SQS/DLQ, Scheduler group/schedules;
- customer-managed data/log/event/backup KMS keys;
- workload-specific IAM roles and GitHub OIDC plan/apply/image/migration roles;
- CloudWatch log groups, alarms, dashboard, encrypted SNS topic;
- AWS Backup vault/plan/selection/copy action;
- cross-Region ECR/S3 replication and failover DNS in the global root.

Organization-level services such as Organizations/Control Tower, central CloudTrail, Config, GuardDuty, Security Hub, Inspector enhanced ECR scanning, Direct Connect/TGW hubs, central egress/firewall, Resolver, PKI, and SIEM are prerequisites/integrations rather than application-owned resources.
