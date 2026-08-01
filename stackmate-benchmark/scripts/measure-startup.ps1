param(
    [Parameter(Mandatory = $true)]
    [string]$BaselineJar,

    [Parameter(Mandatory = $true)]
    [string]$DeterministicJar,

    [Parameter(Mandatory = $true)]
    [string]$LocalJar,

    [string]$ModelPath = "",

    [ValidateRange(3, 100)]
    [int]$Iterations = 10,

    [ValidateRange(0, 20)]
    [int]$WarmupIterations = 2,

    [ValidateRange(10, 180)]
    [int]$TimeoutSeconds = 60,

    [string]$OutputDirectory = "target/startup-measurement"
)

$ErrorActionPreference = "Stop"

function Resolve-RequiredFile([string]$Value, [string]$Name) {
    $resolved = [System.IO.Path]::GetFullPath($Value)
    if (-not [System.IO.File]::Exists($resolved)) {
        throw "$Name does not exist: $resolved"
    }
    return $resolved
}

function Percentile([long[]]$Values, [double]$Rate) {
    $sorted = @($Values | Sort-Object)
    $index = [Math]::Ceiling($Rate * $sorted.Count) - 1
    return $sorted[[Math]::Max(0, [Math]::Min($index, $sorted.Count - 1))]
}

function Read-SharedText([string]$Path) {
    if (-not [System.IO.File]::Exists($Path)) {
        return ""
    }
    $stream = [System.IO.File]::Open(
        $Path,
        [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::Read,
        [System.IO.FileShare]::ReadWrite
    )
    try {
        $reader = [System.IO.StreamReader]::new($stream)
        try {
            return $reader.ReadToEnd()
        }
        finally {
            $reader.Dispose()
        }
    }
    finally {
        $stream.Dispose()
    }
}

function Measure-Mode([hashtable]$Mode, [string]$RunDirectory) {
    $all = [System.Collections.Generic.List[long]]::new()
    $measured = [System.Collections.Generic.List[long]]::new()
    $total = $WarmupIterations + $Iterations
    for ($index = 0; $index -lt $total; $index++) {
        $log = Join-Path $RunDirectory ("{0}-{1:D2}.log" -f $Mode.Name, ($index + 1))
        $errorLog = Join-Path $RunDirectory ("{0}-{1:D2}.err.log" -f $Mode.Name, ($index + 1))
        $arguments = @("-jar", $Mode.Jar, "--server.port=0", "--spring.main.banner-mode=off") + $Mode.Arguments
        $watch = [System.Diagnostics.Stopwatch]::StartNew()
        $process = Start-Process -FilePath (Join-Path $env:JAVA_HOME "bin/java.exe") `
            -ArgumentList $arguments `
            -WindowStyle Hidden `
            -RedirectStandardOutput $log `
            -RedirectStandardError $errorLog `
            -PassThru
        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        $ready = $false
        try {
            while ([DateTime]::UtcNow -lt $deadline -and -not $process.HasExited) {
                if ([System.IO.File]::Exists($log)) {
                    $content = Read-SharedText $log
                    if ($content -match "Started .*Application in") {
                        $ready = $true
                        break
                    }
                }
                Start-Sleep -Milliseconds 25
                $process.Refresh()
            }
            if (-not $ready) {
                $stderr = Read-SharedText $errorLog
                throw "startup timed out or exited for $($Mode.Name): $stderr"
            }
            $elapsed = $watch.ElapsedMilliseconds
            $all.Add($elapsed)
            if ($index -ge $WarmupIterations) {
                $measured.Add($elapsed)
            }
        }
        finally {
            if (-not $process.HasExited) {
                $process.Kill($true)
                $process.WaitForExit(5000) | Out-Null
            }
        }
    }
    return [ordered]@{
        name = $Mode.Name
        warmupIterations = $WarmupIterations
        measuredIterations = $Iterations
        minimumMilliseconds = ($measured | Measure-Object -Minimum).Minimum
        medianMilliseconds = Percentile $measured.ToArray() 0.50
        p95Milliseconds = Percentile $measured.ToArray() 0.95
        maximumMilliseconds = ($measured | Measure-Object -Maximum).Maximum
        allSamplesMilliseconds = $all.ToArray()
        measuredSamplesMilliseconds = $measured.ToArray()
    }
}

$baseline = Resolve-RequiredFile $BaselineJar "BaselineJar"
$deterministic = Resolve-RequiredFile $DeterministicJar "DeterministicJar"
$local = Resolve-RequiredFile $LocalJar "LocalJar"
$model = if ([string]::IsNullOrWhiteSpace($ModelPath)) { "" } else { Resolve-Path $ModelPath | Select-Object -ExpandProperty Path }
$runDirectory = [System.IO.Path]::GetFullPath((Join-Path $OutputDirectory ([DateTime]::UtcNow.ToString("yyyyMMdd-HHmmss"))))
[System.IO.Directory]::CreateDirectory($runDirectory) | Out-Null

$modes = @(
    @{ Name = "without-stackmate"; Jar = $baseline; Arguments = @() },
    @{ Name = "deterministic-starter"; Jar = $deterministic; Arguments = @("--stackmate.mode=deterministic", "--stackmate.inference.enabled=false") }
)
if (-not [string]::IsNullOrWhiteSpace($model)) {
    $modes += @{ Name = "local-starter-lazy"; Jar = $local; Arguments = @("--stackmate.mode=hybrid", "--stackmate.inference.enabled=true", "--stackmate.inference.lazy-load=true", "--stackmate.inference.model-path=$model") }
    $modes += @{ Name = "local-adapter-eager"; Jar = $local; Arguments = @("--stackmate.mode=hybrid", "--stackmate.inference.enabled=true", "--stackmate.inference.lazy-load=false", "--stackmate.inference.model-path=$model") }
}

$results = foreach ($mode in $modes) { Measure-Mode $mode $runDirectory }
$machine = Get-CimInstance Win32_ComputerSystem
$processor = Get-CimInstance Win32_Processor | Select-Object -First 1
$report = [ordered]@{
    measuredAtUtc = [DateTime]::UtcNow.ToString("O")
    command = $MyInvocation.Line
    warmupPolicy = "Fresh JVM per sample; first $WarmupIterations runs per mode excluded; OS file cache not cleared"
    java = (& (Join-Path $env:JAVA_HOME "bin/java.exe") -version 2>&1 | Out-String).Trim()
    os = [System.Environment]::OSVersion.VersionString
    cpu = $processor.Name
    logicalProcessors = $machine.NumberOfLogicalProcessors
    memoryBytes = [long]$machine.TotalPhysicalMemory
    results = @($results)
    limitation = "local-adapter-eager initializes and validates the adapter; Jlama weights remain demand-loaded until inference"
}
$reportPath = Join-Path $runDirectory "startup-report.json"
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportPath -Encoding utf8
$report | ConvertTo-Json -Depth 8
Write-Output "Report: $reportPath"
