param(
    [ValidateRange(3, 20)]
    [int]$Iterations = 7,

    [ValidateRange(1, 10000)]
    [int]$MaximumEntries = 10000,

    [string]$OutputDirectory = "target/cache-memory-measurement"
)

$ErrorActionPreference = "Stop"

function Get-Percentile([long[]]$Values, [double]$Rate) {
    $sorted = @($Values | Sort-Object)
    $index = [Math]::Ceiling($Rate * $sorted.Count) - 1
    return $sorted[[Math]::Max(0, [Math]::Min($index, $sorted.Count - 1))]
}

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "../.."))
$java = [System.IO.Path]::GetFullPath((Join-Path $env:JAVA_HOME "bin/java.exe"))
$benchmarkClasses = Join-Path $repositoryRoot "stackmate-benchmark/target/classes"
$coreClasses = Join-Path $repositoryRoot "stackmate-core/target/classes"
$probeClass = Join-Path $benchmarkClasses "io/github/kalayciburak/stackmate/benchmark/performance/DeduplicationRetainedMemoryProbe.class"
foreach ($requiredPath in @($java, $probeClass)) {
    if (-not [System.IO.File]::Exists($requiredPath)) {
        throw "Required file does not exist: $requiredPath"
    }
}

$classpath = $benchmarkClasses + [System.IO.Path]::PathSeparator + $coreClasses
$samples = [System.Collections.Generic.List[object]]::new()
for ($index = 0; $index -lt $Iterations; $index++) {
    $output = & $java `
        -Xms128m `
        -Xmx128m `
        -XX:+UseSerialGC `
        -classpath $classpath `
        io.github.kalayciburak.stackmate.benchmark.performance.DeduplicationRetainedMemoryProbe `
        $MaximumEntries
    if ($LASTEXITCODE -ne 0) {
        throw "Memory probe process failed with exit code $LASTEXITCODE"
    }
    $sample = ($output | Select-Object -Last 1) | ConvertFrom-Json
    $samples.Add($sample)
}

$retainedSamples = @($samples | ForEach-Object { [long]$_.retainedBytes })
$report = [ordered]@{
    measuredAtUtc = [DateTime]::UtcNow.ToString("O")
    method = "Fresh JVM per sample; Serial GC; 128 MiB fixed heap; five explicit GC/finalization cycles before and after filling the store"
    limitation = "Runtime heap deltas are approximate and include JVM measurement noise; they are not an exact object-layout calculation"
    java = (& $java -version 2>&1 | Out-String).Trim()
    maximumEntries = $MaximumEntries
    fingerprintCharacters = 64
    iterations = $Iterations
    medianRetainedBytes = Get-Percentile $retainedSamples 0.50
    p95RetainedBytes = Get-Percentile $retainedSamples 0.95
    minimumRetainedBytes = ($retainedSamples | Measure-Object -Minimum).Minimum
    maximumRetainedBytes = ($retainedSamples | Measure-Object -Maximum).Maximum
    samples = @($samples)
}

$runDirectory = [System.IO.Path]::GetFullPath((Join-Path $OutputDirectory ([DateTime]::UtcNow.ToString("yyyyMMdd-HHmmss-fff"))))
[System.IO.Directory]::CreateDirectory($runDirectory) | Out-Null
$reportPath = Join-Path $runDirectory "cache-memory-report.json"
$json = $report | ConvertTo-Json -Depth 6
$json | Set-Content -LiteralPath $reportPath -Encoding utf8
$json
Write-Output "Report: $reportPath"
