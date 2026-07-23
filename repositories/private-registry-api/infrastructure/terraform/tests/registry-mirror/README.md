# Terraform registry mirror smoke test

This root resolves `cloudposse/label/null` 0.25.0 through the JFrog module cache and
`hashicorp/null` 3.3.0 exclusively through the JFrog provider network mirror.

`artifacts.example.invalid` is a deliberate non-routable placeholder. Replace it in
`main.tf` and `mirror.tfrc` with the destination JPD hostname. Update the `TF_TOKEN_*`
variable name below by replacing the hostname's periods with underscores.

Run it from PowerShell with a token supplied only through the environment:

```powershell
$env:TF_TOKEN_artifacts_example_invalid = '<access-token>'
$env:TF_CLI_CONFIG_FILE = (Resolve-Path .\mirror.tfrc).Path
terraform init
terraform validate
terraform plan '-out=mirror.tfplan'
```

Do not apply the plan. The direct provider installation method explicitly excludes
`hashicorp/null`, preventing a public-registry fallback.
