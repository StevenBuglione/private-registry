# Required Inputs Before Deployment

Claude or another implementation agent must not invent these values.

## AWS organization

- production/non-production/DR account IDs;
- primary and DR Regions;
- approved Availability Zones;
- deployment role and KMS administrator principals;
- organization-required tags and SCP constraints;
- central logging/security service ownership.

## Networking

- VPC CIDRs and subnet CIDRs;
- corporate ingress CIDRs;
- Transit Gateway/VPN/Direct Connect attachment details;
- centralized egress route and allowed outbound endpoints;
- private hosted zone ID and DNS delegation;
- Route 53 Resolver rules/endpoints;
- JFrog PrivateLink service name or private route;
- whether NAT gateways are permitted/required.

## Identity

- OIDC issuer;
- authorization/token/user-info endpoints;
- client ID and protected client-secret delivery method;
- required scopes/claims;
- group-to-role mapping;
- session timeout and logout behavior;
- confirmation that ALB can resolve/reach endpoints and trusts certificates.

## JFrog

- Artifactory version, edition, SaaS/self-managed topology;
- JFrog hostname/base URL;
- exact supported module/provider repository modes;
- candidate/release/virtual repository names;
- Xray policies/watch names;
- service identities and permission targets;
- GPG public key and signing process;
- replication/federation/DR design;
- package retention and deletion policy.

## Data and security

- data classification;
- audit/document/log retention periods;
- S3 Object Lock requirement/mode;
- KMS key administrators/users;
- Aurora engine/size/backup retention;
- OpenSearch capacity, index retention, and admin role;
- Secrets Manager rotation policy;
- SIEM, incident, and alert destinations.

## Application

- final product name and branding assets;
- support model and product owner;
- package owner source of truth;
- lifecycle/support/approval taxonomy;
- visibility model;
- module/provider expected scale;
- initial pilot packages;
- expected traffic/publication rates;
- Terraform/OpenTofu minimum supported versions.

## GitHub/CI

- final UI/API repository names and visibility;
- GitHub organization;
- CODEOWNERS teams;
- environment names and approvers;
- OIDC provider ARN/trust constraints;
- vulnerability/license/IaC scanning tools;
- image signing/provenance requirements;
- branch protection and release policy.

## Resilience

- approved SLO/RTO/RPO;
- Aurora Global Database decision;
- warm versus rebuild OpenSearch decision;
- Route 53 failover ownership;
- DR ECS capacity;
- JFrog regional continuity approach;
- backup vault/account/retention requirements;
- planned DR exercise cadence.
