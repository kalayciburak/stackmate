param(
    [string]$Version = "0.1.0-rc.2",
    [string]$SignatureOutput = "release/public-api-baseline.txt",
    [string]$InventoryOutput = "release/public-api-inventory.json"
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    throw "JAVA_HOME must point to JDK 21"
}

$repository = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$jarTool = Join-Path $env:JAVA_HOME "bin/jar.exe"
$javapTool = Join-Path $env:JAVA_HOME "bin/javap.exe"
if (-not [System.IO.File]::Exists($jarTool)) {
    $jarTool = Join-Path $env:JAVA_HOME "bin/jar"
}
if (-not [System.IO.File]::Exists($javapTool)) {
    $javapTool = Join-Path $env:JAVA_HOME "bin/javap"
}

$artifacts = @(
    "stackmate-core",
    "stackmate-spring-boot-autoconfigure",
    "stackmate-spring-boot-starter"
)
$jars = foreach ($artifactId in $artifacts) {
    $path = Join-Path $repository "$artifactId/target/$artifactId-$Version.jar"
    if (-not [System.IO.File]::Exists($path)) {
        throw "Public API input JAR is missing: $path"
    }
    $path
}
$classpath = $jars -join [System.IO.Path]::PathSeparator
$signature = [System.Collections.Generic.List[string]]::new()
$types = [System.Collections.Generic.List[object]]::new()

function Api-Status([string]$TypeName) {
    if ($TypeName -like "io.github.kalayciburak.stackmate.configuration.*") {
        return "CONFIGURATION_API"
    }
    $spis = @(
        "io.github.kalayciburak.stackmate.analysis.DiagnosticAnalyzer",
        "io.github.kalayciburak.stackmate.capture.ServletExceptionCaptureHandler",
        "io.github.kalayciburak.stackmate.deduplication.DeduplicationStore",
        "io.github.kalayciburak.stackmate.export.IncidentExporter",
        "io.github.kalayciburak.stackmate.output.ConsoleRenderer",
        "io.github.kalayciburak.stackmate.privacy.Sanitizer"
    )
    if ($spis -contains $TypeName) {
        return "EXTENSION_SPI"
    }
    return "STABLE_API"
}

foreach ($index in 0..($artifacts.Count - 1)) {
    $artifactId = $artifacts[$index]
    $jar = $jars[$index]
    $classes = @(
        & $jarTool tf $jar |
            Where-Object {
                $_ -like "*.class" -and
                $_ -notlike "META-INF/versions/*" -and
                $_ -notlike "*module-info.class" -and
                $_ -notlike "*package-info.class"
            } |
            ForEach-Object { $_.Substring(0, $_.Length - 6).Replace('/', '.') } |
            Sort-Object -Unique
    )
    foreach ($className in $classes) {
        $output = @(& $javapTool -public -constants -s -classpath $classpath $className 2>&1)
        if ($LASTEXITCODE -ne 0) {
            throw "javap failed for $className`: $($output -join ' ')"
        }
        $declaration = $output | Where-Object { $_ -match '^public ' } | Select-Object -First 1
        if ($null -eq $declaration) {
            continue
        }
        $signature.Add("## $artifactId/$className")
        foreach ($line in $output | Where-Object { $_ -notmatch '^Compiled from ' }) {
            $signature.Add($line.TrimEnd())
        }
        $signature.Add("")
        $kind = if ($declaration -match '\binterface\b') {
            "interface"
        }
        elseif ($declaration -match '\benum\b') {
            "enum"
        }
        elseif ($declaration -match '\brecord\b') {
            "record"
        }
        else {
            "class"
        }
        $types.Add(
            [ordered]@{
                artifactId = $artifactId
                type = $className
                kind = $kind
                classification = Api-Status $className
            }
        )
    }
}

$signaturePath = [System.IO.Path]::GetFullPath((Join-Path $repository $SignatureOutput))
$inventoryPath = [System.IO.Path]::GetFullPath((Join-Path $repository $InventoryOutput))
foreach ($path in @($signaturePath, $inventoryPath)) {
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($path)) | Out-Null
}
$signatureText = ($signature -join "`n").TrimEnd() + "`n"
[System.IO.File]::WriteAllText(
    $signaturePath,
    $signatureText,
    [System.Text.UTF8Encoding]::new($false)
)
$signatureHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $signaturePath).Hash.ToLowerInvariant()
$inventory = [ordered]@{
    schemaVersion = "1"
    release = $Version
    signatureFile = [System.IO.Path]::GetRelativePath($repository, $signaturePath).Replace('\', '/')
    signatureSha256 = $signatureHash
    publicTypeCount = $types.Count
    classifications = @($types)
}
$inventoryText = $inventory | ConvertTo-Json -Depth 6
[System.IO.File]::WriteAllText(
    $inventoryPath,
    $inventoryText + "`n",
    [System.Text.UTF8Encoding]::new($false)
)
Write-Output "PUBLIC_API_TYPES=$($types.Count)"
Write-Output "PUBLIC_API_SHA256=$signatureHash"
