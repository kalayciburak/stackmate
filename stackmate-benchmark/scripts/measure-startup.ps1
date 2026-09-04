param(
    [Parameter(Mandatory = $true)]
    [string]$BaselineJar,

    [Parameter(Mandatory = $true)]
    [string]$DeterministicJar,

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

function Get-Percentile([long[]]$Values, [double]$Rate) {
    $sorted = @($Values | Sort-Object)
    $index = [Math]::Ceiling($Rate * $sorted.Count) - 1
    return $sorted[[Math]::Max(0, [Math]::Min($index, $sorted.Count - 1))]
}

function Read-SharedText([string]$Path) {
    if (-not [System.IO.File]::Exists($Path)) {
        return ""
    }
    $stream = [System.IO.File]::Open($Path, "Open", "Read", "ReadWrite")
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

function Stop-ProcessTree([System.Diagnostics.Process]$Process) {
    if ($null -eq $Process -or $Process.HasExited) {
        return
    }
    try {
        $Process.Kill($true)
    }
    catch {
        if ($IsWindows) {
            & taskkill.exe /PID $Process.Id /T /F 2>$null | Out-Null
        } else {
            $Process.Kill()
        }
    }
    $Process.WaitForExit(5000) | Out-Null
}

function Measure-Mode([hashtable]$Mode, [string]$RunDirectory, [string]$JavaExecutable) {
    $all = [System.Collections.Generic.List[long]]::new()
    $measured = [System.Collections.Generic.List[long]]::new()
    $total = $WarmupIterations + $Iterations
    for ($index = 0; $index -lt $total; $index++) {
        $log = Join-Path $RunDirectory ("{0}-{1:D2}.log" -f $Mode.Name, ($index + 1))
        $errorLog = Join-Path $RunDirectory ("{0}-{1:D2}.err.log" -f $Mode.Name, ($index + 1))
        $arguments = @("-jar", ('"{0}"' -f $Mode.Jar), "--server.address=127.0.0.1", "--server.port=0", "--spring.main.banner-mode=off") + $Mode.Arguments
        $watch = [System.Diagnostics.Stopwatch]::StartNew()
        $startOptions = @{
            FilePath = $JavaExecutable
            ArgumentList = $arguments
            RedirectStandardOutput = $log
            RedirectStandardError = $errorLog
            PassThru = $true
        }
        if ($IsWindows) {
            $startOptions.WindowStyle = "Hidden"
        }
        $process = Start-Process @startOptions
        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        $ready = $false
        try {
            while ([DateTime]::UtcNow -lt $deadline -and -not $process.HasExited) {
                if ((Read-SharedText $log) -match "Started .*Application in") {
                    $ready = $true
                    break
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
            Stop-ProcessTree $process
        }
    }
    return [ordered]@{
        name = $Mode.Name
        jar = $Mode.Jar
        arguments = @($Mode.Arguments)
        warmupIterations = $WarmupIterations
        measuredIterations = $Iterations
        minimumMilliseconds = ($measured | Measure-Object -Minimum).Minimum
        medianMilliseconds = Get-Percentile $measured.ToArray() 0.50
        p95Milliseconds = Get-Percentile $measured.ToArray() 0.95
        maximumMilliseconds = ($measured | Measure-Object -Maximum).Maximum
        allSamplesMilliseconds = $all.ToArray()
        measuredSamplesMilliseconds = $measured.ToArray()
    }
}

$baseline = Resolve-RequiredFile $BaselineJar "BaselineJar"
$deterministic = Resolve-RequiredFile $DeterministicJar "DeterministicJar"
$javaPath = Join-Path $env:JAVA_HOME $(if ($IsWindows) { "bin/java.exe" } else { "bin/java" })
$java = Resolve-RequiredFile $javaPath "JAVA_HOME java executable"
$runDirectory = [System.IO.Path]::GetFullPath((Join-Path $OutputDirectory ([DateTime]::UtcNow.ToString("yyyyMMdd-HHmmss-fff"))))
[System.IO.Directory]::CreateDirectory($runDirectory) | Out-Null
$modes = @(
    @{ Name = "without-stackmate"; Jar = $baseline; Arguments = @() },
    @{ Name = "disabled-starter"; Jar = $deterministic; Arguments = @("--stackmate.enabled=false") },
    @{ Name = "enabled-deterministic"; Jar = $deterministic; Arguments = @() }
)

$results = foreach ($mode in $modes) { Measure-Mode $mode $runDirectory $java }
$machine = if ($IsWindows) { Get-CimInstance Win32_ComputerSystem } else { $null }
$processor = if ($IsWindows) { Get-CimInstance Win32_Processor | Select-Object -First 1 } else { $null }
$processorName = if ($IsWindows) {
    $processor.Name
} else {
    [System.Runtime.InteropServices.RuntimeInformation]::ProcessArchitecture.ToString()
}
$logicalProcessors = if ($IsWindows) {
    $machine.NumberOfLogicalProcessors
} else {
    [System.Environment]::ProcessorCount
}
$memoryBytes = if ($IsWindows) { [long]$machine.TotalPhysicalMemory } else { $null }
$report = [ordered]@{
    measuredAtUtc = [DateTime]::UtcNow.ToString("O")
    command = $MyInvocation.Line
    warmupPolicy = "Fresh JVM per sample; first $WarmupIterations runs per mode excluded; OS file cache not cleared"
    java = (& $java -version 2>&1 | Out-String).Trim()
    os = [System.Runtime.InteropServices.RuntimeInformation]::OSDescription
    cpu = $processorName
    logicalProcessors = $logicalProcessors
    memoryBytes = $memoryBytes
    results = @($results)
}
$reportPath = Join-Path $runDirectory "startup-report.json"
$json = $report | ConvertTo-Json -Depth 8
$json | Set-Content -LiteralPath $reportPath -Encoding utf8
$json
Write-Output "Report: $reportPath"
