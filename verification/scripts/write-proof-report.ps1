param(
    [string]$RegistryPath = '',
    [string]$EvidencePath = '',
    [ValidateSet('NOT_RUN', 'PASS')]
    [string]$GateResult = 'NOT_RUN',
    [Parameter(Mandatory = $true)]
    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$workspace = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
if ([string]::IsNullOrWhiteSpace($RegistryPath)) {
    $RegistryPath = Join-Path $PSScriptRoot '..\contract\proof-contract-registry.json'
}

function Read-Json([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Missing report input: $Path"
    }
    return [System.IO.File]::ReadAllText(
        (Resolve-Path -LiteralPath $Path),
        [System.Text.Encoding]::UTF8
    ) | ConvertFrom-Json
}

$registry = Read-Json $RegistryPath
if ([string]::IsNullOrWhiteSpace($EvidencePath)) {
    $EvidencePath = Join-Path $workspace ([string]$registry.artifactPolicy.baselineEvidencePath)
}
$evidence = Read-Json $EvidencePath
$registryHash = (Get-FileHash -LiteralPath $RegistryPath -Algorithm SHA256).Hash.ToLowerInvariant()
$blockingClassifications = @($registry.claimPolicy.blockingClassifications | ForEach-Object { [string]$_ })
$blockingStatuses = @($registry.claimPolicy.blockingStatuses | ForEach-Object { [string]$_ })
$blocking = @($registry.proofObligations | Where-Object {
    $blockingClassifications -ccontains [string]$_.classification -and
    $blockingStatuses -ccontains [string]$_.status
})

$sourceRevision = 'UNKNOWN'
Push-Location $workspace
try {
    $revision = @(& git rev-parse HEAD 2>$null)
    if ($LASTEXITCODE -eq 0 -and $revision.Count -gt 0) {
        $sourceRevision = ([string]$revision[0]).Trim()
    }
} finally {
    Pop-Location
}

$leanEvidence = $evidence.lean
$implementationEvidence = $evidence.implementation
$runtimeEvidence = $evidence.runtime
$runtimeProfile = @($registry.runtimeProfiles)[0]
$report = [ordered]@{
    schema = 'proof-report.schema.v1'
    proofContract = [string]$registry.version
    registrySchemaVersion = [int]$registry.registrySchemaVersion
    registrySha256 = $registryHash
    sourceRevision = $sourceRevision
    verificationGate = [ordered]@{
        result = $GateResult
        meaning = if ($GateResult -eq 'PASS') {
            'generated after contract, mutation, Lean, and implementation-conformance gates passed'
        } else {
            'report generated without asserting execution of the complete gate'
        }
    }
    claim = [ordered]@{
        prototypeCorrectness = [string]$registry.claimPolicy.prototypeCorrectness
        blockingObligations = @($blocking.id)
    }
    obligations = [ordered]@{
        total = @($registry.proofObligations).Count
        discharged = @($registry.proofObligations | Where-Object status -eq 'discharged').Count
        partial = @($registry.proofObligations | Where-Object status -eq 'partial').Count
        open = @($registry.proofObligations | Where-Object status -eq 'open').Count
        outOfScope = @($registry.proofObligations | Where-Object classification -eq 'out_of_scope').Count
    }
    mechanization = [ordered]@{
        framework = [string]$registry.mechanization.framework
        version = [string]$registry.mechanization.version
        requiredTheorems = @($registry.mechanization.requiredTheorems).Count
        recordedEvidenceAt = [string]$evidence.capturedAt
        recordedKernel = [string]$leanEvidence.kernel
        recordedMutationsKilled = [string]$leanEvidence.mutationsKilled
        allowedCoreAxioms = @($registry.mechanization.axiomAudit.allowedCoreAxioms)
        coverage = @($registry.mechanization.coverage)
        openScope = @($registry.mechanization.openScope)
    }
    implementation = [ordered]@{
        recordedEvidenceAt = [string]$evidence.capturedAt
        admittedConstructors = @($registry.constructorCoverage.features).Count
        recordedConformanceTests = [int]$implementationEvidence.conformanceTests
        recordedConformanceFailures = [int]$implementationEvidence.failures
        recordedCompilerMutationScore = [string]$implementationEvidence.compilerMutationScore
        recordedNeo4jParserTrees = [string]$implementationEvidence.neo4jParserTrees
    }
    runtime = [ordered]@{
        profile = [string]$runtimeProfile.id
        serverVersion = [string]$runtimeProfile.serverVersion
        cypherVersion = [string]$runtimeProfile.cypherVersion
        recordedEvidenceAt = [string]$runtimeEvidence.recordedAt
        cypherAssumptionRowsPassed = [string]$runtimeEvidence.cypherAssumptionRowsPassed
        differentialEquivalent = [string]$runtimeEvidence.differentialEquivalent
        scope = [string]$runtimeEvidence.scope
    }
    publication = [ordered]@{
        paperPublication = [string]$registry.artifactPolicy.paperPublication
        includedInMachineGate = $false
    }
}

$fullOutput = [System.IO.Path]::GetFullPath($OutputPath)
$parent = Split-Path $fullOutput -Parent
if (-not [string]::IsNullOrWhiteSpace($parent)) {
    [void][System.IO.Directory]::CreateDirectory($parent)
}
[System.IO.File]::WriteAllText(
    $fullOutput,
    (($report | ConvertTo-Json -Depth 12) + "`n"),
    $utf8NoBom
)
if ((Get-Item -LiteralPath $fullOutput).Length -le 0) {
    throw "Generated report is empty: $fullOutput"
}
Write-Host "Proof report PASS: schema=proof-report.schema.v1, gate=$GateResult, path=$fullOutput"
