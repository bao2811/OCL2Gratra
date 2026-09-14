[CmdletBinding()]
param(
    [switch]$EnableNeo4j,
    [switch]$ConfirmDisposableDatabase,
    [string]$Neo4jVersion = '',
    [string]$EnvFile = '',
    [switch]$CheckConfigurationOnly
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$runId = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N')
$runDir = Join-Path $root "ocl2cypher/target/experiments/$runId"
function Test-Neo4jConfiguration {
    foreach ($key in @('NEO4J_URI','NEO4J_DB','NEO4J_USER','NEO4J_PASSWORD')) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($key))) { throw "Missing $key" }
    }
    $uri = $null
    if (-not [Uri]::TryCreate($env:NEO4J_URI, [UriKind]::Absolute, [ref]$uri) -or
        $uri.Scheme -notin @('bolt','bolt+s','bolt+ssc','neo4j','neo4j+s','neo4j+ssc') -or
        [string]::IsNullOrWhiteSpace($uri.Host) -or -not [string]::IsNullOrEmpty($uri.UserInfo)) {
        throw 'Invalid Neo4j URI; use a real endpoint without embedded credentials.'
    }
}
function Test-Neo4jEndpoint {
    $uri = [Uri]$env:NEO4J_URI
    $port = if ($uri.IsDefaultPort -or $uri.Port -lt 1) { 7687 } else { $uri.Port }
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $connect = $client.ConnectAsync($uri.Host, $port)
        if (-not $connect.Wait(3000) -or -not $client.Connected) {
            throw "Neo4j endpoint $($uri.Host):$port is unavailable; start the server before the reproducibility gate."
        }
    } finally {
        $client.Dispose()
    }
}
$connectionKeys = @('NEO4J_URI','NEO4J_DB','NEO4J_USER','NEO4J_PASSWORD')
$savedConnection = @{}
foreach ($key in $connectionKeys) { $savedConnection[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
# Resolve before Push-Location: relative paths belong to the caller.
if ($EnvFile) { $EnvFile = (Resolve-Path -LiteralPath $EnvFile).Path }
$savedGeneric = [Environment]::GetEnvironmentVariable('OCL2CYPHER_RUN_NEO4J_E2E')
$savedCar = [Environment]::GetEnvironmentVariable('OCL2CYPHER_RUN_CARRENTAL_E2E')
$savedMedical = [Environment]::GetEnvironmentVariable('OCL2CYPHER_RUN_MEDICAL_E2E')

function Get-InputHashes {
    $paths = @((Join-Path $root 'pom.xml'), (Join-Path $root 'ocl2cypher/pom.xml'))
    foreach ($folder in @('ocl2cypher/src','ocl2cypher/scripts','examples/carrental/umlmm','examples/medical')) {
        $paths += @(Get-ChildItem -LiteralPath (Join-Path $root $folder) -File -Recurse | ForEach-Object FullName)
    }
    @($paths | Sort-Object -Unique | ForEach-Object {
        [ordered]@{ path = $_.Substring($root.Length + 1); sha256 = (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash }
    })
}
function Invoke-Gate([string]$Name, [string[]]$MavenArgs, [string]$Suite, [int]$ExpectedCases) {
    $reports = Join-Path $runDir $Name
    New-Item -ItemType Directory -Path $reports | Out-Null
    $started = Get-Date
    # Explicitly wired in ocl2cypher/pom.xml; do not assume a Surefire user property.
    & mvn @MavenArgs "-Docl2cypher.testReportsDirectory=$reports"
    $exit = $LASTEXITCODE
    $elapsed = ((Get-Date) - $started).TotalSeconds
    [ordered]@{exitCode=$exit; wallSeconds=$elapsed; note='Maven gate wall time, NOT query latency'} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $reports 'gate.json') -Encoding UTF8
    if ($exit -ne 0) { throw "$Name failed (exit $exit); inspect $reports" }
    $xmlFiles = @(Get-ChildItem -LiteralPath $reports -Filter 'TEST-*.xml')
    if ($xmlFiles.Count -eq 0) { throw "$Name produced no fresh XML reports" }
    $cases = @()
    foreach ($file in $xmlFiles) {
        [xml]$xml = Get-Content -Raw -LiteralPath $file.FullName
        $cases += @($xml.SelectNodes('//testcase'))
    }
    $failures = @($cases | Where-Object { $_.SelectSingleNode('failure') -or $_.SelectSingleNode('error') })
    if ($failures.Count -ne 0) { throw "$Name XML contains failures/errors" }
    if ($Suite) {
        $selected = @($cases | Where-Object { $_.classname -eq $Suite })
        if ($selected.Count -ne $ExpectedCases) { throw "$Name expected $ExpectedCases leaf cases, found $($selected.Count)" }
        if (@($selected | Where-Object { $_.SelectSingleNode('skipped') }).Count -ne 0) { throw "$Name contains skipped required cases" }
    }
}

Push-Location $root
try {
    if ($EnvFile) {
        $values = @{}
        foreach ($line in [IO.File]::ReadAllLines($EnvFile)) {
            if ($line -notmatch '^\s*(NEO4J_URI|NEO4J_DB|NEO4J_USER|NEO4J_PASSWORD)\s*=(.*)$') { continue }
            $key = $Matches[1]
            $value = $Matches[2].Trim()
            if ($values.ContainsKey($key)) { throw "Duplicate connection key: $key" }
            if ($value.StartsWith('"') -or $value.StartsWith("'")) {
                if ($value.Length -lt 2 -or $value[$value.Length - 1] -ne $value[0]) { throw "Unclosed quote for $key" }
                $value = $value.Substring(1, $value.Length - 2)
            }
            if ([string]::IsNullOrWhiteSpace($value)) { throw "Empty connection key: $key" }
            $values[$key] = $value
        }
        foreach ($key in $connectionKeys) {
            if (-not $values.ContainsKey($key)) { throw "EnvFile missing $key" }
        }
        foreach ($key in $connectionKeys) { [Environment]::SetEnvironmentVariable($key, $values[$key], 'Process') }
    }
    if ($EnableNeo4j -or $CheckConfigurationOnly) { Test-Neo4jConfiguration }
    if ($CheckConfigurationOnly) {
        Write-Host 'Connection configuration is present and URI syntax is valid. No database connection or tests performed.'
        return
    }
    if ($EnableNeo4j) {
        if (-not $ConfirmDisposableDatabase) { throw 'ConfirmDisposableDatabase is required: E2E writes and retains graph data.' }
        if ([string]::IsNullOrWhiteSpace($Neo4jVersion)) { throw 'Supply Neo4jVersion from the test server; it is recorded as user-reported.' }
        Test-Neo4jEndpoint
    }
    New-Item -ItemType Directory -Path $runDir | Out-Null
    $before = Get-InputHashes
    $before | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $runDir 'inputs-before.json') -Encoding UTF8
    $revision = (& git rev-parse HEAD 2>$null)
    [ordered]@{runId=$runId; revision=$revision; sourceSnapshot='See input hashes; revision alone does not identify uncommitted changes';
        startedUtc=[DateTime]::UtcNow.ToString('o'); neo4jEnabled=[bool]$EnableNeo4j;
        neo4jVersionUserReported=$Neo4jVersion; expectedFrontendRejected=6; expectedNumericRejected=0;
        expectedCarRentalE2ECases=19; expectedMedicalE2ECases=36;
        expectedFeatureMatrixLeafCases=61; expectedLiveComparisons=85} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runDir 'manifest.json') -Encoding UTF8
    & mvn -version 2>&1 | Out-File -LiteralPath (Join-Path $runDir 'toolchain.txt') -Encoding UTF8
    $env:OCL2CYPHER_RUN_NEO4J_E2E = 'false'
    $env:OCL2CYPHER_RUN_CARRENTAL_E2E = 'false'
    $env:OCL2CYPHER_RUN_MEDICAL_E2E = 'false'
    Invoke-Gate 'regression' @('-pl','ocl2cypher','test') 'org.uet.dse.ocl2cypher.caseStudy.CarRentalInvariantReplayTest' 25
    if ($EnableNeo4j) {
        $env:OCL2CYPHER_RUN_CARRENTAL_E2E = 'true'
        Invoke-Gate 'neo4j' @('-pl','ocl2cypher','-Dtest=CarRentalNeo4jReplayTest','test') 'org.uet.dse.ocl2cypher.caseStudy.CarRentalNeo4jReplayTest' 19
        $env:OCL2CYPHER_RUN_MEDICAL_E2E = 'true'
        Invoke-Gate 'neo4j-medical' @('-pl','ocl2cypher','-Dtest=MedicalNeo4jReplayTest','test') 'org.uet.dse.ocl2cypher.caseStudy.MedicalNeo4jReplayTest' 36
        $env:OCL2CYPHER_RUN_NEO4J_E2E = 'true'
        Invoke-Gate 'neo4j-feature-matrix' @('-pl','ocl2cypher','-Dtest=Neo4jFeatureMatrixTest','test') 'org.uet.dse.ocl2cypher.Neo4jFeatureMatrixTest' 61
    }
    $after = Get-InputHashes
    $after | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $runDir 'inputs-after.json') -Encoding UTF8
    if (($before | ConvertTo-Json -Depth 5 -Compress) -ne ($after | ConvertTo-Json -Depth 5 -Compress)) {
        throw 'Inputs changed during run: results cannot identify one fixed source snapshot.'
    }
    [ordered]@{status= $(if ($EnableNeo4j) {'REGRESSION_AND_85_LIVE_COMPARISONS_PASS'} else {'REGRESSION_ONLY_PASS'});
        completedUtc=[DateTime]::UtcNow.ToString('o'); proofStatus='No universal theorem discharged'} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runDir 'outcome.json') -Encoding UTF8
    Write-Host "Evidence directory: $runDir"
} catch {
    if (Test-Path -LiteralPath $runDir) {
        '{"status":"FAILED_OR_INCOMPLETE"}' | Set-Content -LiteralPath (Join-Path $runDir 'outcome.json') -Encoding UTF8
    }
    throw
} finally {
    foreach ($key in $connectionKeys) { [Environment]::SetEnvironmentVariable($key, $savedConnection[$key], 'Process') }
    [Environment]::SetEnvironmentVariable('OCL2CYPHER_RUN_NEO4J_E2E', $savedGeneric, 'Process')
    [Environment]::SetEnvironmentVariable('OCL2CYPHER_RUN_CARRENTAL_E2E', $savedCar, 'Process')
    [Environment]::SetEnvironmentVariable('OCL2CYPHER_RUN_MEDICAL_E2E', $savedMedical, 'Process')
    Pop-Location
}
