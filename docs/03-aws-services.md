# AWS services

The Registry deliberately limits managed application data services to PostgreSQL.

Required platform capabilities:

- private networking and DNS;
- internal TLS/OIDC ingress;
- container image storage and compute for the UI and combined API/worker;
- Aurora PostgreSQL or RDS PostgreSQL with backup, monitoring, and optional proxy;
- secret management, logs, alarms, and least-privilege IAM;
- private JFrog connectivity where available.

The application does not require OpenSearch, application S3 buckets, SQS, EventBridge, or EventBridge Scheduler. Terraform-state S3 and ECR's S3 VPC endpoint are deployment/platform mechanics, not Registry data stores.
