[CmdletBinding()]
param(
    [string] $Endpoint = 'http://127.0.0.1:8080/api/v1/internal/webhooks/jfrog',
    [string] $Repository = 'iac-catalog-release-local',
    [string] $Path = 'v1/providers/hashicorp/null/3.2.4/catalog-manifest.json'
)

$ErrorActionPreference = 'Stop'
$ApiRoot = Split-Path -Parent $PSScriptRoot
$EventingFile = Join-Path $ApiRoot '.env.eventing'
if (-not (Test-Path -LiteralPath $EventingFile)) {
    throw 'Create .env.eventing with scripts/bootstrap-local-eventing-env.ps1 first.'
}

$Configuration = @{}
foreach ($Line in Get-Content -LiteralPath $EventingFile) {
    if ($Line -match '^([^#=]+)=(.*)$') {
        $Configuration[$Matches[1].Trim()] = $Matches[2]
    }
}

$Secret = $Configuration['REGISTRY_WEBHOOK_SECRET']
$Origin = $Configuration['REGISTRY_WEBHOOK_ORIGIN']
$Subscription = $Configuration['REGISTRY_WEBHOOK_SUBSCRIPTION_ID']
if ([string]::IsNullOrWhiteSpace($Secret) -or
    [string]::IsNullOrWhiteSpace($Origin) -or
    [string]::IsNullOrWhiteSpace($Subscription)) {
    throw '.env.eventing is missing required webhook configuration.'
}

$EventId = 'local-acceptance-' + [guid]::NewGuid().ToString('N')
$Payload = @{
    event_type = 'artifact.property.changed'
    occurred_at = [DateTimeOffset]::UtcNow.ToString('o')
    data = @{
        repo_key = $Repository
        path = $Path
    }
} | ConvertTo-Json -Compress -Depth 4
$PayloadBytes = [Text.Encoding]::UTF8.GetBytes($Payload)
$KeyBytes = [Text.Encoding]::UTF8.GetBytes($Secret)
$Hmac = [Security.Cryptography.HMACSHA256]::new($KeyBytes)
try {
    $Signature = [Convert]::ToHexString($Hmac.ComputeHash($PayloadBytes)).ToLowerInvariant()
}
finally {
    $Hmac.Dispose()
}

$Headers = @{
    'X-JFrog-Signature' = 'sha256=' + $Signature
    'X-JFrog-Origin' = $Origin
    'X-JFrog-Subscription-Id' = $Subscription
    'X-JFrog-Event-Id' = $EventId
    'X-Correlation-Id' = $EventId
}

Invoke-RestMethod -Method Post -Uri $Endpoint -ContentType 'application/json' -Headers $Headers -Body $PayloadBytes
