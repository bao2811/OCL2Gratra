param(
    [string]$RegistryPath = (Join-Path $PSScriptRoot '..\contract\proof-contract-registry.json'),
    [string]$MechanizedPath = (Join-Path $PSScriptRoot '..\lean'),
    [string]$LeanPath = '',
    [switch]$AllowMissingToolchain
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
    return [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $Path), [System.Text.Encoding]::UTF8)
}

$workspace = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$registry = (Read-Utf8 $RegistryPath) | ConvertFrom-Json
$registryHash = (Get-FileHash -LiteralPath $RegistryPath -Algorithm SHA256).Hash.ToLowerInvariant()
$proofPath = Join-Path $MechanizedPath 'Ocl2CypherProof.lean'
$toolchainPath = Join-Path $MechanizedPath 'lean-toolchain'
$lakefilePath = Join-Path $MechanizedPath 'lakefile.lean'
$proof = Read-Utf8 $proofPath
$toolchain = (Read-Utf8 $toolchainPath).Trim()
[void](Read-Utf8 $lakefilePath)

$expectedLeanVersion = '4.32.2'
$expectedToolchain = "leanprover/lean4:v$expectedLeanVersion"
$expectedRequiredTheorems = @(
    'image_reflects_membership',
    'image_preserves_subset',
    'exists_over_image',
    'forall_over_image',
    'encodeValue_injective',
    'implies_rewrite',
    'forall_rewrite',
    'notEmpty_rewrite',
    'normalize_preserves_eval',
    'normalize_reaches_redex_free',
    'implies_root_strictly_decreases',
    'all_rewrite_semantics',
    'normalize_reaches_normal_form',
    'normalize_idempotent',
    'root_rewrite_strictly_decreases',
    'typed_rewrite_preserves_type',
    'scoped_rename_preserves_binder_boundary',
    'named_to_scoped_semantic_correspondence',
    'java_capture_guard_sound',
    'java_guarded_rename_preserves_scoped_semantics',
    'structural_preservation',
    'java_ir_eval_refinement',
    'theorem6_forward',
    'theorem6_backward',
    'theorem6_at_object'
)
if ($toolchain -cne $expectedToolchain) {
    Add-CheckError "Lean toolchain drift: expected '$expectedToolchain', actual '$toolchain'"
}
if (-not $proof.Contains("def proofContractVersion : String := `"$($registry.version)`"")) {
    Add-CheckError "Lean proof does not pin registry contract $($registry.version)"
}
if (-not $proof.Contains($registryHash)) {
    Add-CheckError "Lean proof has stale registry SHA-256; expected $registryHash"
}
if (-not $proof.Contains("def pinnedLeanVersion : String := `"$expectedLeanVersion`"")) {
    Add-CheckError "Lean proof does not pin Lean $expectedLeanVersion"
}

$forbidden = @(
    @{ Pattern = '(?m)^\s*axiom\b'; Label = 'axiom declaration' },
    @{ Pattern = '(?m)^\s*opaque\b'; Label = 'opaque declaration' },
    @{ Pattern = '\bsorry\b'; Label = 'sorry placeholder' },
    @{ Pattern = '\badmit\b'; Label = 'admit placeholder' }
)
foreach ($item in $forbidden) {
    if ($proof -match $item.Pattern) {
        Add-CheckError "Lean proof contains forbidden $($item.Label)"
    }
}

$requiredTheorems = @($registry.mechanization.requiredTheorems | ForEach-Object { [string]$_ })
if ($requiredTheorems.Count -ne $expectedRequiredTheorems.Count) {
    Add-CheckError "Registry mechanization theorem count drift: expected $($expectedRequiredTheorems.Count), actual $($requiredTheorems.Count)"
} else {
    for ($i = 0; $i -lt $expectedRequiredTheorems.Count; $i++) {
        if ($requiredTheorems[$i] -cne $expectedRequiredTheorems[$i]) {
            Add-CheckError "Registry mechanization theorem drift at index $i`: expected '$($expectedRequiredTheorems[$i])', actual '$($requiredTheorems[$i])'"
        }
    }
}
foreach ($theorem in $requiredTheorems) {
    if ($proof -notmatch ('(?m)^\s*theorem\s+' + [regex]::Escape($theorem) + '\b')) {
        Add-CheckError "Lean proof is missing required theorem $theorem"
    }
}
$allowedCoreAxioms = @($registry.mechanization.axiomAudit.allowedCoreAxioms | ForEach-Object { [string]$_ })
if ($allowedCoreAxioms.Count -ne 2 -or
    $allowedCoreAxioms[0] -cne 'propext' -or
    $allowedCoreAxioms[1] -cne 'Quot.sound') {
    Add-CheckError "Lean allowed-core-axiom policy drift: expected propext,Quot.sound; actual '$($allowedCoreAxioms -join ',')'"
}
$axiomAuditTheorems = @($registry.mechanization.axiomAudit.theorems)
foreach ($audit in $axiomAuditTheorems) {
    if (-not $proof.Contains("#print axioms $($audit.name)")) {
        Add-CheckError "Lean proof is missing axiom audit for $($audit.name)"
    }
}

if ([string]::IsNullOrWhiteSpace($LeanPath)) {
    $portable = Join-Path $workspace '_build_lib\lean-4.32.2\lean-4.32.2-windows\bin\lean.exe'
    if (Test-Path -LiteralPath $portable -PathType Leaf) {
        $LeanPath = $portable
    } else {
        $command = Get-Command 'lean' -ErrorAction SilentlyContinue
        if ($null -ne $command) { $LeanPath = $command.Source }
    }
}

if ([string]::IsNullOrWhiteSpace($LeanPath) -or -not (Test-Path -LiteralPath $LeanPath -PathType Leaf)) {
    if ($AllowMissingToolchain) {
        Write-Warning "Lean $expectedLeanVersion is unavailable; static mechanization contract checks ran, kernel check skipped."
    } else {
        Add-CheckError "Lean $expectedLeanVersion executable is required but unavailable"
    }
} else {
    $versionOutput = (& $LeanPath '--version' 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or -not $versionOutput.Contains("version $expectedLeanVersion")) {
        Add-CheckError "Lean executable version mismatch: $versionOutput"
    } else {
        $tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
        $outputDirectory = Join-Path $tempRoot ("ocl2cypher-lean-" + [guid]::NewGuid().ToString('N'))
        [void][System.IO.Directory]::CreateDirectory($outputDirectory)
        try {
            $oleanPath = Join-Path $outputDirectory 'Ocl2CypherProof.olean'
            $previousPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try {
                Push-Location (Resolve-Path -LiteralPath $MechanizedPath)
                try {
                    $leanOutput = @(& $LeanPath '-o' $oleanPath (Resolve-Path -LiteralPath $proofPath) 2>&1)
                    $leanExit = $LASTEXITCODE
                } finally {
                    Pop-Location
                }
            } finally {
                $ErrorActionPreference = $previousPreference
            }
            $leanText = $leanOutput | Out-String
            if ($leanExit -ne 0 -or -not (Test-Path -LiteralPath $oleanPath -PathType Leaf)) {
                Add-CheckError "Lean kernel rejected the proof (exit $leanExit): $leanText"
            }
            if ($leanText.Contains('sorryAx')) {
                Add-CheckError 'Lean axiom report contains sorryAx'
            }
            foreach ($match in [regex]::Matches($leanText, 'depends on axioms:\s*\[([^\]]*)\]')) {
                $reported = @($match.Groups[1].Value.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
                foreach ($axiom in $reported) {
                    if ($allowedCoreAxioms -cnotcontains $axiom) {
                        Add-CheckError "Lean axiom report contains unapproved core axiom $axiom"
                    }
                }
            }
            $leanNormalizedText = [regex]::Replace($leanText, '\s+', ' ')
            foreach ($audit in $axiomAuditTheorems) {
                $expected = @($audit.expected | ForEach-Object { [string]$_ })
                $expectedText = if ($expected.Count -eq 0) {
                    "'$($audit.name)' does not depend on any axioms"
                } else {
                    "'$($audit.name)' depends on axioms: [$($expected -join ', ')]"
                }
                $expectedNormalizedText = [regex]::Replace($expectedText, '\s+', ' ')
                if (-not $leanNormalizedText.Contains($expectedNormalizedText)) {
                    Add-CheckError "Lean axiom audit drift for $($audit.name); expected '$expectedText'"
                }
            }
        } finally {
            $resolvedOutput = [System.IO.Path]::GetFullPath($outputDirectory)
            if ($resolvedOutput.StartsWith($tempRoot, [System.StringComparison]::OrdinalIgnoreCase) -and
                (Split-Path $resolvedOutput -Leaf).StartsWith('ocl2cypher-lean-')) {
                Remove-Item -LiteralPath $resolvedOutput -Recurse -Force
            }
        }
    }
}

if ($errors.Count -gt 0) {
    foreach ($message in $errors) { Write-Error $message -ErrorAction Continue }
    exit 1
}

$kernelStatus = if ([string]::IsNullOrWhiteSpace($LeanPath)) { 'skipped' } else { 'PASS' }
Write-Host "Mechanized proof check PASS: contract=$($registry.version), registry=$registryHash, Lean=$expectedLeanVersion, theorems=$($requiredTheorems.Count), kernel=$kernelStatus."
