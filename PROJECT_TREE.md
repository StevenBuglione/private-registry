# Project tree

```text
private-registry/
├── contracts/                         shared manifest/event contracts
├── docs/                              architecture, security, operations, acceptance
├── repositories/
│   ├── private-registry-api/
│   │   ├── src/main/java/             Java 25 API, identity, JFrog, eventing, ingestion
│   │   ├── src/main/resources/        configuration, migrations, curated seed manifest
│   │   ├── src/test/java/             backend unit/integration/architecture tests
│   │   ├── infrastructure/terraform/  PostgreSQL-centered platform and isolated Entra test root
│   │   └── compose.yaml               PostgreSQL-only stateful local acceptance stack
│   └── private-registry-ui/
│       ├── web/src/                   first-party React application
│       ├── public/assets/              Registry and provider assets
│       ├── deploy/nginx/               runtime proxy and configuration
│       └── Dockerfile                 deterministic UI image
└── scripts/                            validation/export helpers
```

Generated build, dependency, Terraform state, environment, seed-cache, and browser-capture directories are ignored.
