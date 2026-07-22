[CmdletBinding()]
param(
    [ValidateSet('format', 'local', 'pr', 'nightly', 'sonar-input')]
    [string] $Mode = 'local'
)

$ErrorActionPreference = 'Stop'
$ApiRoot = Split-Path -Parent $PSScriptRoot
$Tasks = switch ($Mode) {
    'format' { @('spotlessApply') }
    'local' { @('qualityLocal') }
    'pr' { @('qualityPr') }
    'nightly' { @('qualityNightly') }
    'sonar-input' { @('classes', 'testClasses', 'jacocoTestReport') }
}

Push-Location -LiteralPath $ApiRoot
try {
    & .\gradlew.bat --no-daemon --stacktrace @Tasks
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle quality gate failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}
