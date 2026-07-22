# ECS deployment contract

The API repository creates the ECR repository, ECS cluster, task execution role, task role, internal ALB listener, target group, CloudWatch log group, and ECS service. This repository publishes an immutable UI image only. The API repository Terraform registers the task definition and updates the ECS service through an environment-protected GitHub OIDC role; the UI workflow never mutates ECS directly.

Required container contract:

- listen on port `8080`;
- `GET /healthz` returns 200 without authentication inside the target group;
- serve SPA routes through `index.html`;
- write runtime JSON at container startup;
- run with a read-only root filesystem where supported, with writable tmpfs paths for Nginx runtime state;
- log to stdout/stderr;
- stop within the ECS deregistration interval;
- never require outbound internet access at runtime.
