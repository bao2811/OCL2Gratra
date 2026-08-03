param(
    [string]$CheckerPath = (Join-Path $PSScriptRoot 'check-verification-contract.ps1'),
    [string]$RegistryPath = (Join-Path $PSScriptRoot '..\contract\proof-contract-registry.json'),
    [string]$WorkspacePath = (Join-Path $PSScriptRoot '..\..')
)

$ErrorActionPreference = 'Stop'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$workspace = [System.IO.Path]::GetFullPath($WorkspacePath)
$checker = [System.IO.Path]::GetFullPath($CheckerPath)
$hostExecutable = (Get-Process -Id $PID).Path
$mutationRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("verification-contract-mutations-" + [guid]::NewGuid().ToString('N'))
$passed = 0
$total = 6

function Read-Text([string]$Path) {
    return [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
}

function Write-Text([string]$Path, [string]$Value) {
    [System.IO.File]::WriteAllText($Path, $Value, $utf8NoBom)
}

function New-Case([string]$Name) {
    $casePath = Join-Path $mutationRoot $Name
    [void][System.IO.Directory]::CreateDirectory($casePath)
    $registry = Join-Path $casePath 'registry.json'
    Copy-Item -LiteralPath $RegistryPath -Destination $registry
    return [pscustomobject]@{ Root = $casePath; Registry = $registry }
}

function Invoke-Checker([object]$Case, [string[]]$ExtraArguments = @()) {
    $arguments = @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $checker,
        '-RegistryPath', $Case.Registry,
        '-WorkspacePath', $workspace
    ) + $ExtraArguments
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = @(& $hostExecutable @arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    return [pscustomobject]@{ ExitCode = $exitCode; Output = ($output | Out-String) }
}

function Assert-Killed(
    [string]$Name,
    [object]$Case,
    [string]$ExpectedText,
    [string[]]$ExtraArguments = @()
) {
    $result = Invoke-Checker $Case $ExtraArguments
    if ($result.ExitCode -eq 0) {
        throw "Mutation '$Name' survived"
    }
    $normalizedOutput = [regex]::Replace($result.Output, '\s+', ' ')
    $normalizedExpected = [regex]::Replace($ExpectedText, '\s+', ' ')
    if (-not $normalizedOutput.Contains($normalizedExpected)) {
        throw "Mutation '$Name' failed for the wrong reason; expected '$ExpectedText'. Output: $($result.Output)"
    }
    $script:passed++
    Write-Host "KILLED $Name"
}

[void][System.IO.Directory]::CreateDirectory($mutationRoot)
try {
    $baseline = New-Case 'baseline'
    $baselineResult = Invoke-Checker $baseline
    if ($baselineResult.ExitCode -ne 0) {
        throw "Machine-verification baseline failed: $($baselineResult.Output)"
    }

    $case = New-Case 'missing-premise'
    $registry = (Read-Text $case.Registry) | ConvertFrom-Json
    ($registry.theorems | Where-Object id -eq 'PC-T0').assumptions = @()
    Write-Text $case.Registry ($registry | ConvertTo-Json -Depth 40)
    Assert-Killed 'missing theorem premise' $case 'premise/dependency list'

    $case = New-Case 'dependency-drift'
    $registry = (Read-Text $case.Registry) | ConvertFrom-Json
    ($registry.theorems | Where-Object id -eq 'PC-T4').assumptions = @('A2','A3','A4','A5','AX')
    Write-Text $case.Registry ($registry | ConvertTo-Json -Depth 40)
    Assert-Killed 'registry dependency drift' $case 'unknown assumption AX'

    $case = New-Case 'admitted-constructor'
    $mutatedSource = Join-Path $case.Root 'OclValFragmentCoverageTest.java'
    $source = Join-Path $workspace 'neo4j-tgg\src\test\java\org\uet\dse\neo4jtgg\experiment\OclValFragmentCoverageTest.java'
    Copy-Item -LiteralPath $source -Destination $mutatedSource
    $text = Read-Text $mutatedSource
    $needle = '                c("isUnique", "context Person inv C47: Set{self.age, 17}->isUnique(x | self.age >= 18)")'
    if (-not $text.Contains($needle)) { throw 'Cannot locate final admitted constructor' }
    Write-Text $mutatedSource ($text.Replace($needle, $needle + ",`r`n" + '                c("unregistered", "context Person inv C48: true")'))
    Assert-Killed 'unregistered admitted constructor' $case 'source count drift' @(
        '-AdmittedCaseSourcePath', $mutatedSource
    )

    $case = New-Case 'vocabulary-drift'
    $vocabularyRoot = Join-Path $case.Root 'workspace'
    $registry = (Read-Text $case.Registry) | ConvertFrom-Json
    foreach ($relativePath in @($registry.implementationVocabulary.path | Select-Object -Unique)) {
        $source = Join-Path $workspace $relativePath
        $destination = Join-Path $vocabularyRoot $relativePath
        [void][System.IO.Directory]::CreateDirectory((Split-Path $destination -Parent))
        Copy-Item -LiteralPath $source -Destination $destination
    }
    $canonical = Join-Path $vocabularyRoot 'neo4j\src\main\java\org\uet\dse\neo4j\encoding\CanonicalGraphVocabulary.java'
    Write-Text $canonical ((Read-Text $canonical).Replace('"ObjectInstanceOf"', '"ObjectInstanceOf_MUTATED"'))
    Assert-Killed 'implementation vocabulary drift' $case 'ObjectInstanceOf; expected implementation literal' @(
        '-VocabularyWorkspacePath', $vocabularyRoot
    )

    $case = New-Case 'missing-evidence'
    $registry = (Read-Text $case.Registry) | ConvertFrom-Json
    ($registry.evidence | Where-Object id -eq 'EV-PO17-CHECKER').path = 'verification/evidence/does-not-exist.txt'
    Write-Text $case.Registry ($registry | ConvertTo-Json -Depth 40)
    Assert-Killed 'discharged obligation missing evidence' $case 'verification/evidence/does-not-exist.txt'

    $case = New-Case 'premature-active-claim'
    $registry = (Read-Text $case.Registry) | ConvertFrom-Json
    $registry.claimPolicy.prototypeCorrectness = 'active'
    Write-Text $case.Registry ($registry | ConvertTo-Json -Depth 40)
    Assert-Killed 'premature prototype correctness claim' $case 'Prototype correctness claim is active while required'

    Write-Host "Machine verification mutation tests PASS: $passed/$total killed."
} finally {
    $resolved = [System.IO.Path]::GetFullPath($mutationRoot)
    $temp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if ($resolved.StartsWith($temp, [System.StringComparison]::OrdinalIgnoreCase) -and
        (Split-Path $resolved -Leaf).StartsWith('verification-contract-mutations-')) {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}

# The last deliberately rejected child checker exits non-zero. Do not leak that
# expected mutation exit code to the calling PowerShell/GitHub Actions process.
$global:LASTEXITCODE = 0
