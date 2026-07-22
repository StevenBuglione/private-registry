# Registry Entra acceptance identities

This Terraform root creates only Microsoft Entra directory objects: one single-tenant OIDC application, its service principal and delegated `User.Read` grant, two APM groups, an administrator group, and three unlicensed cloud-only users. It does not create Azure subscription resources or modify Conditional Access.

Use the signed-in Azure CLI identity to run Terraform. Supply the tenant through an ignored `terraform.tfvars` or `TF_VAR_entra_tenant_id`; never commit credentials or Terraform state.

After apply, copy the sensitive outputs into the ignored Compose environment file with `scripts/export-compose-env.ps1`. The generated file is intentionally outside source control.
