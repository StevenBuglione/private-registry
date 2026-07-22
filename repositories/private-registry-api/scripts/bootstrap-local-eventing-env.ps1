[CmdletBinding()]
param(
    [string]$OutputPath = (Join-Path $PSScriptRoot "..\.env.eventing")
)

$ErrorActionPreference = "Stop"
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
if (Test-Path -LiteralPath $resolvedOutput) {
    Write-Host "Registry eventing environment already exists at $resolvedOutput"
    exit 0
}

$secretBytes = [byte[]]::new(32)
[System.Security.Cryptography.RandomNumberGenerator]::Fill($secretBytes)
$secret = [Convert]::ToHexString($secretBytes).ToLowerInvariant()
$lines = @(
    "REGISTRY_WEBHOOK_SECRET=$secret",
    "REGISTRY_WEBHOOK_ORIGIN=trialwbgt07.jfrog.io",
    "REGISTRY_WEBHOOK_SUBSCRIPTION_ID=registry-local-acceptance"
)
[System.IO.File]::WriteAllLines($resolvedOutput, $lines, [System.Text.UTF8Encoding]::new($false))
Write-Host "Wrote Registry eventing environment to $resolvedOutput"
