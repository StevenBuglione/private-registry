# Terraform registry mirror smoke test

This root resolves `cloudposse/label/null` 0.25.0 through the JFrog module cache and
`hashicorp/null` 3.3.0 exclusively through the JFrog provider network mirror.

Run it from PowerShell with a token supplied only through the environment:

```powershell
$env:TF_TOKEN_trialwbgt07_jfrog_io = '<access-token>'
$env:TF_CLI_CONFIG_FILE = (Resolve-Path .\mirror.tfrc).Path
terraform init
terraform validate
terraform plan '-out=mirror.tfplan'
```

Do not apply the plan. The direct provider installation method explicitly excludes
`hashicorp/null`, preventing a public-registry fallback.
