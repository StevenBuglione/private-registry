# Environment stack

Initialize with the environment-specific backend file:

```bash
terraform init -backend-config=backend.hcl
terraform plan -out=tfplan
terraform apply tfplan
```

Do not commit `backend.hcl`, `terraform.tfvars`, plans, or state. Supply `oidc_client_secret` through the protected CI environment (`TF_VAR_oidc_client_secret`) rather than a checked-in file.

The first apply should keep `deploy_application_services = false`. Enable it only after immutable images and production adapters are ready.
