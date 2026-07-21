# AWS Account and Resource Inventory

## Account placement

Use existing AWS Organizations/Control Tower accounts. Recommended separation:

| Account | Purpose |
|---|---|
| shared networking | Transit Gateway, Direct Connect/VPN, Route 53 Resolver, centralized egress |
| security tooling | Security Hub, GuardDuty, Config aggregation, Inspector administration |
| log archive | Organization CloudTrail and immutable centralized logs |
| registry non-production | development and integration runtime |
| registry production | production catalog control plane |
| registry disaster recovery | warm standby in a secondary Region |
| shared build services | optional hardened signing/build workloads |

Do not deploy account-level organization controls from this application stack unless the enterprise platform team delegates ownership.

## Networking

- VPC per environment and Region.
- Three ingress subnets for the internal ALB.
- Three private application subnets for ECS and interface endpoints.
- Three isolated data subnets for Aurora and OpenSearch.
- Optional egress/NAT subnets only when centralized egress is unavailable.
- Transit Gateway attachment or approved private network path.
- Route 53 private hosted zone and Resolver integration.
- S3 gateway endpoint.
- Interface endpoints for ECR API/DKR, CloudWatch Logs/Monitoring, Secrets Manager, KMS, STS, SQS, EventBridge.
- Optional JFrog SaaS PrivateLink interface endpoint.
- VPC Flow Logs.

## Ingress and web security

- Internal Application Load Balancer.
- ACM certificate.
- Route 53 alias.
- HTTPS listener with OIDC authentication.
- AWS WAF regional web ACL.
- ALB access logs to S3.
- Target groups for UI and API.

Important: ALB OIDC requires the IdP authorization, token, and user-info endpoints to meet AWS reachability and certificate requirements. Confirm this during the identity design gate.

## Compute and images

- ECS cluster with Container Insights.
- Fargate task definitions and services:
  - `registry-web`;
  - `catalog-api`;
  - `catalog-indexer`.
- Scheduled Fargate tasks:
  - `catalog-reconciler`;
  - `catalog-migrations`.
- ECR repositories for each image with immutable tags and scan-on-push. Enable Amazon Inspector enhanced ECR scanning through the organization-level security baseline when available.
- Application Auto Scaling:
  - UI/API by CPU, memory, ALB request count, or latency;
  - indexer by SQS backlog per task.

## Data

- Aurora PostgreSQL provisioned cluster.
- RDS Proxy.
- OpenSearch Service domain with VPC access, encryption, dedicated masters, and Multi-AZ with Standby in production.
- S3 buckets:
  - normalized documentation;
  - ingestion quarantine;
  - immutable audit evidence;
  - ALB logs;
  - reconciliation reports.

## Events

- EventBridge custom event bus.
- Rules for promoted, deprecated, and revoked package events.
- SQS standard ingestion queue.
- SQS dead-letter queue.
- EventBridge Scheduler for incremental and full reconciliation.

## Security

- The reference Terraform creates separate customer-managed keys for shared data, logs, eventing/alarms, and backups. Organizations with stricter cryptographic separation can split the shared data key into dedicated S3, Aurora, OpenSearch, and ECR keys without changing service boundaries.
- Secrets Manager for database credentials, OIDC client secret, JFrog reader credential, and optional signing materials.
- Least-privilege ECS execution and task roles.
- GitHub OIDC deployment roles.
- IAM Access Analyzer and central security services as organization prerequisites.

## Operations and recovery

- CloudWatch log groups, metrics, dashboards, alarms.
- SNS alert topic or integration into the existing incident platform.
- AWS Backup vault and policy for Aurora snapshots.
- S3 versioning, Object Lock for evidence where required, and cross-Region replication.
- Aurora Global Database or approved cross-Region recovery pattern.
- ECR cross-Region replication.
- Secondary OpenSearch domain or rebuild procedure.

## External/shared prerequisites

- corporate OIDC provider;
- enterprise DNS and certificate issuance;
- central network connectivity;
- central CloudTrail/Config/GuardDuty/Security Hub;
- enterprise secrets and key-management policy;
- JFrog tenancy and licensing;
- source-control and CI environments;
- SIEM and incident management integration.
