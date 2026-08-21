param(
    [string]$RegistryPath = (Join-Path $PSScriptRoot '..\contract\proof-contract-registry.json'),
    [string]$WorkspacePath = (Join-Path $PSScriptRoot '..\..'),
    [string]$CoverageMatrixPath = '',
    [string]$AdmittedCaseSourcePath = '',
    [string]$VocabularyWorkspacePath = '',
    [string]$ReportPath = '',
    [switch]$RequireGitTracked
)

$ErrorActionPreference = 'Stop'
$errors = [System.Collections.Generic.List[string]]::new()

function Add-CheckError([string]$Message) {
    $script:errors.Add($Message)
}

function Read-Utf8([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Missing file: $Path"
    }
    return [System.IO.File]::ReadAllText(
        (Resolve-Path -LiteralPath $Path),
        [System.Text.Encoding]::UTF8
    )
}

function Get-Utf8LfSha256([string]$Path) {
    $canonical = (Read-Utf8 $Path).Replace("`r`n", "`n").Replace("`r", "`n")
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $digest = $sha256.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($canonical))
        return ([System.BitConverter]::ToString($digest)).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha256.Dispose()
    }
}

function Test-ExactSequence([object[]]$Actual, [object[]]$Expected, [string]$Label) {
    $actualValues = @($Actual | ForEach-Object { [string]$_ })
    $expectedValues = @($Expected | ForEach-Object { [string]$_ })
    if ($actualValues.Count -ne $expectedValues.Count) {
        Add-CheckError "$Label count drift: expected $($expectedValues.Count), actual $($actualValues.Count)"
        return
    }
    for ($i = 0; $i -lt $expectedValues.Count; $i++) {
        if ($actualValues[$i] -cne $expectedValues[$i]) {
            Add-CheckError "$Label order/content drift at index $i`: expected '$($expectedValues[$i])', actual '$($actualValues[$i])'"
        }
    }
}

function Test-UniqueIds([object[]]$Items, [string]$Label) {
    $duplicates = @(
        $Items | ForEach-Object { [string]$_.id } |
            Group-Object | Where-Object Count -gt 1 | ForEach-Object Name
    )
    if ($duplicates.Count -gt 0) {
        Add-CheckError "$Label contains duplicate IDs: $($duplicates -join ', ')"
    }
}

function Resolve-WorkspacePath([string]$RelativePath, [string]$Label, [string]$BasePath = $workspace) {
    if ([string]::IsNullOrWhiteSpace($RelativePath)) {
        Add-CheckError "$Label has an empty path"
        return $null
    }
    $base = [System.IO.Path]::GetFullPath($BasePath)
    $full = [System.IO.Path]::GetFullPath((Join-Path $base $RelativePath))
    $prefix = $base.TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar
    ) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $full.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        Add-CheckError "$Label escapes its configured workspace: $RelativePath"
        return $null
    }
    return $full
}

function Test-EvidenceSymbol([string]$Path, [string]$Symbol, [string]$EvidenceId) {
    if ([string]::IsNullOrWhiteSpace($Symbol)) { return }
    $text = Read-Utf8 $Path
    if ($Path.EndsWith('.java', [System.StringComparison]::OrdinalIgnoreCase)) {
        if ($Symbol -match '^class\s+([A-Za-z_][A-Za-z0-9_]*)$') {
            $pattern = '(?m)^\s*(?:public\s+)?(?:final\s+)?class\s+' + [regex]::Escape($Matches[1]) + '\b'
        } elseif ($Symbol -match '^[A-Za-z_][A-Za-z0-9_]*$') {
            $pattern = '(?m)^\s*(?:@[A-Za-z0-9_.()"\s,=]+\s*)*(?:(?:public|protected|private|static|final|synchronized)\s+)*[A-Za-z0-9_<>,.?\[\]\s]+\s+' + [regex]::Escape($Symbol) + '\s*\('
        } else {
            $pattern = [regex]::Escape($Symbol)
        }
        if ($text -notmatch $pattern) {
            Add-CheckError "Evidence $EvidenceId declaration is missing: $Symbol"
        }
    } elseif (-not $text.Contains($Symbol)) {
        Add-CheckError "Evidence $EvidenceId marker is missing: $Symbol"
    }
}

function Get-BlockingObligations([object]$Registry) {
    $classifications = @($Registry.claimPolicy.blockingClassifications | ForEach-Object { [string]$_ })
    $statuses = @($Registry.claimPolicy.blockingStatuses | ForEach-Object { [string]$_ })
    return @($Registry.proofObligations | Where-Object {
        $classifications -ccontains [string]$_.classification -and
        $statuses -ccontains [string]$_.status
    })
}

$workspace = [System.IO.Path]::GetFullPath($WorkspacePath)
$vocabularyWorkspace = if ([string]::IsNullOrWhiteSpace($VocabularyWorkspacePath)) {
    $workspace
} else {
    [System.IO.Path]::GetFullPath($VocabularyWorkspacePath)
}

try {
    $registryText = Read-Utf8 $RegistryPath
    $registry = $registryText | ConvertFrom-Json
} catch {
    Write-Error $_
    exit 1
}
$registryHash = Get-Utf8LfSha256 $RegistryPath

if ([int]$registry.registrySchemaVersion -ne 4) {
    Add-CheckError "Registry: expected schema version 4, actual '$($registry.registrySchemaVersion)'"
}
Test-ExactSequence @($registry.assumptions.id) @('A1','A2','A3','A4','A5','A6','A7','A8','A9') 'Registry assumptions'
Test-ExactSequence @($registry.scopeLemmas.id) @('M1','M2','M3','M3a','M4','M5') 'Registry scope lemmas'
Test-ExactSequence @($registry.theorems.id) @('PC-T0','PC-T1','PC-T2','PC-T3','PC-T4','PC-T5','PC-T6') 'Registry theorems'
Test-ExactSequence @($registry.semanticFunctions.id) @(1..29 | ForEach-Object { 'SF-{0:d2}' -f $_ }) 'Registry semantic functions'
Test-ExactSequence @($registry.proofObligations.id) @(1..24 | ForEach-Object { 'PO-{0:d2}' -f $_ }) 'Registry proof obligations'
Test-ExactSequence @($registry.implementationVocabulary.term) @(
    'ObjectInstanceOf','InstanceOf','modelKey','classKey','attributeKey','associationKey',
    'sourceQualifiers','targetQualifiers','scalarCodecV1','__oclBottom'
) 'Registry implementation vocabulary'
Test-UniqueIds @($registry.assumptions) 'Registry assumptions'
Test-UniqueIds @($registry.scopeLemmas) 'Registry scope lemmas'
Test-UniqueIds @($registry.theorems) 'Registry theorems'
Test-UniqueIds @($registry.semanticFunctions) 'Registry semantic functions'
Test-UniqueIds @($registry.evidence) 'Registry evidence'
Test-UniqueIds @($registry.proofObligations) 'Registry proof obligations'

$assumptionIds = @($registry.assumptions.id | ForEach-Object { [string]$_ })
foreach ($theorem in @($registry.theorems)) {
    if ([string]::IsNullOrWhiteSpace([string]$theorem.statement) -or
        [string]::IsNullOrWhiteSpace([string]$theorem.statementVi)) {
        Add-CheckError "Registry theorem $($theorem.id) lacks a bilingual statement kernel"
    }
    if (@($theorem.assumptions).Count -eq 0 -or @($theorem.requires).Count -eq 0) {
        Add-CheckError "Registry theorem $($theorem.id) has an empty premise/dependency list"
    }
    foreach ($assumption in @($theorem.assumptions)) {
        if ($assumptionIds -cnotcontains [string]$assumption) {
            Add-CheckError "Registry theorem $($theorem.id) references unknown assumption $assumption"
        }
    }
    if (@($theorem.assumptions | Group-Object | Where-Object Count -gt 1).Count -gt 0) {
        Add-CheckError "Registry theorem $($theorem.id) contains duplicate assumptions"
    }
    if (@($theorem.requires | Group-Object | Where-Object Count -gt 1).Count -gt 0) {
        Add-CheckError "Registry theorem $($theorem.id) contains duplicate required results"
    }
}

$trackedPaths = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
[void]$trackedPaths.Add('verification/contract/proof-contract-registry.json')
$evidenceById = @{}
foreach ($evidence in @($registry.evidence)) {
    $evidenceId = [string]$evidence.id
    $evidenceById[$evidenceId] = $evidence
    if (@('file','source','test') -cnotcontains [string]$evidence.kind) {
        Add-CheckError "Evidence $evidenceId has invalid kind '$($evidence.kind)'"
    }
    $relativePath = ([string]$evidence.path).Replace('\','/')
    [void]$trackedPaths.Add($relativePath)
    $path = Resolve-WorkspacePath $relativePath "Evidence $evidenceId"
    if ($null -eq $path -or -not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Add-CheckError "Evidence $evidenceId file is missing: $relativePath"
    } else {
        Test-EvidenceSymbol $path ([string]$evidence.symbol) $evidenceId
        if ([string]$evidence.kind -eq 'test' -and [string]::IsNullOrWhiteSpace([string]$evidence.symbol)) {
            Add-CheckError "Test evidence $evidenceId must name a source declaration"
        }
    }
}

$validClassifications = @('required','recommended','out_of_scope')
$validStatuses = @('open','partial','discharged')
foreach ($obligation in @($registry.proofObligations)) {
    if ($validClassifications -cnotcontains [string]$obligation.classification) {
        Add-CheckError "Obligation $($obligation.id) has invalid classification '$($obligation.classification)'"
    }
    if ($validStatuses -cnotcontains [string]$obligation.status) {
        Add-CheckError "Obligation $($obligation.id) has invalid status '$($obligation.status)'"
    }
    $ids = @($obligation.evidenceIds | ForEach-Object { [string]$_ })
    if ([string]$obligation.status -eq 'discharged' -and $ids.Count -eq 0) {
        Add-CheckError "Discharged obligation $($obligation.id) has no evidence"
    }
    foreach ($evidenceId in $ids) {
        if (-not $evidenceById.ContainsKey($evidenceId)) {
            Add-CheckError "Obligation $($obligation.id) references unknown evidence $evidenceId"
        }
    }
}

foreach ($profile in @($registry.runtimeProfiles)) {
    foreach ($field in @('id','serverVersion','edition','cypherVersion','database','encodingProfile','parserArtifact')) {
        if (-not ($profile.PSObject.Properties.Name -contains $field) -or
            [string]::IsNullOrWhiteSpace([string]$profile.$field)) {
            Add-CheckError "Runtime profile $($profile.id) is missing $field"
        }
    }
    foreach ($evidenceId in @($profile.evidenceIds)) {
        if (-not $evidenceById.ContainsKey([string]$evidenceId)) {
            Add-CheckError "Runtime profile $($profile.id) references unknown evidence $evidenceId"
        }
    }
}

if ([string]::IsNullOrWhiteSpace($CoverageMatrixPath)) {
    $CoverageMatrixPath = Resolve-WorkspacePath ([string]$registry.constructorCoverage.matrixPath) 'Coverage matrix'
}
[void]$trackedPaths.Add(([string]$registry.constructorCoverage.matrixPath).Replace('\','/'))
if ([string]::IsNullOrWhiteSpace($AdmittedCaseSourcePath)) {
    $AdmittedCaseSourcePath = Resolve-WorkspacePath ([string]$registry.constructorCoverage.admittedCaseSourcePath) 'Admitted-case source'
}
[void]$trackedPaths.Add(([string]$registry.constructorCoverage.admittedCaseSourcePath).Replace('\','/'))

if ($null -eq $CoverageMatrixPath -or -not (Test-Path -LiteralPath $CoverageMatrixPath -PathType Leaf)) {
    Add-CheckError "Constructor coverage matrix is missing: $CoverageMatrixPath"
} else {
    $coverageRows = @((Read-Utf8 $CoverageMatrixPath) | ConvertFrom-Csv)
    Test-ExactSequence @($coverageRows.constructor) @($registry.constructorCoverage.features) 'Constructor registry/matrix'
    foreach ($row in $coverageRows) {
        foreach ($column in @($registry.constructorCoverage.requiredColumns.PSObject.Properties)) {
            if ([string]$row.($column.Name) -cne [string]$column.Value) {
                Add-CheckError "Coverage row '$($row.constructor)' column '$($column.Name)' drift: expected '$($column.Value)', actual '$($row.($column.Name))'"
            }
        }
    }
}
if ($null -eq $AdmittedCaseSourcePath -or -not (Test-Path -LiteralPath $AdmittedCaseSourcePath -PathType Leaf)) {
    Add-CheckError "Admitted-case source is missing: $AdmittedCaseSourcePath"
} else {
    $admittedFeatures = @(
        [regex]::Matches((Read-Utf8 $AdmittedCaseSourcePath), '\bc\("([^"]+)"') |
            ForEach-Object { $_.Groups[1].Value }
    )
    Test-ExactSequence $admittedFeatures @($registry.constructorCoverage.features) 'Constructor registry/admission source'
}
foreach ($artifactPath in @($registry.constructorCoverage.artifactPaths)) {
    $relativePath = ([string]$artifactPath).Replace('\','/')
    [void]$trackedPaths.Add($relativePath)
    $resolved = Resolve-WorkspacePath $relativePath 'Constructor artifact'
    if ($null -eq $resolved -or -not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
        Add-CheckError "Constructor coverage artifact is missing: $relativePath"
    }
}

$planMatrixRelative = ([string]$registry.planConstructorCoverage.matrixPath).Replace('\','/')
[void]$trackedPaths.Add($planMatrixRelative)
$planMatrixPath = Resolve-WorkspacePath $planMatrixRelative 'Plan-constructor matrix'
if ($null -eq $planMatrixPath -or -not (Test-Path -LiteralPath $planMatrixPath -PathType Leaf)) {
    Add-CheckError "Plan-constructor matrix is missing: $planMatrixRelative"
} else {
    $planRows = @((Read-Utf8 $planMatrixPath) | ConvertFrom-Csv)
    Test-ExactSequence @($planRows.constructor) @($registry.planConstructorCoverage.constructors) `
        'Plan-constructor registry/matrix'
    $actualColumns = if ($planRows.Count -gt 0) {
        @($planRows[0].PSObject.Properties.Name)
    } else { @() }
    Test-ExactSequence $actualColumns @($registry.planConstructorCoverage.requiredColumns) `
        'Plan-constructor matrix columns'
    foreach ($row in $planRows) {
        foreach ($column in @($registry.planConstructorCoverage.requiredColumns)) {
            if ([string]::IsNullOrWhiteSpace([string]$row.$column)) {
                Add-CheckError "Plan-constructor row '$($row.constructor)' has empty column '$column'"
            }
        }
    }
}

$javaIrMatrixRelative = ([string]$registry.javaIrRefinementCoverage.matrixPath).Replace('\','/')
[void]$trackedPaths.Add($javaIrMatrixRelative)
$javaIrMatrixPath = Resolve-WorkspacePath $javaIrMatrixRelative 'Java-IR refinement matrix'
if ($null -eq $javaIrMatrixPath -or -not (Test-Path -LiteralPath $javaIrMatrixPath -PathType Leaf)) {
    Add-CheckError "Java-IR refinement matrix is missing: $javaIrMatrixRelative"
} else {
    $javaIrRows = @((Read-Utf8 $javaIrMatrixPath) | ConvertFrom-Csv)
    Test-ExactSequence @($javaIrRows.java_constructor) @($registry.javaIrRefinementCoverage.constructors) `
        'Java-IR refinement registry/matrix'
    $actualColumns = if ($javaIrRows.Count -gt 0) {
        @($javaIrRows[0].PSObject.Properties.Name)
    } else { @() }
    Test-ExactSequence $actualColumns @($registry.javaIrRefinementCoverage.requiredColumns) `
        'Java-IR refinement matrix columns'
    foreach ($row in $javaIrRows) {
        foreach ($column in @($registry.javaIrRefinementCoverage.requiredColumns)) {
            if ([string]::IsNullOrWhiteSpace([string]$row.$column)) {
                Add-CheckError "Java-IR refinement row '$($row.java_constructor)' has empty column '$column'"
            }
        }
    }
}

$boundVaMatrixRelative = ([string]$registry.boundVaRefinementCoverage.matrixPath).Replace('\','/')
[void]$trackedPaths.Add($boundVaMatrixRelative)
$boundVaMatrixPath = Resolve-WorkspacePath $boundVaMatrixRelative 'Bound-VA refinement matrix'
if ($null -eq $boundVaMatrixPath -or -not (Test-Path -LiteralPath $boundVaMatrixPath -PathType Leaf)) {
    Add-CheckError "Bound-VA refinement matrix is missing: $boundVaMatrixRelative"
} else {
    $boundVaRows = @((Read-Utf8 $boundVaMatrixPath) | ConvertFrom-Csv)
    Test-ExactSequence @($boundVaRows.bound_constructor) @($registry.boundVaRefinementCoverage.boundConstructors) `
        'Bound-VA refinement bound-constructor registry/matrix'
    $matrixVaConstructors = @($boundVaRows | ForEach-Object {
        @(([string]$_.va_constructors).Split('|'))
    })
    Test-ExactSequence $matrixVaConstructors @($registry.boundVaRefinementCoverage.vaConstructors) `
        'Bound-VA refinement VA-constructor registry/matrix'
    $actualColumns = if ($boundVaRows.Count -gt 0) {
        @($boundVaRows[0].PSObject.Properties.Name)
    } else { @() }
    Test-ExactSequence $actualColumns @($registry.boundVaRefinementCoverage.requiredColumns) `
        'Bound-VA refinement matrix columns'
    foreach ($row in $boundVaRows) {
        foreach ($column in @($registry.boundVaRefinementCoverage.requiredColumns)) {
            if ([string]::IsNullOrWhiteSpace([string]$row.$column)) {
                Add-CheckError "Bound-VA refinement row '$($row.bound_constructor)' has empty column '$column'"
            }
        }
    }
}

foreach ($vocabulary in @($registry.implementationVocabulary)) {
    $relativePath = ([string]$vocabulary.path).Replace('\','/')
    [void]$trackedPaths.Add($relativePath)
    $path = Resolve-WorkspacePath $relativePath "Vocabulary $($vocabulary.term)" $vocabularyWorkspace
    if ($null -eq $path -or -not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Add-CheckError "Vocabulary source is missing for $($vocabulary.term): $relativePath"
    } else {
        $codeLines = @((Read-Utf8 $path) -split "`n" | Where-Object {
            $trimmed = $_.Trim()
            -not ($trimmed.StartsWith('//') -or $trimmed.StartsWith('/*') -or $trimmed.StartsWith('*'))
        })
        if (-not (($codeLines -join "`n").Contains([string]$vocabulary.literal))) {
            Add-CheckError "Vocabulary drift for $($vocabulary.term); expected implementation literal '$($vocabulary.literal)'"
        }
    }
}

$expectedMechanizationTheorems = @(
    'image_reflects_membership','image_preserves_subset','exists_over_image','forall_over_image',
    'lift1_source_bound','lift1_bound_validation','lift1_present','lift1_absent','lift1_consumer_agreement',
    'encodeValue_injective','implies_rewrite','forall_rewrite','notEmpty_rewrite',
    'normalize_preserves_eval','normalize_reaches_redex_free','implies_root_strictly_decreases',
    'all_rewrite_semantics','normalize_reaches_normal_form','normalize_idempotent',
    'root_rewrite_strictly_decreases','typed_rewrite_preserves_type',
    'scoped_rename_preserves_binder_boundary','named_to_scoped_semantic_correspondence',
    'java_capture_guard_sound','java_guarded_rename_preserves_scoped_semantics',
    'structural_preservation','java_ir_eval_refinement','bound_va_abstraction','pa_comp',
    'theorem6_forward','theorem6_backward','theorem6_at_object'
)
if ($null -eq $registry.mechanization) {
    Add-CheckError 'Registry: missing mechanization contract'
} else {
    if ([string]$registry.mechanization.framework -cne 'Lean' -or
        [string]$registry.mechanization.version -cne '4.32.2' -or
        [string]$registry.mechanization.toolchain -cne 'leanprover/lean4:v4.32.2') {
        Add-CheckError 'Mechanization framework/version/toolchain drift'
    }
    Test-ExactSequence @($registry.mechanization.requiredTheorems) $expectedMechanizationTheorems 'Registry mechanization theorems'
    Test-ExactSequence @($registry.mechanization.coverage | ForEach-Object { "$($_.id):$($_.status)" }) @(
        'MK-FINITE-SET:mechanized','MK-LIFT1:mechanized','MK-ENCODE:mechanized','MK-NORMALIZE:partial','MK-T4:mechanized',
        'MK-BOUND-VA:mechanized','MK-PA-COMP:mechanized','MK-T6:mechanized'
    ) 'Registry mechanization coverage'
    if (@($registry.mechanization.openScope).Count -eq 0) {
        Add-CheckError 'Mechanization contract must state its open scope'
    }
    foreach ($property in @('sourcePath','checkerPath','mutationCheckerPath')) {
        $relativePath = ([string]$registry.mechanization.$property).Replace('\','/')
        [void]$trackedPaths.Add($relativePath)
        $path = Resolve-WorkspacePath $relativePath "Mechanization $property"
        if ($null -eq $path -or -not (Test-Path -LiteralPath $path -PathType Leaf)) {
            Add-CheckError "Mechanization $property is missing: $relativePath"
        }
    }
    Test-ExactSequence @($registry.mechanization.projectPaths) @(
        'verification/lean/lakefile.lean',
        'verification/lean/lean-toolchain',
        'verification/lean/lake-manifest.json'
    ) 'Registry mechanization project files'
    foreach ($projectPath in @($registry.mechanization.projectPaths)) {
        $relativePath = ([string]$projectPath).Replace('\','/')
        [void]$trackedPaths.Add($relativePath)
        $path = Resolve-WorkspacePath $relativePath 'Mechanization project file'
        if ($null -eq $path -or -not (Test-Path -LiteralPath $path -PathType Leaf)) {
            Add-CheckError "Mechanization project file is missing: $relativePath"
        }
    }
    $externalChecker = $registry.mechanization.externalChecker
    if ($null -eq $externalChecker -or
        [string]$externalChecker.kind -cne 'nanoda-ndjson' -or
        [string]$externalChecker.lean4exportCommit -cne '9fb131bb100eb32ccf6836f14e4f8328d13b6792' -or
        [string]$externalChecker.nanodaCommit -cne '418320295890faed83a96fd97907b12a3b6728c2' -or
        [string]$externalChecker.upstreamIssue -cne 'https://github.com/leanprover/lean-action/issues/169') {
        Add-CheckError 'Registry pinned nanoda NDJSON checker contract drift'
    } else {
        foreach ($property in @('sourcePath','configPath','workflowPath')) {
            $relativePath = ([string]$externalChecker.$property).Replace('\','/')
            [void]$trackedPaths.Add($relativePath)
            $path = Resolve-WorkspacePath $relativePath "External checker $property"
            if ($null -eq $path -or -not (Test-Path -LiteralPath $path -PathType Leaf)) {
                Add-CheckError "External checker $property is missing: $relativePath"
            }
        }

        $externalScriptPath = Resolve-WorkspacePath ([string]$externalChecker.sourcePath) 'External checker source'
        if ($null -ne $externalScriptPath -and (Test-Path -LiteralPath $externalScriptPath -PathType Leaf)) {
            $externalScript = Read-Utf8 $externalScriptPath
            foreach ($pin in @(
                "LEAN4EXPORT_COMMIT=`"$([string]$externalChecker.lean4exportCommit)`"",
                "NANODA_COMMIT=`"$([string]$externalChecker.nanodaCommit)`""
            )) {
                if (-not $externalScript.Contains($pin)) {
                    Add-CheckError "External checker source is missing registered pin: $pin"
                }
            }
        }

        $externalConfigPath = Resolve-WorkspacePath ([string]$externalChecker.configPath) 'External checker config'
        if ($null -ne $externalConfigPath -and (Test-Path -LiteralPath $externalConfigPath -PathType Leaf)) {
            try {
                $externalConfig = (Read-Utf8 $externalConfigPath) | ConvertFrom-Json
                Test-ExactSequence @($externalConfig.permitted_axioms) @(
                    'propext','Classical.choice','Quot.sound','Lean.trustCompiler'
                ) 'Nanoda permitted axioms'
                if (-not [bool]$externalConfig.use_stdin -or
                    [bool]$externalConfig.unpermitted_axiom_hard_error -or
                    [bool]$externalConfig.unsafe_permit_all_axioms -or
                    -not [bool]$externalConfig.nat_extension -or
                    -not [bool]$externalConfig.string_extension -or
                    [bool]$externalConfig.print_axioms -or
                    -not [bool]$externalConfig.print_success_message) {
                    Add-CheckError 'Nanoda NDJSON configuration policy drift'
                }
            } catch {
                Add-CheckError "Nanoda configuration is invalid JSON: $($_.Exception.Message)"
            }
        }

        $externalWorkflowPath = Resolve-WorkspacePath ([string]$externalChecker.workflowPath) 'External checker workflow'
        if ($null -ne $externalWorkflowPath -and (Test-Path -LiteralPath $externalWorkflowPath -PathType Leaf)) {
            $externalWorkflow = Read-Utf8 $externalWorkflowPath
            if (-not $externalWorkflow.Contains('nanoda: false') -or
                $externalWorkflow.Contains('nanoda: true') -or
                -not $externalWorkflow.Contains('run: bash ./verification/scripts/check-nanoda.sh')) {
                Add-CheckError 'GitHub workflow does not enforce the pinned nanoda NDJSON gate'
            }
        }
    }
    $proofPath = Resolve-WorkspacePath ([string]$registry.mechanization.sourcePath) 'Mechanization source'
    if ($null -ne $proofPath -and (Test-Path -LiteralPath $proofPath -PathType Leaf)) {
        $proof = Read-Utf8 $proofPath
        if (-not $proof.Contains("def proofContractVersion : String := `"$($registry.version)`"")) {
            Add-CheckError "Mechanization source does not pin contract $($registry.version)"
        }
        if (-not $proof.Contains($registryHash)) {
            Add-CheckError "Mechanization source has stale registry SHA-256; expected $registryHash"
        }
        foreach ($theorem in $expectedMechanizationTheorems) {
            if ($proof -notmatch ('(?m)^\s*theorem\s+' + [regex]::Escape($theorem) + '\b')) {
                Add-CheckError "Mechanization source is missing required theorem $theorem"
            }
        }
        foreach ($audit in @($registry.mechanization.axiomAudit.theorems)) {
            if (-not $proof.Contains("#print axioms $($audit.name)")) {
                Add-CheckError "Mechanization source is missing axiom audit for $($audit.name)"
            }
        }
    }
}

if ($null -eq $registry.artifactPolicy -or
    [string]$registry.artifactPolicy.kind -cne 'machine-verification' -or
    [string]$registry.artifactPolicy.reportSchema -cne 'proof-report.schema.v1' -or
    [string]$registry.artifactPolicy.paperPublication -cne 'external-local-only') {
    Add-CheckError 'Artifact policy must keep local paper sources outside GitHub machine correctness evidence'
} else {
    $reportGenerator = ([string]$registry.artifactPolicy.reportGeneratorPath).Replace('\','/')
    [void]$trackedPaths.Add($reportGenerator)
    $generatorPath = Resolve-WorkspacePath $reportGenerator 'Report generator'
    if ($null -eq $generatorPath -or -not (Test-Path -LiteralPath $generatorPath -PathType Leaf)) {
        Add-CheckError "Report generator is missing: $reportGenerator"
    }
    $baselineEvidence = ([string]$registry.artifactPolicy.baselineEvidencePath).Replace('\','/')
    [void]$trackedPaths.Add($baselineEvidence)
    $baselinePath = Resolve-WorkspacePath $baselineEvidence 'Baseline evidence'
    if ($null -eq $baselinePath -or -not (Test-Path -LiteralPath $baselinePath -PathType Leaf)) {
        Add-CheckError "Baseline evidence is missing: $baselineEvidence"
    }
}

$blocking = @(Get-BlockingObligations $registry)
if ([string]$registry.claimPolicy.prototypeCorrectness -ceq [string]$registry.claimPolicy.activeValue -and
    $blocking.Count -gt 0) {
    Add-CheckError "Prototype correctness claim is active while blocking obligations remain open/partial: $(@($blocking.id) -join ', ')"
}

if ($RequireGitTracked) {
    Push-Location $workspace
    try {
        foreach ($relativePath in @($trackedPaths | Sort-Object)) {
            $previousPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try {
                $gitOutput = @(& git ls-files --error-unmatch -- $relativePath 2>&1)
                $gitExitCode = $LASTEXITCODE
            } finally {
                $ErrorActionPreference = $previousPreference
            }
            if ($gitExitCode -ne 0) {
                Add-CheckError "Required verification input is not tracked by Git: $relativePath"
            }
        }
    } finally {
        Pop-Location
    }
}

if ($errors.Count -gt 0) {
    foreach ($message in $errors) {
        Write-Error $message -ErrorAction Continue
    }
    exit 1
}

if (-not [string]::IsNullOrWhiteSpace($ReportPath)) {
    & (Resolve-WorkspacePath ([string]$registry.artifactPolicy.reportGeneratorPath) 'Report generator') `
        -RegistryPath $RegistryPath -OutputPath $ReportPath
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$trackedStatus = if ($RequireGitTracked) { 'checked' } else { 'skipped' }
Write-Host "Machine verification contract PASS: contract=$($registry.version), schema=$($registry.registrySchemaVersion), theorems=$(@($registry.theorems).Count), obligations=$(@($registry.proofObligations).Count), constructors=$(@($registry.constructorCoverage.features).Count), planConstructors=$(@($registry.planConstructorCoverage.constructors).Count), javaIrConstructors=$(@($registry.javaIrRefinementCoverage.constructors).Count), boundConstructors=$(@($registry.boundVaRefinementCoverage.boundConstructors).Count), semanticIrConstructors=$(@($registry.boundVaRefinementCoverage.vaConstructors).Count), vocabulary=$(@($registry.implementationVocabulary).Count), tracked=$trackedStatus."
