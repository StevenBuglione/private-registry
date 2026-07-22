# Repository model

The handoff repository contains two independently buildable product roots.

## `repositories/private-registry-ui`

Owns the tracked React/Vite application, public assets, runtime configuration, Nginx proxy/container, UI automation, accessibility checks, and visual QA report. It does not own server-side identity, catalog persistence, package bytes, or cloud infrastructure.

## `repositories/private-registry-api`

Owns the Java 25 API and workers, Gradle build, identity verification, Microsoft Graph authorization, JFrog Java client adapter, migrations, OpenSearch mappings, EventBridge/SQS/S3 integration, curated seed manifest, Docker Compose environment, and deployment Terraform.

The UI and API may be exported into separate remotes, but shared API/event contracts must stay versioned and tested together.
