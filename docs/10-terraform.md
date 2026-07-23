# Terraform

The regional platform module creates networking, ingress, ECR, the UI and combined API/worker compute, Aurora PostgreSQL/RDS Proxy, secrets/IAM, monitoring, and backup. It intentionally omits application OpenSearch, S3, SQS, EventBridge, Scheduler, and separate indexer/reconciler services.

Deployment uses two passes:

1. Apply the foundation with `deploy_application_services=false`.
2. Publish immutable images, run Flyway, then enable services with reviewed image tags.

The isolated `identity-test` root creates only free Entra acceptance-test directory objects. The bootstrap root still uses encrypted/versioned S3 for Terraform state; this is deployment tooling and not an application runtime dependency.
