param(
    [string]$Version = "0.1.0-rc.2",
    [string]$OutputRoot = "target/file-repository-verification"
)

$ErrorActionPreference = "Stop"
$repository = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$resolvedOutputRoot = if ([System.IO.Path]::IsPathFullyQualified($OutputRoot)) {
    [System.IO.Path]::GetFullPath($OutputRoot)
} else {
    [System.IO.Path]::GetFullPath((Join-Path $repository $OutputRoot))
}
$runDirectory = Join-Path $resolvedOutputRoot ([DateTime]::UtcNow.ToString("yyyyMMdd-HHmmss-fff"))
$fileRepository = Join-Path $runDirectory "repository"
$consumerRepository = Join-Path $runDirectory "consumer-m2"
$settingsPath = Join-Path $runDirectory "settings.xml"
$wrapper = Join-Path $repository $(if ($IsWindows) { "mvnw.cmd" } else { "mvnw" })
foreach ($directory in @($runDirectory, $fileRepository, $consumerRepository)) {
    [System.IO.Directory]::CreateDirectory($directory) | Out-Null
}

function Invoke-Maven([string[]]$Arguments) {
    & $wrapper @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Maven failed with exit code $LASTEXITCODE`: $($Arguments -join ' ')"
    }
}

function Get-Sha256([string]$Path) {
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

$fileRepositoryUri = [Uri]::new($fileRepository).AbsoluteUri
Invoke-Maven @(
    "--batch-mode",
    "--no-transfer-progress",
    "-Ppublic-release",
    "-pl",
    "stackmate-core,stackmate-spring-boot-autoconfigure,stackmate-spring-boot-starter",
    "-am",
    "-DaltDeploymentRepository=stackmate-file::$fileRepositoryUri",
    "deploy"
)

$groupPath = "io/github/kalayciburak"
$expected = @(
    "$groupPath/stackmate-parent/$Version/stackmate-parent-$Version.pom",
    "$groupPath/stackmate-core/$Version/stackmate-core-$Version.pom",
    "$groupPath/stackmate-core/$Version/stackmate-core-$Version.jar",
    "$groupPath/stackmate-core/$Version/stackmate-core-$Version-sources.jar",
    "$groupPath/stackmate-core/$Version/stackmate-core-$Version-javadoc.jar",
    "$groupPath/stackmate-spring-boot-autoconfigure/$Version/stackmate-spring-boot-autoconfigure-$Version.pom",
    "$groupPath/stackmate-spring-boot-autoconfigure/$Version/stackmate-spring-boot-autoconfigure-$Version.jar",
    "$groupPath/stackmate-spring-boot-autoconfigure/$Version/stackmate-spring-boot-autoconfigure-$Version-sources.jar",
    "$groupPath/stackmate-spring-boot-autoconfigure/$Version/stackmate-spring-boot-autoconfigure-$Version-javadoc.jar",
    "$groupPath/stackmate-spring-boot-starter/$Version/stackmate-spring-boot-starter-$Version.pom",
    "$groupPath/stackmate-spring-boot-starter/$Version/stackmate-spring-boot-starter-$Version.jar",
    "$groupPath/stackmate-spring-boot-starter/$Version/stackmate-spring-boot-starter-$Version-sources.jar",
    "$groupPath/stackmate-spring-boot-starter/$Version/stackmate-spring-boot-starter-$Version-javadoc.jar"
)
$artifacts = [System.Collections.Generic.List[object]]::new()
$checksums = [System.Collections.Generic.List[object]]::new()
foreach ($relative in $expected) {
    $path = Join-Path $fileRepository $relative
    if (-not [System.IO.File]::Exists($path)) {
        throw "File-repository artifact is missing: $relative"
    }
    $artifacts.Add(
        [ordered]@{
            path = $relative
            size = (Get-Item -LiteralPath $path).Length
            sha256 = Get-Sha256 $path
        }
    )
    foreach ($algorithm in @("MD5", "SHA1")) {
        $suffix = $algorithm.ToLowerInvariant()
        $checksumPath = "$path.$suffix"
        if (-not [System.IO.File]::Exists($checksumPath)) {
            throw "File-repository checksum is missing: $relative.$suffix"
        }
        $expectedChecksum = (Get-FileHash -Algorithm $algorithm -LiteralPath $path).Hash.ToLowerInvariant()
        $actualChecksum = (Get-Content -LiteralPath $checksumPath -Raw).Trim().ToLowerInvariant()
        if ($actualChecksum -ne $expectedChecksum) {
            throw "Invalid $algorithm checksum for $relative"
        }
        $checksums.Add(
            [ordered]@{
                path = "$relative.$suffix"
                algorithm = $algorithm
                value = $actualChecksum
            }
        )
    }
}

$settings = @"
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <profiles>
    <profile>
      <id>stackmate-file-repository</id>
      <repositories>
        <repository>
          <id>stackmate-file</id>
          <url>$fileRepositoryUri</url>
          <releases><enabled>true</enabled></releases>
          <snapshots><enabled>false</enabled></snapshots>
        </repository>
        <repository>
          <id>central</id>
          <url>https://repo.maven.apache.org/maven2</url>
          <releases><enabled>true</enabled></releases>
          <snapshots><enabled>false</enabled></snapshots>
        </repository>
      </repositories>
      <pluginRepositories>
        <pluginRepository>
          <id>central-plugins</id>
          <url>https://repo.maven.apache.org/maven2</url>
          <releases><enabled>true</enabled></releases>
          <snapshots><enabled>false</enabled></snapshots>
        </pluginRepository>
      </pluginRepositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>stackmate-file-repository</activeProfile>
  </activeProfiles>
</settings>
"@
[System.IO.File]::WriteAllText(
    $settingsPath,
    $settings,
    [System.Text.UTF8Encoding]::new($false)
)

Invoke-Maven @(
    "--batch-mode",
    "--no-transfer-progress",
    "-s",
    $settingsPath,
    "-Dmaven.repo.local=$consumerRepository",
    "-f",
    (Join-Path $repository "release/consumer/pom.xml"),
    "-Dstackmate.version=$Version",
    "clean",
    "verify"
)

$resolutionMarker = Join-Path $consumerRepository "$groupPath/stackmate-spring-boot-starter/$Version/_remote.repositories"
if (-not [System.IO.File]::Exists($resolutionMarker)) {
    throw "External consumer did not record starter resolution"
}
$marker = Get-Content -LiteralPath $resolutionMarker -Raw
if ($marker -notmatch "stackmate-file") {
    throw "External consumer did not resolve the starter from stackmate-file"
}

$report = [ordered]@{
    schemaVersion = "1"
    status = "PASS"
    version = $Version
    deployedCoordinates = @(
        "io.github.kalayciburak:stackmate-parent:$Version",
        "io.github.kalayciburak:stackmate-core:$Version",
        "io.github.kalayciburak:stackmate-spring-boot-autoconfigure:$Version",
        "io.github.kalayciburak:stackmate-spring-boot-starter:$Version"
    )
    requiredArtifacts = @($artifacts)
    verifiedChecksums = @($checksums)
    externalConsumer = [ordered]@{
        repository = "stackmate-file"
        cleanLocalRepository = $true
        contractTests = 6
        packagedProcessTests = 1
    }
    publicationPerformed = $false
    credentialsUsed = $false
}
$reportPath = Join-Path $runDirectory "file-repository-report.json"
$json = $report | ConvertTo-Json -Depth 6
[System.IO.File]::WriteAllText(
    $reportPath,
    $json + "`n",
    [System.Text.UTF8Encoding]::new($false)
)
Write-Output "FILE_REPOSITORY_VERIFICATION=PASS"
Write-Output "FILE_REPOSITORY_REPORT=$reportPath"
