param(
    [Parameter(Mandatory = $true)]
    [string]$BaseRevision,

    [string]$HeadRevision = "HEAD",

    [string]$OutputDirectory = "target/dependency-review"
)

$ErrorActionPreference = "Stop"
$repository = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$output = [System.IO.Path]::GetFullPath((Join-Path $repository $OutputDirectory))
$wrapper = Join-Path $repository $(if ($IsWindows) { "mvnw.cmd" } else { "mvnw" })
[System.IO.Directory]::CreateDirectory($output) | Out-Null

function Resolve-Revision([string]$Revision) {
    $resolved = @(& git -C $repository rev-parse "$Revision^{commit}" 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Cannot resolve Git revision $Revision`: $($resolved -join ' ')"
    }
    return [string]$resolved[0]
}

function Dependency-Kind([System.Xml.XmlNode]$Node) {
    $current = $Node.ParentNode
    while ($null -ne $current) {
        if ($current.LocalName -eq "plugin") {
            return "PLUGIN_DEPENDENCY"
        }
        if ($current.LocalName -eq "dependencyManagement") {
            return "DEPENDENCY_MANAGEMENT"
        }
        $current = $current.ParentNode
    }
    return "PROJECT_DEPENDENCY"
}

function Child-Text([System.Xml.XmlNode]$Node, [string]$Name, [string]$Default) {
    $child = @($Node.ChildNodes | Where-Object { $_.LocalName -eq $Name } | Select-Object -First 1)
    if ($child.Count -eq 0 -or [string]::IsNullOrWhiteSpace($child[0].InnerText)) {
        return $Default
    }
    return $child[0].InnerText.Trim()
}

function Dependencies-At([string]$Revision) {
    $result = [System.Collections.Generic.List[object]]::new()
    $pomPaths = @(& git -C $repository ls-tree -r --name-only $Revision) |
        Where-Object { $_ -eq "pom.xml" -or $_.EndsWith("/pom.xml") }
    foreach ($pomPath in $pomPaths) {
        $content = @(& git -C $repository show "${Revision}:$pomPath" 2>&1)
        if ($LASTEXITCODE -ne 0) {
            throw "Cannot read $pomPath at $Revision"
        }
        [xml]$document = $content -join "`n"
        foreach ($dependency in $document.SelectNodes("//*[local-name()='dependency']")) {
            $groupId = Child-Text $dependency "groupId" ""
            $artifactId = Child-Text $dependency "artifactId" ""
            if ($groupId -eq "" -or $artifactId -eq "") {
                continue
            }
            $result.Add(
                [ordered]@{
                    pom = $pomPath
                    kind = Dependency-Kind $dependency
                    groupId = $groupId
                    artifactId = $artifactId
                    version = Child-Text $dependency "version" "managed"
                    scope = Child-Text $dependency "scope" "compile"
                    optional = Child-Text $dependency "optional" "false"
                }
            )
        }
    }
    return @($result | Sort-Object pom, kind, groupId, artifactId, version, scope, optional)
}

function Dependency-Key([object]$Dependency) {
    return @(
        $Dependency.pom,
        $Dependency.kind,
        $Dependency.groupId,
        $Dependency.artifactId,
        $Dependency.version,
        $Dependency.scope,
        $Dependency.optional
    ) -join "|"
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

$base = Resolve-Revision $BaseRevision
$head = Resolve-Revision $HeadRevision
$baseDependencies = @(Dependencies-At $base)
$headDependencies = @(Dependencies-At $head)
$baseByKey = @{}
$headByKey = @{}
foreach ($dependency in $baseDependencies) {
    $baseByKey[(Dependency-Key $dependency)] = $dependency
}
foreach ($dependency in $headDependencies) {
    $headByKey[(Dependency-Key $dependency)] = $dependency
}
$added = @($headByKey.Keys | Where-Object { -not $baseByKey.ContainsKey($_) } | ForEach-Object { $headByKey[$_] })
$removed = @($baseByKey.Keys | Where-Object { -not $headByKey.ContainsKey($_) } | ForEach-Object { $baseByKey[$_] })

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
$runtimeTrees = [System.Collections.Generic.List[object]]::new()
$violations = [System.Collections.Generic.List[object]]::new()
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
            $violations.Add(
                [ordered]@{
                    artifactId = $artifactId
                    forbidden = $forbidden
                }
            )
        }
    }
    $runtimeTrees.Add(
        [ordered]@{
            artifactId = $artifactId
            file = [System.IO.Path]::GetFileName($treePath)
            sha256 = Get-Sha256 $treePath
        }
    )
}

$report = [ordered]@{
    schemaVersion = "1"
    status = if ($violations.Count -eq 0) { "PASS" } else { "FAIL" }
    baseCommit = $base
    headCommit = $head
    declaredDependencies = [ordered]@{
        baseCount = $baseDependencies.Count
        headCount = $headDependencies.Count
        added = @($added | Sort-Object pom, kind, groupId, artifactId)
        removed = @($removed | Sort-Object pom, kind, groupId, artifactId)
    }
    publicRuntimeTrees = @($runtimeTrees)
    policyViolations = @($violations)
    publicationPerformed = $false
}
$reportPath = Join-Path $output "dependency-review.json"
$json = $report | ConvertTo-Json -Depth 8
[System.IO.File]::WriteAllText(
    $reportPath,
    $json + "`n",
    [System.Text.UTF8Encoding]::new($false)
)

Write-Output "DEPENDENCY_REVIEW_STATUS=$($report.status)"
Write-Output "DEPENDENCY_REVIEW_BASE=$base"
Write-Output "DEPENDENCY_REVIEW_HEAD=$head"
Write-Output "DEPENDENCY_REVIEW_ADDED=$($added.Count)"
Write-Output "DEPENDENCY_REVIEW_REMOVED=$($removed.Count)"
if ($violations.Count -ne 0) {
    throw "Public runtime dependency policy violations: $($violations.Count)"
}
