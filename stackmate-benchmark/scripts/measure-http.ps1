param(
    [Parameter(Mandatory = $true)]
    [string]$BaselineJar,

    [Parameter(Mandatory = $true)]
    [string]$DeterministicJar,

    [ValidateRange(1, 10000)]
    [int]$Iterations = 100,

    [ValidateRange(0, 1000)]
    [int]$WarmupIterations = 20,

    [ValidateRange(5, 180)]
    [int]$ReadinessTimeoutSeconds = 45,

    [ValidateRange(1, 60)]
    [int]$RequestTimeoutSeconds = 10,

    [ValidateRange(1, 10000)]
    [int]$DeduplicationMaximumEntries = 512,

    [string]$OutputDirectory = "target/http-measurement"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http

function Resolve-RequiredFile([string]$Value, [string]$Name) {
    $resolved = [System.IO.Path]::GetFullPath($Value)
    if (-not [System.IO.File]::Exists($resolved)) {
        throw "$Name does not exist: $resolved"
    }
    return $resolved
}

function Get-Percentile([double[]]$Values, [double]$Rate) {
    $sorted = @($Values | Sort-Object)
    $index = [Math]::Ceiling($Rate * $sorted.Count) - 1
    return $sorted[[Math]::Max(0, [Math]::Min($index, $sorted.Count - 1))]
}

function Get-RandomLoopbackPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try {
        return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
    }
    finally {
        $listener.Stop()
    }
}

function Get-Sha256([string]$Value) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
        return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace("-", "").ToLowerInvariant()
    }
    finally {
        $sha.Dispose()
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
        & taskkill.exe /PID $Process.Id /T /F 2>$null | Out-Null
    }
    $Process.WaitForExit(5000) | Out-Null
}

function Send-Request([System.Net.Http.HttpClient]$Client, [string]$Uri) {
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $response = $Client.GetAsync($Uri).GetAwaiter().GetResult()
    try {
        $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $watch.Stop()
        return [ordered]@{
            elapsedMilliseconds = $watch.Elapsed.TotalMilliseconds
            statusCode = [int]$response.StatusCode
            bodySha256 = Get-Sha256 $body
        }
    }
    finally {
        $response.Dispose()
    }
}

function Measure-Endpoint(
    [System.Net.Http.HttpClient]$Client,
    [string]$Uri,
    [int]$ExpectedStatusCode
) {
    for ($index = 0; $index -lt $WarmupIterations; $index++) {
        Send-Request $Client $Uri | Out-Null
    }
    $samples = [System.Collections.Generic.List[double]]::new()
    $statuses = [System.Collections.Generic.HashSet[int]]::new()
    $hashes = [System.Collections.Generic.HashSet[string]]::new()
    $totalWatch = [System.Diagnostics.Stopwatch]::StartNew()
    for ($index = 0; $index -lt $Iterations; $index++) {
        $sample = Send-Request $Client $Uri
        $samples.Add($sample.elapsedMilliseconds)
        $statuses.Add($sample.statusCode) | Out-Null
        $hashes.Add($sample.bodySha256) | Out-Null
    }
    $totalWatch.Stop()
    if ($statuses.Count -ne 1 -or -not $statuses.Contains($ExpectedStatusCode)) {
        throw "Unexpected status code for $Uri; expected $ExpectedStatusCode, observed $(@($statuses) -join ', ')"
    }
    if ($hashes.Count -ne 1) {
        throw "Response body was not deterministic for $Uri; observed $($hashes.Count) hashes"
    }
    return [ordered]@{
        warmupIterations = $WarmupIterations
        measuredIterations = $Iterations
        medianMilliseconds = Get-Percentile $samples.ToArray() 0.50
        p95Milliseconds = Get-Percentile $samples.ToArray() 0.95
        throughputRequestsPerSecond = $Iterations / $totalWatch.Elapsed.TotalSeconds
        statusCodes = @($statuses | Sort-Object)
        bodySha256 = @($hashes | Sort-Object)
        samplesMilliseconds = $samples.ToArray()
    }
}

function Measure-Mode([hashtable]$Mode, [string]$RunDirectory, [string]$JavaExecutable) {
    $port = Get-RandomLoopbackPort
    $log = Join-Path $RunDirectory ("{0}.log" -f $Mode.Name)
    $errorLog = Join-Path $RunDirectory ("{0}.err.log" -f $Mode.Name)
    $arguments = @("-jar", ('"{0}"' -f $Mode.Jar), "--server.address=127.0.0.1", "--server.port=$port", "--spring.main.banner-mode=off", "--stackmate.deduplication.maximum-entries=$DeduplicationMaximumEntries") + $Mode.Arguments
    $process = Start-Process -FilePath $JavaExecutable `
        -ArgumentList $arguments `
        -WindowStyle Hidden `
        -RedirectStandardOutput $log `
        -RedirectStandardError $errorLog `
        -PassThru
    $client = [System.Net.Http.HttpClient]::new()
    $client.Timeout = [TimeSpan]::FromSeconds($RequestTimeoutSeconds)
    $baseUri = "http://127.0.0.1:$port"
    try {
        $deadline = [DateTime]::UtcNow.AddSeconds($ReadinessTimeoutSeconds)
        $ready = $false
        while ([DateTime]::UtcNow -lt $deadline -and -not $process.HasExited) {
            try {
                $probe = Send-Request $client "$baseUri/benchmark/success"
                if ($probe.statusCode -eq 200) {
                    $ready = $true
                    break
                }
            }
            catch [System.Net.Http.HttpRequestException] {
            }
            catch [System.Threading.Tasks.TaskCanceledException] {
            }
            Start-Sleep -Milliseconds 50
            $process.Refresh()
        }
        if (-not $ready) {
            $stderr = if (Test-Path $errorLog) { Get-Content -Raw $errorLog } else { "" }
            throw "readiness timed out or process exited for $($Mode.Name): $stderr"
        }
        return [ordered]@{
            name = $Mode.Name
            jar = $Mode.Jar
            arguments = @($Mode.Arguments)
            success = Measure-Endpoint $client "$baseUri/benchmark/success" 200
            unhandledFailure = Measure-Endpoint $client "$baseUri/benchmark/failure" 500
        }
    }
    finally {
        $client.Dispose()
        Stop-ProcessTree $process
    }
}

$baseline = Resolve-RequiredFile $BaselineJar "BaselineJar"
$deterministic = Resolve-RequiredFile $DeterministicJar "DeterministicJar"
$java = Resolve-RequiredFile (Join-Path $env:JAVA_HOME "bin/java.exe") "JAVA_HOME java executable"
$runDirectory = [System.IO.Path]::GetFullPath((Join-Path $OutputDirectory ([DateTime]::UtcNow.ToString("yyyyMMdd-HHmmss-fff"))))
[System.IO.Directory]::CreateDirectory($runDirectory) | Out-Null
$modes = @(
    @{ Name = "without-stackmate"; Jar = $baseline; Arguments = @() },
    @{ Name = "disabled-starter"; Jar = $deterministic; Arguments = @("--stackmate.enabled=false") },
    @{ Name = "enabled-deterministic"; Jar = $deterministic; Arguments = @() }
)

$results = foreach ($mode in $modes) { Measure-Mode $mode $runDirectory $java }
foreach ($endpoint in @("success", "unhandledFailure")) {
    $statusSignatures = @($results | ForEach-Object { $_[$endpoint].statusCodes -join "," } | Select-Object -Unique)
    $bodySignatures = @($results | ForEach-Object { $_[$endpoint].bodySha256 -join "," } | Select-Object -Unique)
    if ($statusSignatures.Count -ne 1 -or $bodySignatures.Count -ne 1) {
        throw "$endpoint behavior differs between modes"
    }
}
$report = [ordered]@{
    measuredAtUtc = [DateTime]::UtcNow.ToString("O")
    command = $MyInvocation.Line
    networkPolicy = "HTTP requests target random 127.0.0.1 ports only"
    measurementPolicy = "One persistent HttpClient per mode; sequential warmups and measured requests"
    deduplication = [ordered]@{
        configuredMaximumEntries = $DeduplicationMaximumEntries
        retainedMemoryBytes = $null
        note = "Entry count is configuration-bounded; retained bytes are intentionally not estimated"
    }
    java = (& $java -version 2>&1 | Out-String).Trim()
    os = [System.Environment]::OSVersion.VersionString
    results = @($results)
}
$reportPath = Join-Path $runDirectory "http-report.json"
$json = $report | ConvertTo-Json -Depth 10
$json | Set-Content -LiteralPath $reportPath -Encoding utf8
$json
Write-Output "Report: $reportPath"
