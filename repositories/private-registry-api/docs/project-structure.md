# Project structure

```text
.
├── src/main/java/com/stevenbuglione/registry/
│   ├── catalog/             # PostgreSQL-backed catalog service
│   ├── config/              # OpenSearch, security, and request configuration
│   ├── health/              # dependency readiness
│   ├── model/               # API records and enums
│   └── web/                 # compatibility and enterprise HTTP routes
├── src/main/resources/
│   ├── db/migration/        # production Flyway schema migrations
│   └── db/local/            # Compose-only fixture migration
├── src/test/java/           # JUnit and MockMvc tests
├── contracts/               # versioned event and manifest contracts
├── opensearch/              # index templates and aliases
├── infrastructure/          # future AWS deployment configuration
├── api/openapi.yaml         # HTTP compatibility contract
├── compose.yaml             # PostgreSQL + OpenSearch + API local stack
├── Dockerfile               # Java 25 multi-stage image
└── build.gradle.kts         # Gradle build and dependency definition
```

The current runnable workload is the catalog API. Worker and reconciliation workloads remain future platform work and are not represented as placeholder executables.
