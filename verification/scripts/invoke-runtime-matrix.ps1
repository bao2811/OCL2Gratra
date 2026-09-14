param(
    [string]$ProfilesPath = (Join-Path $PSScriptRoot '..\runtime\neo4j-runtime-matrix.tsv'),
    [string]$OutputPath = (Join-Path $PSScriptRoot '..\report\neo4j-runtime-matrix.tsv'),
    [long]$Seed = 20260823,
    [int]$Cases = 64,
    [string]$MavenRepository = '',
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$workspace = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))

if ($Cases -lt 1) { throw '-Cases must be positive' }
if (-not (Test-Path -LiteralPath $ProfilesPath -PathType Leaf)) {
    throw "Neo4j runtime matrix does not exist: $ProfilesPath"
}

$lines = [System.IO.File]::ReadAllLines((Resolve-Path -LiteralPath $ProfilesPath),
        [System.Text.Encoding]::UTF8) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) -and -not $_.StartsWith('#') }
if ($lines.Count -lt 2) { throw 'Neo4j runtime matrix has no profile rows' }
$profiles = @($lines | ConvertFrom-Csv -Delimiter "`t")
$required = @('profileId', 'uri', 'database', 'user', 'passwordEnvironment', 'expectedKernel', 'expectedEdition')
foreach ($field in $required) {
    if (-not ($profiles[0].PSObject.Properties.Name -contains $field)) {
        throw "Neo4j runtime matrix is missing column: $field"
    }
}

$saved = @{}
foreach ($name in @('NEO4J_URI', 'NEO4J_DB', 'NEO4J_USER', 'NEO4J_PASSWORD')) {
    $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
$rows = [System.Collections.Generic.List[string]]::new()
$rows.Add("# schema`tneo4j-property-runtime-matrix-v1")
$rows.Add("# executedAt`t$([DateTimeOffset]::Now.ToString('o'))")
$rows.Add("# seed`t$Seed")
$rows.Add("# generatedCasesPerProfile`t$Cases")
$rows.Add("profileId`tstatus`ttests`tfailures`terrors`tskipped`tseconds`texpectedKernel`texpectedEdition`tdatabase")

try {
    foreach ($profile in $profiles) {
        foreach ($field in @('profileId', 'uri', 'database', 'user')) {
            if ([string]::IsNullOrWhiteSpace([string]$profile.$field)) {
                throw "Runtime matrix profile has blank $field"
            }
        }
        $env:NEO4J_URI = [string]$profile.uri
        $env:NEO4J_DB = [string]$profile.database
        $env:NEO4J_USER = [string]$profile.user
        if (-not [string]::IsNullOrWhiteSpace([string]$profile.passwordEnvironment)) {
            $password = [Environment]::GetEnvironmentVariable([string]$profile.passwordEnvironment)
            if ([string]::IsNullOrWhiteSpace($password)) {
                throw "Profile $($profile.profileId) requires environment variable $($profile.passwordEnvironment)"
            }
            $env:NEO4J_PASSWORD = $password
        } else {
            [Environment]::SetEnvironmentVariable('NEO4J_PASSWORD', $null, 'Process')
        }

        $mavenArgs = @()
        if ($Offline) { $mavenArgs += '-o' }
        if (-not [string]::IsNullOrWhiteSpace($MavenRepository)) {
            $mavenArgs += "-Dmaven.repo.local=$([System.IO.Path]::GetFullPath($MavenRepository))"
        }
        $mavenArgs += @('-pl', 'neo4j-tgg',
            '-Dtest=OclPropertyBasedRealNeo4jTest',
            '-Dneo4j.property.it=true',
            "-Docl.property.seed=$Seed", "-Docl.property.runtime.cases=$Cases")
        if (-not [string]::IsNullOrWhiteSpace([string]$profile.expectedKernel)) {
            $mavenArgs += "-Dneo4j.matrix.expectedKernel=$($profile.expectedKernel)"
        }
        if (-not [string]::IsNullOrWhiteSpace([string]$profile.expectedEdition)) {
            $mavenArgs += "-Dneo4j.matrix.expectedEdition=$($profile.expectedEdition)"
        }
        $mavenArgs += 'test'

        Write-Host "[runtime-matrix:$($profile.profileId)] seed=$Seed cases=$Cases uri=$($profile.uri) database=$($profile.database)"
        Push-Location $workspace
        try {
            & mvn @mavenArgs
            if ($LASTEXITCODE -ne 0) { throw "Maven failed for profile $($profile.profileId)" }
        } finally {
            Pop-Location
        }

        $reportPath = Join-Path $workspace 'neo4j-tgg\target\surefire-reports\TEST-org.uet.dse.neo4jtgg.experiment.OclPropertyBasedRealNeo4jTest.xml'
        if (-not (Test-Path -LiteralPath $reportPath -PathType Leaf)) {
            throw "Missing Surefire property report for profile $($profile.profileId)"
        }
        [xml]$report = [System.IO.File]::ReadAllText($reportPath, [System.Text.Encoding]::UTF8)
        $suite = $report.testsuite
        $tests = [int]$suite.tests
        $failures = [int]$suite.failures
        $errors = [int]$suite.errors
        $skipped = [int]$suite.skipped
        if ($tests -lt 1 -or $failures -ne 0 -or $errors -ne 0 -or $skipped -ne 0) {
            throw "Non-publishable property result for profile $($profile.profileId): tests=$tests failures=$failures errors=$errors skipped=$skipped"
        }
        $rows.Add("$($profile.profileId)`tPASS`t$tests`t$failures`t$errors`t$skipped`t$($suite.time)`t$($profile.expectedKernel)`t$($profile.expectedEdition)`t$($profile.database)")
    }
} finally {
    foreach ($name in $saved.Keys) {
        [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process')
    }
}

$fullOutput = [System.IO.Path]::GetFullPath($OutputPath)
$parent = Split-Path $fullOutput -Parent
if (-not (Test-Path -LiteralPath $parent -PathType Container)) {
    [System.IO.Directory]::CreateDirectory($parent) | Out-Null
}
[System.IO.File]::WriteAllText($fullOutput, ($rows -join "`n") + "`n", $utf8NoBom)
Write-Host "Neo4j property runtime matrix PASS: profiles=$($profiles.Count), output=$fullOutput"
