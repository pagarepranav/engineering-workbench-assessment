$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    $env:MAVEN_SKIP_RC = '1'
    & mvn '-B' "-Dmaven.repo.local=$projectRoot\.m2\repository" 'package' '-DskipTests'
    if ($LASTEXITCODE -ne 0) { throw 'Maven build failed' }
    & java '-jar' 'target/engineering-workbench-1.0.0.jar'
    if ($LASTEXITCODE -ne 0) { throw 'Application exited with an error' }
} finally { Pop-Location }
