# ECS Service and Task Contracts

## `registry-web`

- long-running ECS service, port 8080;
- ALB health: `GET /healthz`;
- static OpenTofu UI assets served by Nginx;
- startup writes non-secret `/config/runtime.json`;
- no AWS/JFrog credentials and no runtime internet requirement;
- read-only root filesystem with ephemeral mounts for Nginx cache/run/tmp/runtime config;
- minimum production tasks: 3; scale by ALB requests/CPU.

## `catalog-api`

- long-running ECS service, port 8080;
- liveness: `GET /health/live`; readiness: `GET /health/ready`;
- browser reaches it only through authenticated ALB routes;
- validates ALB-signed identity and performs server-side authorization;
- reads Aurora through RDS Proxy, OpenSearch, and S3;
- never serves package archives or returns service credentials;
- minimum production tasks: 3; scale by request/latency/CPU.

## `catalog-indexer`

- long-running ECS service with no load balancer;
- consumes the encrypted SQS ingestion queue;
- validates JFrog release and documentation digests;
- writes S3/Aurora/audit then derived OpenSearch state;
- extends message visibility during long processing;
- deletes only after durable success;
- minimum production tasks: 2; scale by queue backlog and oldest age.

## `catalog-reconciler`

- scheduled one-off Fargate task;
- incremental and full schedules through EventBridge Scheduler;
- compares JFrog, Aurora, S3, and OpenSearch;
- default mode is dry-run; repair requires a distinct authorization path;
- emits immutable reports and drift metrics;
- no public endpoint.

## `catalog-migrations`

- manually/protected one-off Fargate task;
- runs before an application rollout;
- uses a migration-specific role and database identity;
- migrations must support rolling deployment and forward-compatible rollback;
- cannot be scheduled or invoked by normal application identities.

## Shared runtime rules

- Fargate `awsvpc`, private subnets, no public IP;
- X86_64 images unless the build and task platform are changed together;
- SHA/digest image promotion, never `latest`;
- awslogs with KMS-encrypted log groups;
- least-privilege task role per workload;
- ECS deployment circuit breaker with rollback;
- graceful SIGTERM and bounded stop time;
- OpenTelemetry correlation IDs and structured logs;
- no shell/debug access unless a time-bound break-glass control enables ECS Exec.
