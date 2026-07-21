# Production adapter implementation

The Java 25 Spring Boot starter isolates external systems behind focused services. Complete the remaining production adapters before enabling application services in production.

## Aurora store

Use a maintained PostgreSQL driver and:

- connect only through RDS Proxy with TLS verification;
- use separate application and migration users;
- cap connection pools below proxy/database limits;
- wrap package/version/document metadata changes in transactions;
- acquire idempotency rows before ingestion work;
- use statement timeouts and context cancellation;
- avoid storing full Markdown bodies in Aurora;
- expose dependency-specific readiness without leaking details.

## S3 document store

- SSE-KMS on every write;
- checksum validation on upload/download;
- normalized deterministic keys;
- versioning and retention policies;
- no public ACLs or presigned URLs in the browser path;
- bounded decompression and file counts;
- reject path traversal and symbolic links;
- quarantine invalid bundles with event/error metadata.

## OpenSearch adapter

- use SigV4 and VPC-only access;
- query aliases, never concrete index names from API handlers;
- filter visibility before returning results;
- use `search_after` cursors rather than deep offset pagination;
- exact-name and namespace boosts before documentation body matches;
- use bulk indexing and deterministic document IDs;
- support blue/green index rebuild and atomic alias switch.

## JFrog client

- read-only token from Secrets Manager or workload identity integration;
- PrivateLink/private routing;
- verify artifact SHA-256 and catalog manifest digest;
- enforce allowed repositories and maximum content sizes;
- treat JFrog properties as evidence, not user-supplied display HTML;
- do not proxy module/provider archives to browsers.

## Identity and authorization

Verify the ALB `x-amzn-oidc-data` JWT signature with the ALB public key endpoint, expected signer ARN, issuer, audience, expiration, and nonce/state protections in the ALB flow. Map immutable IdP group identifiers to roles from centrally managed configuration. Enforce visibility, package scope, and sensitive audit/security field access in repository queries and handlers.

## Worker semantics

- at-least-once SQS delivery;
- idempotency key uniqueness in Aurora;
- heartbeat/visibility extension for long work;
- delete only after completion;
- classify retryable versus terminal failures;
- terminal validation failures go to quarantine and then DLQ;
- include correlation/event IDs in every log, metric, and audit record.
