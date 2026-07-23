# Java API

The API is Java 25, Spring Boot 4.1, Spring MVC with virtual threads, Spring Security/OAuth2, Spring Modulith, JDBC, Flyway, and Gradle. Blocking JDBC/JFrog adapters remain bounded; WebFlux would not make those dependencies non-blocking.

Modules isolate catalog, identity, eventing, ingestion, health, seeding, and web adapters. ArchUnit and Modulith tests enforce boundaries and cycles.

All catalog service methods require an `AccessContext`. PostgreSQL performs APM filtering for list, count, search, detail, version, docs, governance, and SSE access. The UI never receives unauthorized rows.

API readiness checks PostgreSQL only. Worker readiness checks PostgreSQL and JFrog so a JFrog outage cannot remove existing catalog reads.
