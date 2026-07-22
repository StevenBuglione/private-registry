# Networking, DNS, Identity, and Authorization

## VPC layout

Production uses three Availability Zones with isolated tiers:

```text
VPC /16
├── ingress subnets      internal ALB
├── application subnets ECS tasks and interface endpoints
├── data subnets        Aurora and OpenSearch
└── optional egress     NAT gateways when centralized egress is unavailable
```

No ECS task, database instance, or OpenSearch node receives a public IP.

## Routing and egress

Preferred order:

1. VPC endpoints for AWS services.
2. JFrog PrivateLink or private routed connectivity.
3. Centralized enterprise egress for approved IdP/source-control endpoints.
4. Per-VPC NAT only as an explicit fallback.

Document every required outbound hostname and deny general internet egress.

## DNS

Use an internal name such as:

```text
registry.internal.example
```

Route 53 private hosted zones integrate with corporate DNS through Resolver endpoints and forwarding rules. The JFrog hostname remains separate because CLI registry discovery and browser catalog access are different trust boundaries.

## Browser authentication

The ALB performs OIDC authentication on the HTTPS listener. The backend must verify the ALB-signed `x-amzn-oidc-data` assertion before trusting claims. Direct access to ECS targets is blocked by security groups.

The OIDC app must allow:

```text
https://registry.internal.example/oauth2/idpresponse
```

The OIDC client secret is sensitive Terraform state unless injected through an alternative approved process; state access must be tightly restricted.

## Authorization

Recommended roles:

```text
registry-reader
package-author
package-maintainer
package-approver
security-reviewer
catalog-administrator
platform-administrator
auditor
```

Authorization decisions consider:

- identity groups;
- package visibility;
- owning team/business unit;
- lifecycle state;
- risk tier;
- requested operation.

Protected APIs enforce authorization server-side. Search must not reveal hidden package names or counts to unauthorized users.

## Workload identity

- ECS services use dedicated task roles.
- GitHub Actions assumes AWS roles using OIDC.
- JFrog service identities are scoped by repository/action.
- Database migration credentials are separate from application credentials.
- Provider signing uses a dedicated isolated identity.

## Security groups

- ALB ingress: TCP 443 from approved corporate CIDRs only.
- UI/API ingress: TCP 8080 from ALB security group only.
- RDS Proxy ingress: TCP 5432 from API/worker security groups.
- Aurora ingress: TCP 5432 from RDS Proxy and controlled migration task only.
- OpenSearch ingress: TCP 443 from API/worker security groups.
- endpoints ingress: TCP 443 from ECS security groups.
- outbound rules are narrowed after the initial integration inventory is complete.
