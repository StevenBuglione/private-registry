# JFrog Terraform public mirrors

This Terraform root creates the public module and provider pull-through caches used by
the private registry proof. Backend coordinates are deliberately excluded from source.
Copy `backend.hcl.example` to the ignored `backend.hcl` and supply the real Azure Storage
coordinates for the target environment.

Set credentials only in the current process:

```powershell
$env:JFROG_URL = 'https://trialwbgt07.jfrog.io/artifactory'
$env:JFROG_ACCESS_TOKEN = '<access-token>'
```

Then initialize, review, and apply:

```powershell
terraform init -backend-config=backend.hcl
terraform validate
terraform plan '-out=jfrog.tfplan'
terraform apply jfrog.tfplan
```

Both repositories have `prevent_destroy` enabled. Removing them intentionally requires
an explicit reviewed code change. Do not commit access tokens or plan files.

## APM access control

Copy `apm-packages.tfvars.example.json` to the ignored
`apm-packages.auto.tfvars.json` and supply the target tenant, member object IDs, JFrog
members, and package patterns. Keeping this assignment file out of Git prevents a
reusable starter from publishing personal identifiers while the required input prevents
an accidental empty plan from removing existing access assignments.

The root creates matching Entra and JFrog groups and package-scoped read permissions.
After applying Terraform, apply the corresponding Artifactory metadata property:

```powershell
.\scripts\set-apm-properties.ps1
```

The `apm.id` property is searchable governance metadata; the JFrog permission target is
the security boundary. Configure JFrog SSO group provisioning before relying on
automatic membership for additional users.
