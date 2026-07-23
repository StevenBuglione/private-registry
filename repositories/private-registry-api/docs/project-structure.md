# Project structure

```text
.
├── src/main/java/com/stevenbuglione/registry/
│   ├── artifactory/          # official JFrog Java Client adapter
│   ├── catalog/              # authorized PostgreSQL catalog and search
│   ├── eventing/             # signed webhook and PostgreSQL publication
│   ├── ingestion/            # queue worker, validation, documents, reconciliation
│   ├── security/             # Entra, Graph, ALB OIDC, access context
│   ├── health/               # PostgreSQL/API and JFrog/worker readiness
│   ├── model/                # framework-independent API records
│   └── web/                  # HTTP and SSE adapters
├── src/main/resources/
│   ├── db/migration/         # production Flyway schema migrations
│   └── db/local/             # Compose-only fixtures
├── src/test/java/            # JUnit, Modulith, ArchUnit, and adapter tests
├── contracts/                # versioned event and manifest contracts
├── infrastructure/           # deployment configuration
├── api/openapi.yaml          # HTTP contract
├── compose.yaml              # PostgreSQL + API + UI
├── Dockerfile                # Java 25 multi-stage image
└── build.gradle.kts          # Gradle dependencies and quality gates
```

The API, event worker, and reconciler are one Spring Modulith application and one deployable image. PostgreSQL is the only local stateful service.
