param(
    [switch]$Offline,
    [switch]$RequireCleanRuntimeEvidence,
    [string]$MavenRepository = ''
)

$ErrorActionPreference = 'Stop'

$workspace = Resolve-Path (Join-Path $PSScriptRoot '..\..')
& (Join-Path $PSScriptRoot 'check-verification-contract.ps1')
if (-not $?) { exit 1 }
& (Join-Path $PSScriptRoot 'check-verification-contract-mutations.ps1')
if (-not $?) { exit 1 }
& (Join-Path $PSScriptRoot 'check-mechanized-proof.ps1') -AllowMissingToolchain
if (-not $?) { exit 1 }

$portableLean = Join-Path $workspace '_build_lib\lean-4.32.2\lean-4.32.2-windows\bin\lean.exe'
$pathLean = Get-Command 'lean' -ErrorAction SilentlyContinue
if ((Test-Path -LiteralPath $portableLean -PathType Leaf) -or $null -ne $pathLean) {
    & (Join-Path $PSScriptRoot 'check-mechanized-proof-mutations.ps1')
    if (-not $?) { exit 1 }
} else {
    Write-Warning 'Lean mutation gate skipped because Lean 4.32.2 is unavailable; CI runs it with the pinned toolchain.'
}

$tests = @(
    'OclConformanceMatrixTest',
    'OclValFragmentCoverageTest',
    'OclValNegativeAdmissionCoverageTest',
    'OclVal47NonVacuityContractTest',
    'CertifiedValidationCompilationTest',
    'FixturePremiseVerifierTest',
    'OclMetamodelSnapshotTest',
    'RepresentationAdequacyEvaluatorTest',
    'CanonicalEncodingIndependentContractTest',
    'OclGraphEncodingAdequacyTest',
    'CompilerMutationContractTest',
    'RendererAccessorAgreementTest',
    'BottomSafeReceiverAgreementTest',
    'OclBottomTokenContractTest',
    'OclScalarClosureCheckerTest',
    'RawCypherAstTotalityTest',
    'GeneratedCypherSyntaxTreeTest',
    'GeneratedCypherCanonicalTreeTest',
    'GeneratedCypherFormalTreeManifestTest',
    'OpenCypherFrontendReferenceContractTest',
    'Neo4jCypherAstBridgeTest',
    'OclCypherPlanFormalTreeAgreementTest',
    'OclCypherPlanConstructorEvidenceMatrixTest',
    'OclIrJavaRefinementCoverageTest',
    'OclIrPlanPayloadRefinementTest',
    'BoundVaProductionRefinementCoverageTest',
    'DynamicEmfModelValidatorTest',
    'MetamodelInstanceXmlContractTest',
    'SpecPlanSimContractTest',
    'MetamodelFeatureRefinementCoverageTest',
    'CanonicalPgmmEmfConformanceTest',
    'OclVoidContextMatrixTest',
    'AdapterAdequacyCertificateTest',
    'AdapterAdequacyEvidenceMatrixTest',
    'FamiliesToPersonsCaseStudyTest',
    'OclCypherRendererTest',
    'OclRewritePreservationTest',
    'OclDualCheckTest'
)

if ($RequireCleanRuntimeEvidence) {
    $tests += @(
        'OclVal47NonVacuityEvidenceTest',
        'CanonicalProfileRuntimeEvidenceManifestTest',
        'Cypher5ValRuntimeEvidenceManifestTest'
    )
}
$tests = $tests -join ','

Push-Location $workspace
try {
    $mavenArgs = @()
    if ($Offline) { $mavenArgs += '-o' }
    if (-not [string]::IsNullOrWhiteSpace($MavenRepository)) {
        $mavenArgs += "-Dmaven.repo.local=$MavenRepository"
    }
    if ($RequireCleanRuntimeEvidence) {
        $mavenArgs += '-Dverification.requireCleanEvidence=true'
    }
    $mavenArgs += @(
        '-pl', 'neo4j-tgg',
        "-Dtest=$tests",
        '-Dsurefire.failIfNoSpecifiedTests=false',
        'test'
    )
    & mvn @mavenArgs
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}

Write-Host 'Implementation conformance checks passed.'
