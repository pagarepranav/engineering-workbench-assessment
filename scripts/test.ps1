$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    $env:MAVEN_SKIP_RC = '1'
    & mvn '-B' "-Dmaven.repo.local=$projectRoot\.m2\repository" 'verify'
    if ($LASTEXITCODE -ne 0) { throw 'Verification failed; inspect target/surefire-reports' }
} finally { Pop-Location }
