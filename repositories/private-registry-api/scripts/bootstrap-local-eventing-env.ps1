[CmdletBinding()]
param(
    [string]$OutputPath = (Join-Path $PSScriptRoot "..\.env.eventing"),
    [string]$ArtifactoryEnvironmentPath = (Join-Path $PSScriptRoot "..\.env.artifactory"),
    [string]$Origin = "",
    [string]$SubscriptionId = "registry-local-acceptance"
)

$ErrorActionPreference = "Stop"
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
if (Test-Path -LiteralPath $resolvedOutput) {
    Write-Host "Registry eventing environment already exists at $resolvedOutput"
    exit 0
}

$resolvedArtifactoryEnvironment = [System.IO.Path]::GetFullPath($ArtifactoryEnvironmentPath)
if ([string]::IsNullOrWhiteSpace($Origin) -and (Test-Path -LiteralPath $resolvedArtifactoryEnvironment)) {
    $hostnameLine = Get-Content -LiteralPath $resolvedArtifactoryEnvironment |
        Where-Object { $_ -match "^REGISTRY_JFROG_HOSTNAME=" } |
        Select-Object -First 1
    if ($hostnameLine) {
        $Origin = $hostnameLine.Substring("REGISTRY_JFROG_HOSTNAME=".Length).Trim()
    }
}
if ([string]::IsNullOrWhiteSpace($Origin)) {
    throw "Supply -Origin or create .env.artifactory with REGISTRY_JFROG_HOSTNAME first."
}
if ([string]::IsNullOrWhiteSpace($SubscriptionId)) {
    throw "SubscriptionId cannot be empty."
}

$secretBytes = [byte[]]::new(32)
[System.Security.Cryptography.RandomNumberGenerator]::Fill($secretBytes)
$secret = [Convert]::ToHexString($secretBytes).ToLowerInvariant()
$lines = @(
    "REGISTRY_WEBHOOK_SECRET=$secret",
    "REGISTRY_WEBHOOK_ORIGIN=$Origin",
    "REGISTRY_WEBHOOK_SUBSCRIPTION_ID=$SubscriptionId"
)
[System.IO.File]::WriteAllLines($resolvedOutput, $lines, [System.Text.UTF8Encoding]::new($false))
Write-Host "Wrote Registry eventing environment to $resolvedOutput"
