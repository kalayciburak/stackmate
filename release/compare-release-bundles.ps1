param(
    [Parameter(Mandatory = $true)]
    [string]$First,

    [Parameter(Mandatory = $true)]
    [string]$Second,

    [Parameter(Mandatory = $true)]
    [string]$Report
)

$ErrorActionPreference = "Stop"
$firstRoot = [System.IO.Path]::GetFullPath($First)
$secondRoot = [System.IO.Path]::GetFullPath($Second)

if (-not [System.IO.Directory]::Exists($firstRoot)) {
    throw "First release bundle does not exist: $firstRoot"
}
if (-not [System.IO.Directory]::Exists($secondRoot)) {
    throw "Second release bundle does not exist: $secondRoot"
}

function File-Map([string]$Root) {
    $result = @{}
    Get-ChildItem -LiteralPath $Root -Recurse -File |
        Where-Object {
            $_.FullName -notmatch "[\\/]checksums[\\/](manifest\.json|SHA256SUMS)$" -and
            $_.FullName -notmatch "[\\/]reports[\\/]release-audit\.json$"
        } |
        ForEach-Object {
            $relative = [System.IO.Path]::GetRelativePath($Root, $_.FullName).Replace('\', '/')
            $result[$relative] = [ordered]@{
                size = $_.Length
                sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToLowerInvariant()
            }
        }
    return $result
}

$firstFiles = File-Map $firstRoot
$secondFiles = File-Map $secondRoot
$paths = @($firstFiles.Keys + $secondFiles.Keys | Sort-Object -Unique)
$comparisons = foreach ($path in $paths) {
    $left = $firstFiles[$path]
    $right = $secondFiles[$path]
    [ordered]@{
        path = $path
        firstSha256 = if ($null -eq $left) { $null } else { $left.sha256 }
        secondSha256 = if ($null -eq $right) { $null } else { $right.sha256 }
        identical = $null -ne $left -and $null -ne $right -and $left.sha256 -eq $right.sha256
    }
}
$different = @($comparisons | Where-Object { -not $_.identical })
$result = [ordered]@{
    schemaVersion = "1"
    status = if ($different.Count -eq 0) { "PASS" } else { "FAIL" }
    comparedFiles = $comparisons.Count
    differences = $different.Count
    files = @($comparisons)
}
$reportPath = [System.IO.Path]::GetFullPath($Report)
$parent = [System.IO.Path]::GetDirectoryName($reportPath)
[System.IO.Directory]::CreateDirectory($parent) | Out-Null
$result | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $reportPath -Encoding utf8
$result | ConvertTo-Json -Depth 3
if ($different.Count -ne 0) {
    exit 1
}
