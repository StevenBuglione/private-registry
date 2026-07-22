[CmdletBinding()]
param(
    [string]$OutputPath = (Join-Path $PSScriptRoot "..\..\..\..\.env.identity-test")
)

$ErrorActionPreference = "Stop"
$terraformRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$outputs = terraform "-chdir=$terraformRoot" output -json | ConvertFrom-Json
if ($LASTEXITCODE -ne 0) {
    throw "Unable to read identity-test Terraform outputs."
}

$oidc = $outputs.oidc.value
$groups = $outputs.authorization_groups.value
$secret = $outputs.oidc_client_secret.value
$users = $outputs.test_users.value

$lines = @(
    "REGISTRY_ENTRA_TENANT_ID=$($oidc.tenant_id)",
    "REGISTRY_OIDC_CLIENT_ID=$($oidc.client_id)",
    "REGISTRY_OIDC_CLIENT_SECRET=$secret",
    "ENTRA_TENANT_ID=$($oidc.tenant_id)",
    "ENTRA_CLIENT_ID=$($oidc.client_id)",
    "ENTRA_CLIENT_SECRET=$secret",
    "REGISTRY_OIDC_ISSUER=$($oidc.issuer)",
    "REGISTRY_OIDC_AUTHORIZATION_URI=$($oidc.authorization_endpoint)",
    "REGISTRY_OIDC_TOKEN_URI=$($oidc.token_endpoint)",
    "REGISTRY_OIDC_USER_INFO_URI=$($oidc.user_info_endpoint)",
    "REGISTRY_OIDC_SCOPES=$($oidc.scopes)",
    "REGISTRY_ENTRA_APM_GROUPS=APM0000001:$($groups.APM9000001),APM0000002:$($groups.APM9000002)",
    "REGISTRY_ENTRA_ADMIN_GROUP_ID=$($groups.registry_admin)",
    "REGISTRY_E2E_APM_A_USERNAME=$($users.apm_a.user_principal_name)",
    "REGISTRY_E2E_APM_A_PASSWORD=$($users.apm_a.password)",
    "REGISTRY_E2E_APM_B_USERNAME=$($users.apm_b.user_principal_name)",
    "REGISTRY_E2E_APM_B_PASSWORD=$($users.apm_b.password)",
    "REGISTRY_E2E_ADMIN_USERNAME=$($users.admin.user_principal_name)",
    "REGISTRY_E2E_ADMIN_PASSWORD=$($users.admin.password)"
)

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
[System.IO.File]::WriteAllLines($resolvedOutput, $lines, [System.Text.UTF8Encoding]::new($false))
Write-Host "Wrote Registry identity environment to $resolvedOutput"
