param(
    [Parameter(Mandatory = $true)]
    [string]$Platform,

    [Parameter(Mandatory = $true)]
    [string]$Version,

    [Parameter(Mandatory = $true)]
    [string]$BundlePath,

    [string]$OutputDirectory = "target/qualification"
)

$ErrorActionPreference = "Stop"
$repository = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$output = [System.IO.Path]::GetFullPath((Join-Path $repository $OutputDirectory))
$bundle = [System.IO.Path]::GetFullPath((Join-Path $repository $BundlePath))
$wrapper = Join-Path $repository $(if ($IsWindows) { "mvnw.cmd" } else { "mvnw" })

if (-not [System.IO.Directory]::Exists($bundle)) {
    throw "Qualification bundle does not exist: $bundle"
}
[System.IO.Directory]::CreateDirectory($output) | Out-Null

function Write-Utf8Json([string]$Path, [object]$Value, [int]$Depth = 8) {
    $json = $Value | ConvertTo-Json -Depth $Depth
    [System.IO.File]::WriteAllText(
        $Path,
        $json + "`n",
        [System.Text.UTF8Encoding]::new($false)
    )
}

function Get-Sha256([string]$Path) {
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Get-StreamSha256([System.IO.Stream]$Stream) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return [Convert]::ToHexString($sha.ComputeHash($Stream)).ToLowerInvariant()
    }
    finally {
        $sha.Dispose()
    }
}

function Invoke-Maven([string[]]$Arguments) {
    & $wrapper @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Maven failed with exit code $LASTEXITCODE`: $($Arguments -join ' ')"
    }
}

$dependencyTrees = [System.Collections.Generic.List[object]]::new()
$forbiddenRuntime = @(
    "stackmate-inference",
    "stackmate-spring-boot-autoconfigure-local",
    "stackmate-spring-boot-starter-local",
    "com.github.tjake",
    "testcontainers",
    "org.postgresql",
    "com.h2database",
    "logback-classic",
    "log4j-core"
)
foreach ($artifactId in @(
    "stackmate-core",
    "stackmate-spring-boot-autoconfigure",
    "stackmate-spring-boot-starter"
)) {
    $treePath = Join-Path $output "$artifactId-runtime-dependencies.txt"
    Invoke-Maven @(
        "--batch-mode",
        "--no-transfer-progress",
        "-pl",
        $artifactId,
        "-Dscope=runtime",
        "-DoutputFile=$treePath",
        "dependency:tree"
    )
    $tree = Get-Content -LiteralPath $treePath -Raw
    foreach ($forbidden in $forbiddenRuntime) {
        if ($tree -match [regex]::Escape($forbidden)) {
            throw "Forbidden runtime dependency $forbidden in $artifactId"
        }
    }
    $dependencyTrees.Add(
        [ordered]@{
            artifactId = $artifactId
            file = [System.IO.Path]::GetFileName($treePath)
            sha256 = Get-Sha256 $treePath
        }
    )
}

$testSuites = [System.Collections.Generic.List[object]]::new()
$reportFiles = Get-ChildItem -LiteralPath $repository -Filter "TEST-*.xml" -File -Recurse |
    Where-Object {
        $normalized = $_.FullName.Replace('\', '/')
        $normalized -match "/target/(surefire|failsafe)-reports/"
    }
foreach ($reportFile in $reportFiles) {
    [xml]$document = Get-Content -LiteralPath $reportFile.FullName -Raw
    $suite = $document.testsuite
    $relative = [System.IO.Path]::GetRelativePath($repository, $reportFile.FullName).Replace('\', '/')
    $module = $relative.Substring(0, $relative.IndexOf("/target/"))
    $kind = if ($relative.Contains("/failsafe-reports/")) { "failsafe" } else { "surefire" }
    $testSuites.Add(
        [ordered]@{
            module = $module
            kind = $kind
            name = [string]$suite.name
            tests = [int]$suite.tests
            failures = [int]$suite.failures
            errors = [int]$suite.errors
            skipped = [int]$suite.skipped
            timeSeconds = [string]$suite.time
        }
    )
}

$testTotals = [System.Collections.Generic.List[object]]::new()
foreach ($kind in @("surefire", "failsafe")) {
    $selected = @($testSuites | Where-Object { $_.kind -eq $kind })
    $tests = 0
    $failures = 0
    $errors = 0
    $skipped = 0
    foreach ($suite in $selected) {
        $tests += [int]$suite.tests
        $failures += [int]$suite.failures
        $errors += [int]$suite.errors
        $skipped += [int]$suite.skipped
    }
    $testTotals.Add(
        [ordered]@{
            kind = $kind
            suites = $selected.Count
            tests = $tests
            failures = $failures
            errors = $errors
            skipped = $skipped
        }
    )
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$artifactContents = [System.Collections.Generic.List[object]]::new()
$artifactDirectory = Join-Path $bundle "artifacts"
$mainJars = Get-ChildItem -LiteralPath $artifactDirectory -Filter "*.jar" -File |
    Where-Object {
        $_.Name -notlike "*-sources.jar" -and
        $_.Name -notlike "*-javadoc.jar"
    }
foreach ($jar in $mainJars | Sort-Object Name) {
    $archive = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
    try {
        $entries = [System.Collections.Generic.List[object]]::new()
        foreach ($entry in $archive.Entries | Where-Object { -not $_.FullName.EndsWith('/') }) {
            $stream = $entry.Open()
            try {
                $hash = Get-StreamSha256 $stream
            }
            finally {
                $stream.Dispose()
            }
            $entries.Add(
                [ordered]@{
                    path = $entry.FullName
                    size = $entry.Length
                    sha256 = $hash
                }
            )
        }
        $artifactContents.Add(
            [ordered]@{
                file = $jar.Name
                size = $jar.Length
                sha256 = Get-Sha256 $jar.FullName
                entries = @($entries | Sort-Object path)
            }
        )
    }
    finally {
        $archive.Dispose()
    }
}

$apiInventoryPath = Join-Path $output "public-api.json"
if (-not [System.IO.File]::Exists($apiInventoryPath)) {
    throw "Generated API inventory is missing: $apiInventoryPath"
}
$apiInventory = Get-Content -LiteralPath $apiInventoryPath -Raw | ConvertFrom-Json
$bundleManifest = Join-Path $bundle "checksums/manifest.json"
$commit = (& git -C $repository rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $commit -notmatch '^[0-9a-f]{40}$') {
    throw "Unable to resolve the checked-out qualification commit"
}
$javaExecutable = Join-Path $env:JAVA_HOME $(if ($IsWindows) { "bin/java.exe" } else { "bin/java" })
$java = [string]@(& $javaExecutable -version 2>&1)[0]
$maven = [string]@(& $wrapper --version)[0]
$operatingSystem = if ($IsWindows) {
    [System.Environment]::OSVersion.VersionString
} else {
    [string]@(& uname -a)[0]
}

$summary = [ordered]@{
    schemaVersion = "1"
    platform = $Platform
    commit = $commit
    version = $Version
    operatingSystem = $operatingSystem
    architecture = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString()
    java = $java
    maven = $maven
    testTotals = @($testTotals)
    testSuites = @($testSuites | Sort-Object module, kind, name)
    publicApi = [ordered]@{
        types = $apiInventory.publicTypeCount
        sha256 = $apiInventory.signatureSha256
    }
    bundle = [ordered]@{
        manifestSha256 = Get-Sha256 $bundleManifest
        sha256SumsSha256 = Get-Sha256 (Join-Path $bundle "checksums/SHA256SUMS")
    }
    dependencyTrees = @($dependencyTrees)
    artifactContents = @($artifactContents)
    publicationPerformed = $false
}
Write-Utf8Json (Join-Path $output "qualification-summary.json") $summary 12

Write-Output "QUALIFICATION_PLATFORM=$Platform"
Write-Output "QUALIFICATION_COMMIT=$commit"
Write-Output "QUALIFICATION_API_SHA256=$($apiInventory.signatureSha256)"
Write-Output "QUALIFICATION_BUNDLE_MANIFEST=$($summary.bundle.manifestSha256)"
