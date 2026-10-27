# Run after a successful Maven verify. Packages source and the prebuilt JAR without local data or caches.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$projectRoot = Split-Path -Parent $PSScriptRoot
$bundleStage = Join-Path $projectRoot ('tmp\submission-' + [Guid]::NewGuid().ToString('N'))
$bundleRoot = Join-Path $bundleStage 'Pranav_Pagare_Assessment'
$bundleDist = Join-Path $projectRoot 'dist'
$bundleJar = Join-Path $projectRoot 'target\engineering-workbench-1.0.0.jar'
if (-not (Test-Path -LiteralPath $bundleJar)) { throw 'Build the project with mvn verify first.' }
New-Item -ItemType Directory -Force -Path $bundleRoot,(Join-Path $bundleRoot 'app'),$bundleDist | Out-Null
foreach ($bundleDirectory in @('src','docs','scripts')) {
    Copy-Item -LiteralPath (Join-Path $projectRoot $bundleDirectory) -Destination $bundleRoot -Recurse -Force
}
foreach ($bundleName in @('start.md','START_HERE.md','README.md','pom.xml','Dockerfile','compose.yaml','.gitignore','.dockerignore')) {
    Copy-Item -LiteralPath (Join-Path $projectRoot $bundleName) -Destination $bundleRoot -Force
}
Copy-Item -LiteralPath $bundleJar -Destination (Join-Path $bundleRoot 'app\engineering-workbench-1.0.0.jar')
Copy-Item -LiteralPath $bundleJar -Destination (Join-Path $bundleDist 'engineering-workbench-1.0.0.jar') -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'start.md') -Destination (Join-Path $bundleDist 'start.md') -Force
$bundleSums = Get-ChildItem -LiteralPath $bundleRoot -Recurse -File -Force | Sort-Object FullName | ForEach-Object {
    $bundleRelative = $_.FullName.Substring($bundleRoot.Length + 1).Replace('\','/')
    (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() + '  ' + $bundleRelative
}
[System.IO.File]::WriteAllLines((Join-Path $bundleRoot 'SHA256SUMS.txt'),$bundleSums,[System.Text.UTF8Encoding]::new($false))
foreach ($bundleKind in @('full','source')) {
    $bundleTemporary = Join-Path $bundleDist ([Guid]::NewGuid().ToString('N') + '.zip')
    $bundleArchive = [System.IO.Compression.ZipFile]::Open($bundleTemporary,[System.IO.Compression.ZipArchiveMode]::Create)
    try {
        Get-ChildItem -LiteralPath $bundleRoot -Recurse -File -Force | ForEach-Object {
            $bundleRelative = $_.FullName.Substring($bundleRoot.Length + 1).Replace('\','/')
            if ($bundleKind -eq 'full' -or ($bundleRelative -notlike 'app/*' -and $bundleRelative -ne 'SHA256SUMS.txt')) {
                $bundleEntry = if ($bundleKind -eq 'full') { 'Pranav_Pagare_Assessment/' + $bundleRelative } else { $bundleRelative }
                [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($bundleArchive,$_.FullName,$bundleEntry,[System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
            }
        }
    } finally { $bundleArchive.Dispose() }
    $bundleOutput = if ($bundleKind -eq 'full') { 'Pranav_Pagare_Assessment.zip' } else { 'engineering-workbench-source.zip' }
    Move-Item -LiteralPath $bundleTemporary -Destination (Join-Path $bundleDist $bundleOutput) -Force
    Write-Output (Join-Path $bundleDist $bundleOutput)
}
$bundleArtifactSums = @('Pranav_Pagare_Assessment.zip','engineering-workbench-source.zip','engineering-workbench-1.0.0.jar') | ForEach-Object {
    (Get-FileHash -LiteralPath (Join-Path $bundleDist $_) -Algorithm SHA256).Hash.ToLowerInvariant() + '  ' + $_
}
[System.IO.File]::WriteAllLines((Join-Path $bundleDist 'SHA256SUMS.txt'),$bundleArtifactSums,[System.Text.UTF8Encoding]::new($false))
