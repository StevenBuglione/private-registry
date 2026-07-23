# AWS deployment sequence

1. Apply the network, ingress, container registry, PostgreSQL, secrets, and compute foundation with application services disabled.
2. Publish immutable UI and API images.
3. Run Flyway migrations against PostgreSQL.
4. Configure JFrog connectivity and the runtime reader secret.
5. Enable the UI and combined API/worker service.
6. Run a full reconciliation and verify search, documentation, queue, dead-letter, SSE, and authorization behavior.

The Registry does not require OpenSearch, S3 application buckets, SQS, or EventBridge. PostgreSQL provides those application capabilities in one recoverable state boundary.
