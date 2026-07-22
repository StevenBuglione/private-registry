# Data, Documentation, and Search Design

## Aurora data model

Core tables:

```text
packages
package_versions
package_owners
package_visibility
module_inputs
module_outputs
module_resources
module_submodules
module_examples
provider_resources
provider_data_sources
provider_functions
provider_guides
provider_platforms
documentation_pages
package_dependencies
approvals
policy_results
security_findings
lifecycle_events
ingestion_events
reconciliation_runs
audit_events
```

Key rules:

- natural package identity is unique by type/namespace/name/target;
- version identity is immutable and includes package digest;
- only one latest stable version is selected by policy;
- prerelease, deprecated, revoked, and archived state are explicit;
- event idempotency key is unique;
- package visibility and authorization metadata live in Aurora;
- search index state is not authoritative.

## Documentation in S3

Normalized keys:

```text
modules/{namespace}/{name}/{target}/{version}/...
providers/{namespace}/{name}/{version}/...
```

Each version contains:

- manifest;
- normalized Markdown;
- structured metadata JSON;
- examples and source snippets;
- checksums/digests;
- normalization version.

Raw or suspicious bundles go to quarantine and are never rendered.

Controls:

- Block Public Access;
- KMS encryption;
- versioning;
- path traversal and symlink rejection;
- file count/uncompressed size limits;
- Markdown sanitization;
- content-type allowlist;
- cross-Region replication;
- Object Lock for approval/audit evidence where required.

## OpenSearch indexes

Recommended aliases and versioned indexes:

```text
packages-read       -> packages-v1
symbols-read        -> symbols-v1
documents-read      -> documents-v1
```

Index dimensions include:

- package type, namespace, name, target;
- title, description, keywords;
- owner, support, lifecycle, approval, compatibility, risk;
- resource/data-source/function/input/output names;
- documentation body;
- latest stable version and release date;
- visibility filters.

Ranking priority:

1. exact package name;
2. exact namespace/name;
3. prefix package name;
4. symbol name;
5. title and keywords;
6. description;
7. documentation body.

Boost approved, supported, active, verified packages. Penalize experimental, deprecated, archived, or prerelease-only packages.

## Index rebuild

A rebuild job:

1. creates new versioned indexes;
2. streams authoritative records from Aurora and S3;
3. validates counts and sample queries;
4. atomically switches aliases;
5. retains the previous indexes for rollback;
6. deletes old indexes after the rollback window.

## Version behavior

- unversioned package URLs resolve to latest stable approved version;
- versioned URLs never change content;
- prereleases do not become default;
- revoked versions return a policy warning and may be hidden or blocked;
- documentation changes require a new package version unless policy explicitly allows metadata-only corrections with audit evidence.
