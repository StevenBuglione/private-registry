[CmdletBinding()]
param(
  [string]$ManifestPath = (Join-Path $PSScriptRoot '..\apm-packages.auto.tfvars.json')
)

$ErrorActionPreference = 'Stop'

if (-not $env:JFROG_ACCESS_TOKEN) {
  throw 'JFROG_ACCESS_TOKEN must be set in the current process.'
}

$document = Get-Content -Raw -LiteralPath $ManifestPath | ConvertFrom-Json -AsHashtable
$assignments = $document.apm_assignments

foreach ($assignment in $assignments.GetEnumerator()) {
  $apmId = $assignment.Key
  foreach ($target in $assignment.Value.propertyTargets) {
    & jf rt sp $target "apm.id=$apmId" --url="https://$($env:REGISTRY_JFROG_HOSTNAME ?? 'trialwbgt07.jfrog.io')/artifactory" --access-token=$env:JFROG_ACCESS_TOKEN
    if ($LASTEXITCODE -ne 0) {
      throw "Failed to set apm.id on $target"
    }
  }
}
