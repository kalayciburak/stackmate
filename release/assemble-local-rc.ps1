param(
    [string]$Version = "0.1.0-rc.2",
    [string]$OutputRoot = "target/release"
)

$ErrorActionPreference = "Stop"
$repository = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$allowlistPath = Join-Path $PSScriptRoot "release-artifacts.json"
$allowlist = Get-Content -LiteralPath $allowlistPath -Raw | ConvertFrom-Json
$publicArtifacts = @($allowlist.publicArtifacts)
$expected = @(
    "stackmate-parent",
    "stackmate-core",
    "stackmate-spring-boot-autoconfigure",
    "stackmate-spring-boot-starter"
)

if (@(Compare-Object $expected $publicArtifacts).Count -ne 0) {
    throw "Release allowlist differs from the four frozen public coordinates"
}
if ($allowlist.release -ne $Version) {
    throw "Release allowlist version $($allowlist.release) does not match $Version"
}

[xml]$rootPom = Get-Content -LiteralPath (Join-Path $repository "pom.xml") -Raw
if ($rootPom.project.version -ne $Version) {
    throw "Root POM version $($rootPom.project.version) does not match $Version"
}

$resolvedOutputRoot = if ([System.IO.Path]::IsPathFullyQualified($OutputRoot)) {
    [System.IO.Path]::GetFullPath($OutputRoot)
} else {
    [System.IO.Path]::GetFullPath((Join-Path $repository $OutputRoot))
}
$bundle = [System.IO.Path]::GetFullPath((Join-Path $resolvedOutputRoot "stackmate-$Version"))
if ([System.IO.Directory]::Exists($bundle) -or [System.IO.File]::Exists($bundle)) {
    throw "Release bundle already exists: $bundle"
}

$artifacts = Join-Path $bundle "artifacts"
$sbom = Join-Path $bundle "sbom"
$checksums = Join-Path $bundle "checksums"
$reports = Join-Path $bundle "reports"
foreach ($directory in @($bundle, $artifacts, $sbom, $checksums, $reports)) {
    [System.IO.Directory]::CreateDirectory($directory) | Out-Null
}

function Copy-RequiredFile([string]$Source, [string]$Destination) {
    if (-not [System.IO.File]::Exists($Source)) {
        throw "Required release file is missing: $Source"
    }
    [System.IO.File]::Copy($Source, $Destination, $false)
}

function Read-ZipEntries([string]$Path) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        return @($archive.Entries | ForEach-Object { $_.FullName })
    }
    finally {
        $archive.Dispose()
    }
}

function Assert-CleanArchive([string]$Path, [bool]$RequireLegalFiles) {
    $entries = @(Read-ZipEntries $Path)
    $forbidden = @(
        "(?i)(^|/)inference/",
        "(?i)(^|/)research/",
        "(?i)(^|/)datasets?/",
        "(?i)(^|/)reports?/",
        "(?i)adapter_model",
        "(?i)\.(gguf|safetensors|py|onnx|pt|pth|ckpt)$"
    )
    foreach ($entry in $entries) {
        foreach ($pattern in $forbidden) {
            if ($entry -match $pattern) {
                throw "Forbidden release entry $entry in $Path"
            }
        }
    }
    if ($RequireLegalFiles) {
        if ($entries -notcontains "META-INF/LICENSE" -or $entries -notcontains "META-INF/NOTICE") {
            throw "Main JAR lacks META-INF/LICENSE or META-INF/NOTICE: $Path"
        }
    }
    return $entries.Count
}

function Assert-CleanPom([string]$Path) {
    $content = Get-Content -LiteralPath $Path -Raw
    $forbidden = @(
        "stackmate-inference-",
        "stackmate-spring-boot-autoconfigure-local",
        "stackmate-spring-boot-starter-local",
        "jlama",
        "spring-ai",
        "langchain4j"
    )
    $inspection = $content
    if ([System.IO.Path]::GetFileName($Path) -eq "pom.xml") {
        $match = [regex]::Match(
            $content,
            "(?s)<dependencyManagement>.*?</dependencyManagement>"
        )
        if ($match.Success) {
            $inspection = $match.Value
        }
    }
    foreach ($value in $forbidden) {
        if ($inspection -match [regex]::Escape($value)) {
            throw "Forbidden dependency surface $value in $Path"
        }
    }
}

function Assert-CleanSbom([string]$Path) {
    $document = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    if ($document.bomFormat -ne "CycloneDX") {
        throw "Unexpected SBOM format in $Path"
    }
    $coordinates = @(
        $document.components |
            ForEach-Object { "$($_.group):$($_.name)" }
    ) -join "`n"
    if ($coordinates -match "(?i)(stackmate-inference|autoconfigure-local|starter-local|jlama)") {
        throw "Research dependency found in public SBOM $Path"
    }
}

$artifactAudit = [System.Collections.Generic.List[object]]::new()
Copy-RequiredFile `
    (Join-Path $repository "pom.xml") `
    (Join-Path $artifacts "stackmate-parent-$Version.pom")
Assert-CleanPom (Join-Path $repository "pom.xml")

foreach ($artifactId in $publicArtifacts | Where-Object { $_ -ne "stackmate-parent" }) {
    $module = Join-Path $repository $artifactId
    $target = Join-Path $module "target"
    $mainJar = Join-Path $target "$artifactId-$Version.jar"
    $sourcesJar = Join-Path $target "$artifactId-$Version-sources.jar"
    $javadocJar = Join-Path $target "$artifactId-$Version-javadoc.jar"
    $pom = Join-Path $module "pom.xml"
    $bom = Join-Path $target "bom.json"

    Copy-RequiredFile $mainJar (Join-Path $artifacts ([System.IO.Path]::GetFileName($mainJar)))
    Copy-RequiredFile $sourcesJar (Join-Path $artifacts ([System.IO.Path]::GetFileName($sourcesJar)))
    Copy-RequiredFile $javadocJar (Join-Path $artifacts ([System.IO.Path]::GetFileName($javadocJar)))
    $sourcesEntries = Assert-CleanArchive $sourcesJar $false
    $javadocEntries = Assert-CleanArchive $javadocJar $false
    Copy-RequiredFile $pom (Join-Path $artifacts "$artifactId-$Version.pom")
    Copy-RequiredFile $bom (Join-Path $sbom "$artifactId-$Version-bom.json")

    Assert-CleanPom $pom
    Assert-CleanSbom $bom
    $artifactAudit.Add(
        [ordered]@{
            artifactId = $artifactId
            mainJarEntries = Assert-CleanArchive $mainJar $true
            sourcesJarEntries = $sourcesEntries
            javadocJarEntries = $javadocEntries
            attachmentNote = if ($artifactId -eq "stackmate-spring-boot-starter") {
                "Central-compatible explanatory placeholder sources and Javadoc attached"
            } else {
                "Sources and Javadoc attached"
            }
        }
    )
}

Copy-RequiredFile (Join-Path $repository "LICENSE") (Join-Path $bundle "LICENSE")
Copy-RequiredFile (Join-Path $repository "NOTICE") (Join-Path $bundle "NOTICE")
Copy-RequiredFile `
    (Join-Path $repository "release/release-artifacts.json") `
    (Join-Path $reports "release-artifacts.json")
Copy-RequiredFile `
    (Join-Path $repository "release/public-api-inventory.json") `
    (Join-Path $reports "public-api-inventory.json")
Copy-RequiredFile `
    (Join-Path $repository "release/reproducibility-result.json") `
    (Join-Path $reports "reproducibility-result.json")
$releaseNotes = Join-Path $repository "docs/phase-6-1/release-notes.md"
$verificationCommands = Join-Path $repository "docs/phase-6-1/verification-commands.md"
Copy-RequiredFile $releaseNotes (Join-Path $bundle "release-notes.md")
Copy-RequiredFile $verificationCommands (Join-Path $bundle "verification-commands.md")

$auditReport = [ordered]@{
    schemaVersion = "1"
    version = $Version
    status = "PASS"
    publicArtifacts = $publicArtifacts
    publicationPerformed = $false
    remoteCi = "UNVERIFIED"
    artifactAudit = @($artifactAudit)
}
$auditReport | ConvertTo-Json -Depth 8 | Set-Content `
    -LiteralPath (Join-Path $reports "release-audit.json") `
    -Encoding utf8

$files = @(
    Get-ChildItem -LiteralPath $bundle -Recurse -File |
        Where-Object { $_.FullName -notlike "$checksums*" } |
        Sort-Object { [System.IO.Path]::GetRelativePath($bundle, $_.FullName) }
)
$manifest = foreach ($file in $files) {
    $relative = [System.IO.Path]::GetRelativePath($bundle, $file.FullName).Replace('\', '/')
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $file.FullName).Hash.ToLowerInvariant()
    [ordered]@{
        path = $relative
        size = $file.Length
        sha256 = $hash
    }
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content `
    -LiteralPath (Join-Path $checksums "manifest.json") `
    -Encoding utf8
$sumLines = $manifest | ForEach-Object { "$($_.sha256)  $($_.path)" }
[System.IO.File]::WriteAllText(
    (Join-Path $checksums "SHA256SUMS"),
    ($sumLines -join "`n") + "`n",
    [System.Text.UTF8Encoding]::new($false)
)

Write-Output "LOCAL_RC_BUNDLE_READY=$bundle"
