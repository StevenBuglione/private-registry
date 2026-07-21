# Two-Repository Model and Project Structures

## Why two repositories

The UI and API have different upstream dependencies, languages, release cadences, security surfaces, and ownership. Terraform stays with the API because the API repository owns the shared runtime, service contracts, data services, and deployment sequencing.

## Repository 1: `private-registry-ui`

```text
private-registry-ui/
├── app/                              # imported OpenTofu frontend, committed after intake
├── .upstream/
│   ├── OPEN_TOFU_COMMIT
│   └── UPSTREAM_REPOSITORY
├── overlays/
│   └── src/enterprise/
│       ├── runtime-config.ts
│       ├── GovernanceBadges.tsx
│       └── RegistrySourceSnippet.tsx
├── patches/
│   └── README.md                    # fail-closed patch strategy
├── deploy/
│   ├── nginx/default.conf
│   ├── docker-entrypoint.d/40-runtime-config.sh
│   ├── nginx/runtime.json.template
│   └── runtime-config.example.env
├── scripts/
│   ├── import-upstream.sh
│   ├── apply-overlays.sh
│   ├── patch-upstream.py
│   └── verify-upstream.sh
├── .github/
│   ├── CODEOWNERS
│   ├── dependabot.yml
│   └── workflows/
│       ├── ci.yml
│       ├── release.yml
│       └── upstream-review.yml
├── LICENSES/
├── Dockerfile
├── README.md
├── CLAUDE.md
├── UPSTREAM.md
└── PATCHES.md
```

Ownership:

- frontend/UI code;
- upstream intake and license provenance;
- enterprise UI components;
- UI container image;
- UI tests and deployment.

Not owned:

- AWS infrastructure;
- database/search/event contracts;
- package bytes;
- server-side authorization.

## Repository 2: `private-registry-api`

```text
private-registry-api/
├── cmd/
│   ├── api/
│   ├── indexer/
│   ├── reconciler/
│   └── migrations/
├── internal/
│   ├── auth/
│   ├── catalog/
│   ├── config/
│   ├── events/
│   ├── jfrog/
│   ├── search/
│   ├── httpapi/
│   ├── documents/
│   ├── model/
│   ├── logging/
│   └── worker/
├── api/
├── contracts/
├── migrations/
├── opensearch/
├── infrastructure/terraform/
│   ├── bootstrap/
│   ├── modules/platform/
│   ├── live/dev/
│   ├── live/prod/
│   └── live/dr/
├── docs/
├── scripts/
├── .github/workflows/
├── Dockerfile
├── Makefile
├── README.md
└── CLAUDE.md
```

Ownership:

- compatibility and enterprise APIs;
- indexer/reconciler/migrations;
- data schemas and contracts;
- OpenSearch mappings;
- all AWS Terraform;
- API images and service deployment;
- infrastructure runbooks.

## Contract publication

The API repository publishes the OpenAPI document and JSON Schemas as versioned CI artifacts. The UI generates TypeScript types from that artifact. The UI must not copy ad hoc response types by hand.

## Branch and release controls

Both repositories require:

- protected `main`;
- required code-owner review;
- signed or verified commits according to policy;
- required CI and security checks;
- GitHub environments for deployment approvals;
- immutable SHA image tags;
- release notes and rollback information.

The UI additionally requires security/legal review for upstream intake. The API additionally requires database migration and Terraform plan review.
