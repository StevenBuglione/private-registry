# JFrog Registry and Release Design

## Required Artifactory capability

Use an Artifactory release that supports the Terraform repository types required by the design. Provider origin-registry protocol support is available from Artifactory 7.125 and requires GPG-based signing for providers hosted under the private hostname. Confirm exact repository combinations against the deployed version and edition before automation.

## Repository topology

```text
Modules
├── iac-modules-candidate-local
├── iac-modules-release-local
├── iac-modules-public-remote        optional approved public cache
└── iac-modules-virtual              stable consumer endpoint

Internal providers
├── iac-providers-candidate-local
├── iac-providers-release-local
└── approved provider consumer endpoint per supported JFrog mode

Public providers
└── network-mirror local cache       retain registry.terraform.io identity

Catalog bundles
├── iac-catalog-candidate-local      generic
├── iac-catalog-release-local        generic, immutable
└── iac-catalog-virtual
```

Candidate repositories are restricted to release automation and reviewers. Standard consumers can read only approved release/virtual repositories.

## Module source addresses

The portal generates JFrog-specific source addresses from authoritative metadata rather than asking authors to construct them manually.

Example shape:

```hcl
module "vpc" {
  source  = "artifacts.example/iac-modules-virtual__platform/vpc/aws"
  version = "2.4.1"
}
```

Validate the exact source-address syntax against the deployed JFrog version during the readiness gate.

## Internal provider addresses

Internal providers use the enterprise-controlled JFrog hostname as their origin and are signed:

```hcl
terraform {
  required_providers {
    internal_cloud = {
      source  = "artifacts.example/iac-providers-release-local-rt-ns-platform/internal-cloud"
      version = "~> 3.2"
    }
  }
}
```

Validate the exact repository encoding and virtual/federated support before production.

## Public provider identity

Do not republish public providers under the private hostname. The hostname is part of provider identity. Keep:

```hcl
source = "registry.terraform.io/hashicorp/aws"
```

and configure approved clients to use JFrog as a network mirror.

## Promotion model

```text
source tag
 -> candidate publish
 -> Xray/license/malware/policy scan
 -> Terraform installation test
 -> approval decision
 -> server-side promotion/copy to release
 -> immutability enforcement
 -> catalog bundle promotion
 -> EventBridge event
```

Artifact and documentation bundle promotion must be atomic from the catalog's perspective. The event is published only after both release objects are available.

## Service identities

- package publisher: candidate write only;
- promoter: candidate read + release promotion only;
- scanner: read candidate/release;
- catalog reader: release/catalog read only;
- normal consumer: approved virtual read only;
- administrator: separate break-glass role.

Released versions cannot be overwritten or deleted by publishers.

## Provider signing

- build all supported OS/architecture ZIPs;
- generate SHA256SUMS;
- sign the checksum file with the approved OpenPGP key;
- verify signature before candidate publication and before promotion;
- configure the public key in JFrog;
- keep the private key in isolated approved custody;
- maintain rotation, revocation, and emergency response procedures.
