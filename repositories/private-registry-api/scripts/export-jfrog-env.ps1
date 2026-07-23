[CmdletBinding()]
param(
    [string]$ServerId = "registry",
    [string]$OutputPath = (Join-Path $PSScriptRoot "..\.env.artifactory")
)

$ErrorActionPreference = "Stop"
$configToken = (jf config export $ServerId | Select-Object -Last 1).Trim()
if ([string]::IsNullOrWhiteSpace($configToken)) {
    throw "JFrog CLI did not return a configuration token for '$ServerId'."
}

$decoded = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($configToken)) | ConvertFrom-Json
if ([string]::IsNullOrWhiteSpace($decoded.accessToken) -or [string]::IsNullOrWhiteSpace($decoded.artifactoryUrl)) {
    throw "The selected JFrog CLI configuration does not contain an Artifactory URL and access token."
}

$artifactoryUri = [Uri]$decoded.artifactoryUrl
$hostname = $artifactoryUri.Host
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
$lines = @(
    "REGISTRY_JFROG_HOSTNAME=$hostname",
    "JFROG_ACCESS_TOKEN=$($decoded.accessToken)"
)
[System.IO.File]::WriteAllLines($resolvedOutput, $lines, [System.Text.UTF8Encoding]::new($false))
Write-Host "Wrote JFrog environment to $resolvedOutput"
