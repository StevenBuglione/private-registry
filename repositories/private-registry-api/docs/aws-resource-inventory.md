# AWS resource inventory

PostgreSQL is the only AWS-managed data service required by the Registry. A typical AWS deployment still needs ordinary application hosting and ingress, but it does not need an application-owned object store, search cluster, message queue, or event bus.

The platform may own:

- VPC/subnets/routes and private JFrog connectivity;
- internal ALB with TLS/OIDC and WAF controls;
- ECS services for the UI and combined API/worker;
- immutable ECR repositories;
- Aurora PostgreSQL or RDS PostgreSQL, RDS Proxy where justified, backups, and monitoring;
- secrets, task IAM roles, logs, alarms, and protected CI deployment roles.

Catalog documentation, search indexes, incoming events, retries, dead letters, reconciliation state, and audit records all reside in PostgreSQL. JFrog remains the governed artifact authority.
