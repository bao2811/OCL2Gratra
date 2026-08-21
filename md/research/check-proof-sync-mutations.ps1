param(
    [string]$CheckerPath = (Join-Path $PSScriptRoot 'check-proof-sync.ps1'),
    [string]$WorkspacePath = (Join-Path $PSScriptRoot '..\..')
)

$ErrorActionPreference = 'Stop'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$workspace = [System.IO.Path]::GetFullPath($WorkspacePath)
$checker = [System.IO.Path]::GetFullPath($CheckerPath)
$hostExecutable = (Get-Process -Id $PID).Path
$sourceRegistry = Join-Path $PSScriptRoot '..\..\verification\contract\proof-contract-registry.json'
$sourceMarkdown = Join-Path $PSScriptRoot 'formal-theorems-and-proofs.md'
$sourceLatex = Join-Path (Split-Path $PSScriptRoot -Parent) 'latex\theorem-lemma-proof.tex'
$mutationRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("proof-sync-mutations-" + [guid]::NewGuid().ToString('N'))
$passed = 0
$total = 7

function Read-Text([string]$Path) {
    return [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
}

function Write-Text([string]$Path, [string]$Value) {
    [System.IO.File]::WriteAllText($Path, $Value, $utf8NoBom)
}

function New-Case([string]$Name) {
    $casePath = Join-Path $mutationRoot $Name
    [void][System.IO.Directory]::CreateDirectory($casePath)
    $paths = [ordered]@{
        Registry = Join-Path $casePath 'registry.json'
        Markdown = Join-Path $casePath 'proof.md'
        Latex = Join-Path $casePath 'proof.tex'
    }
    Copy-Item -LiteralPath $sourceRegistry -Destination $paths.Registry
    Copy-Item -LiteralPath $sourceMarkdown -Destination $paths.Markdown
    Copy-Item -LiteralPath $sourceLatex -Destination $paths.Latex
    return $paths
}

function Sync-ProjectionHash([object]$Paths) {
    $hash = (Get-FileHash -LiteralPath $Paths.Registry -Algorithm SHA256).Hash.ToLowerInvariant()
    foreach ($path in @($Paths.Markdown, $Paths.Latex)) {
        $text = Read-Text $path
        $text = [regex]::Replace($text, 'PROOF-REGISTRY-SHA256: [0-9a-fA-F]{64}', "PROOF-REGISTRY-SHA256: $hash")
        Write-Text $path $text
    }
}

function Sync-CanonicalRegistryFromDerived([object]$Paths) {
    $begin = '<!-- BEGIN CANONICAL PROOF-CONTRACT REGISTRY JSON -->'
    $end = '<!-- END CANONICAL PROOF-CONTRACT REGISTRY JSON -->'
    $registry = (Read-Text $Paths.Registry).Replace("`r`n", "`n").Replace("`r", "`n").Trim()
    $markdown = Read-Text $Paths.Markdown
    $pattern = '(?s)' + [regex]::Escape($begin) + '\r?\n.*?\r?\n' + [regex]::Escape($end)
    if (-not [regex]::IsMatch($markdown, $pattern)) {
        throw 'Mutation fixture cannot find the canonical registry block'
    }
    $replacement = $begin + "`n" + $registry + "`n" + $end
    Write-Text $Paths.Markdown ([regex]::Replace($markdown, $pattern, $replacement, 1))
}

function Invoke-Checker([object]$Paths, [string[]]$ExtraArguments) {
    $arguments = @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $checker,
        '-RegistryPath', $Paths.Registry,
        '-MarkdownPath', $Paths.Markdown,
        '-LatexPath', $Paths.Latex,
        '-WorkspacePath', $workspace,
        '-SkipArtifactBuild'
    )
    $arguments += $ExtraArguments
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = @(& $hostExecutable @arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = ($output | Out-String)
    }
}

function Assert-MutationKilled([string]$Name, [object]$Paths, [string]$ExpectedText,
                               [string[]]$ExtraArguments = @()) {
    $result = Invoke-Checker $Paths $ExtraArguments
    if ($result.ExitCode -eq 0) {
        throw "Mutation '$Name' survived: checker unexpectedly returned success"
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
    $baselineResult = Invoke-Checker $baseline @()
    if ($baselineResult.ExitCode -ne 0) {
        throw "Proof-sync baseline failed before mutation testing: $($baselineResult.Output)"
    }

    # Mutation 0: editing only the derived registry must never redefine the
    # canonical contract owned by the formal Markdown source.
    $case = New-Case 'derived-registry-drift'
    $registry = (Read-Text $case.Registry) | ConvertFrom-Json
    ($registry.assumptions | Where-Object id -eq 'A1').key = 'MUTATED_DERIVED_ONLY'
    Write-Text $case.Registry ($registry | ConvertTo-Json -Depth 40)
    Assert-MutationKilled 'derived registry cannot override formal source' $case `
        'Derived proof registry is stale'

    # Mutation 1: missing theorem premise in the Markdown projection.
    $case = New-Case 'missing-premise'
    $text = Read-Text $case.Markdown
    $oldRow = '| PC-T0 | validation observations of `(MM,M)` and every adequate `G` agree | A4, A5 | R1, R2, R3, R4, R5, R6, R7 |'
    $newRow = '| PC-T0 | validation observations of `(MM,M)` and every adequate `G` agree | A4 | R1, R2, R3, R4, R5, R6, R7 |'
    if (-not $text.Contains($oldRow)) { throw 'Mutation fixture cannot find PC-T0 row' }
    Write-Text $case.Markdown ($text.Replace($oldRow, $newRow))
    Assert-MutationKilled 'missing theorem premise' $case 'Markdown: generated theorem/dependency/governance block'

    # Mutation 2: registry dependency changes while both projections retain the old semantic row.
    $case = New-Case 'dependency-drift'
    $registry = (Read-Text $case.Registry) | ConvertFrom-Json
    ($registry.theorems | Where-Object id -eq 'PC-T4').requires = @('T0','G1','G2','G3')
    Write-Text $case.Registry ($registry | ConvertTo-Json -Depth 30)
    Sync-CanonicalRegistryFromDerived $case
    Sync-ProjectionHash $case
    Assert-MutationKilled 'registry dependency drift' $case 'generated theorem/dependency/governance block'

    # Mutation 3: admission grows by one constructor without a registry/matrix proof row.
    $case = New-Case 'admitted-constructor'
    $mutatedSource = Join-Path (Split-Path $case.Registry -Parent) 'OclValFragmentCoverageTest.java'
    $admittedSource = Join-Path $workspace 'neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/OclValFragmentCoverageTest.java'
    Copy-Item -LiteralPath $admittedSource -Destination $mutatedSource
    $text = Read-Text $mutatedSource
    $oldCase = '                c("isUnique", "context Person inv C47: Set{self.age, 17}->isUnique(x | self.age >= 18)")'
    $newCase = $oldCase + ",`r`n" + '                c("new admitted constructor", "context Person inv C48: true")'
    if (-not $text.Contains($oldCase)) { throw 'Mutation fixture cannot find the final admitted constructor' }
    Write-Text $mutatedSource ($text.Replace($oldCase, $newCase))
    Assert-MutationKilled 'unregistered admitted constructor' $case 'Constructor registry/admission source' @('-AdmittedCaseSourcePath', $mutatedSource)

    # Mutation 4: implementation vocabulary changes without updating the adapter contract.
    $case = New-Case 'vocabulary-drift'
    $vocabularyRoot = Join-Path (Split-Path $case.Registry -Parent) 'vocabulary-workspace'
    $registry = (Read-Text $case.Registry) | ConvertFrom-Json
    foreach ($relativePath in @($registry.implementationVocabulary.path | Select-Object -Unique)) {
        $source = Join-Path $workspace $relativePath
        $destination = Join-Path $vocabularyRoot $relativePath
        [void][System.IO.Directory]::CreateDirectory((Split-Path $destination -Parent))
        Copy-Item -LiteralPath $source -Destination $destination
    }
    $canonicalPath = Join-Path $vocabularyRoot 'neo4j/src/main/java/org/uet/dse/neo4j/encoding/CanonicalGraphVocabulary.java'
    $text = Read-Text $canonicalPath
    Write-Text $canonicalPath ($text.Replace('"ObjectInstanceOf"', '"ObjectInstanceOf_MUTATED"'))
    Assert-MutationKilled 'implementation vocabulary drift' $case 'Vocabulary drift for ObjectInstanceOf' @('-VocabularyWorkspacePath', $vocabularyRoot)

    # Mutation 5: a discharged obligation points to evidence that does not exist.
    $case = New-Case 'missing-evidence'
    $registry = (Read-Text $case.Registry) | ConvertFrom-Json
    ($registry.evidence | Where-Object id -eq 'EV-PO17-CHECKER').path = 'md/research/evidence/does-not-exist.txt'
    Write-Text $case.Registry ($registry | ConvertTo-Json -Depth 30)
    Sync-CanonicalRegistryFromDerived $case
    Sync-ProjectionHash $case
    Assert-MutationKilled 'discharged obligation missing evidence' $case 'Evidence EV-PO17-CHECKER file is missing'

    # Mutation 6: an active prototype claim is forbidden while PO-14/PO-19 block it.
    $case = New-Case 'premature-active-claim'
    $registry = (Read-Text $case.Registry) | ConvertFrom-Json
    $registry.claimPolicy.prototypeCorrectness = 'active'
    Write-Text $case.Registry ($registry | ConvertTo-Json -Depth 30)
    Sync-CanonicalRegistryFromDerived $case
    foreach ($path in @($case.Markdown, $case.Latex)) {
        $text = Read-Text $path
        Write-Text $path ($text.Replace('PROTOTYPE-CORRECTNESS-CLAIM: CONDITIONAL', 'PROTOTYPE-CORRECTNESS-CLAIM: ACTIVE'))
    }
    Sync-ProjectionHash $case
    Assert-MutationKilled 'premature prototype correctness claim' $case 'Prototype correctness claim is active while required'

    Write-Host "Proof-sync mutation tests PASS: $passed/$total killed."
} finally {
    $resolvedRoot = [System.IO.Path]::GetFullPath($mutationRoot)
    $tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if ($resolvedRoot.StartsWith($tempRoot, [System.StringComparison]::OrdinalIgnoreCase) -and
        (Split-Path $resolvedRoot -Leaf).StartsWith('proof-sync-mutations-')) {
        Remove-Item -LiteralPath $resolvedRoot -Recurse -Force
    }
}
