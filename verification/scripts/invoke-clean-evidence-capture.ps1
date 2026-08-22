param(
    [string]$Revision = 'HEAD',
    [string]$OutputPath = '',
    [string]$MavenRepository = '',
    [string]$LeanPath = '',
    [switch]$RunRuntime,
    [switch]$SkipImplementation,
    [switch]$KeepWorktree
)

$ErrorActionPreference = 'Stop'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$workspace = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$git = (Get-Command git -ErrorAction Stop).Source

function Invoke-Git([string]$WorkingDirectory, [string[]]$Arguments) {
    $savedPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = @(& $git -C $WorkingDirectory @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $savedPreference
    }
    if ($exitCode -ne 0) {
        throw "git $($Arguments -join ' ') failed:`n$($output -join [Environment]::NewLine)"
    }
    return ($output | Out-String).Trim()
}

function Invoke-CaptureCommand([string]$Label, [string]$WorkingDirectory,
                               [string]$Command, [string[]]$Arguments) {
    Write-Host "[$Label] $Command $($Arguments -join ' ')"
    Push-Location $WorkingDirectory
    try {
        $savedPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            $lines = @(& $Command @Arguments 2>&1)
            $exitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $savedPreference
        }
        $lines | ForEach-Object { Write-Host $_ }
        if ($exitCode -ne 0) {
            throw "$Label failed with exit code $exitCode"
        }
        return ($lines | Out-String).Trim()
    } finally {
        Pop-Location
    }
}

function Get-Utf8LfSha256([string]$Path) {
    $text = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
    $canonical = $text.Replace("`r`n", "`n").Replace("`r", "`n")
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $digest = $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($canonical))
        return ([System.BitConverter]::ToString($digest)).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
}

function Resolve-LeanExecutable {
    if (-not [string]::IsNullOrWhiteSpace($LeanPath)) {
        $candidate = [System.IO.Path]::GetFullPath($LeanPath)
        if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            throw "Lean executable does not exist: $candidate"
        }
        return $candidate
    }
    $portable = Join-Path $workspace '_build_lib\lean-4.32.2\lean-4.32.2-windows\bin\lean.exe'
    if (Test-Path -LiteralPath $portable -PathType Leaf) { return $portable }
    $command = Get-Command lean -ErrorAction SilentlyContinue
    if ($null -ne $command) { return $command.Source }
    throw 'Lean 4.32.2 is required for a clean evidence capture; pass -LeanPath explicitly.'
}

function Get-SurefireTotals([string]$ReportDirectory, [string[]]$SuiteNames) {
    $totals = [ordered]@{ tests = 0; failures = 0; errors = 0; skipped = 0; timeSeconds = 0.0 }
    foreach ($suite in $SuiteNames) {
        $path = Join-Path $ReportDirectory ("TEST-org.uet.dse.neo4jtgg.experiment.$suite.xml")
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "Missing Surefire report for runtime suite: $suite"
        }
        [xml]$xml = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
        $node = $xml.testsuite
        $totals.tests += [int]$node.tests
        $totals.failures += [int]$node.failures
        $totals.errors += [int]$node.errors
        $totals.skipped += [int]$node.skipped
        $totals.timeSeconds += [double]::Parse([string]$node.time,
                [System.Globalization.CultureInfo]::InvariantCulture)
    }
    return [pscustomobject]$totals
}

$commit = Invoke-Git $workspace @('rev-parse', "$Revision^{commit}")
$shortCommit = $commit.Substring(0, 12)
$worktree = Join-Path ([System.IO.Path]::GetTempPath()) (
        "ocl2cypher-clean-capture-$shortCommit-" + [guid]::NewGuid().ToString('N'))
$worktreeAdded = $false
$completed = $false
$capturedAt = [DateTimeOffset]::Now.ToString('o')
$gateRows = [System.Collections.Generic.List[string]]::new()
$runtimeTotals = $null

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $workspace "verification\evidence\clean-capture-$shortCommit.tsv"
}
$fullOutput = [System.IO.Path]::GetFullPath($OutputPath)

$criticalPaths = @(
    'md/research/formal-theorems-and-proofs.md',
    'md/research/Plan/planCypher.md',
    'verification/contract/proof-contract-registry.json',
    'verification/lean/Ocl2CypherProof.lean',
    'md/research/check-proof-sync.ps1',
    'verification/scripts/check-verification-contract.ps1',
    'verification/scripts/check-mechanized-proof.ps1',
    'verification/scripts/check-mechanized-proof-mutations.ps1',
    'verification/scripts/check-implementation-conformance.ps1',
    'verification/scripts/invoke-clean-evidence-capture.ps1',
    'verification/scripts/invoke-runtime-matrix.ps1',
    'verification/runtime/neo4j-runtime-matrix.tsv',
    'verification/coverage/ocl_surface_extension_matrix.csv',
    'neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/OclCertifiedSurfaceNormalizer.java',
    'neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/OclSemanticBinder.java',
    'neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/OclValAdmissionPolicy.java',
    'neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclIrBuilder.java',
    'neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclIrOptimizer.java',
    'neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclCypherPlanner.java',
    'neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/OclCypherRenderer.java',
    'neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/RawCypherAst.java',
    'neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/RawCypherParser.java',
    'neo4j-tgg/src/main/java/org/uet/dse/neo4jtgg/ocl/ir/RawCypherRenderer.java',
    'neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/OclGeneratedPropertyTest.java',
    'neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/OclPropertyCaseGenerator.java',
    'neo4j-tgg/src/test/java/org/uet/dse/neo4jtgg/experiment/OclPropertyBasedRealNeo4jTest.java'
)

try {
    Invoke-Git $workspace @('worktree', 'add', '--detach', $worktree, $commit) | Out-Null
    $worktreeAdded = $true
    $dirty = Invoke-Git $worktree @('status', '--porcelain')
    if (-not [string]::IsNullOrWhiteSpace($dirty)) {
        throw "Detached capture worktree is not clean:`n$dirty"
    }

    $pwsh = (Get-Process -Id $PID).Path
    $resolvedLean = Resolve-LeanExecutable
    Invoke-CaptureCommand 'proof-sync' $worktree $pwsh @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
        (Join-Path $worktree 'md\research\check-proof-sync.ps1'), '-SkipArtifactBuild') | Out-Null
    $gateRows.Add("proof-sync`tPASS")

    Invoke-CaptureCommand 'machine-contract' $worktree $pwsh @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
        (Join-Path $worktree 'verification\scripts\check-verification-contract.ps1'),
        '-RequireGitTracked') | Out-Null
    $gateRows.Add("machine-contract`tPASS")

    Invoke-CaptureCommand 'lean-kernel' $worktree $pwsh @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
        (Join-Path $worktree 'verification\scripts\check-mechanized-proof.ps1'),
        '-LeanPath', $resolvedLean) | Out-Null
    $gateRows.Add("lean-kernel`tPASS")

    Invoke-CaptureCommand 'machine-mutations' $worktree $pwsh @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
        (Join-Path $worktree 'verification\scripts\check-verification-contract-mutations.ps1')) | Out-Null
    $gateRows.Add("machine-mutations`tPASS")

    Invoke-CaptureCommand 'lean-mutations' $worktree $pwsh @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
        (Join-Path $worktree 'verification\scripts\check-mechanized-proof-mutations.ps1'),
        '-LeanPath', $resolvedLean) | Out-Null
    $gateRows.Add("lean-mutations`tPASS")

    if (-not $SkipImplementation) {
        $args = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
            (Join-Path $worktree 'verification\scripts\check-implementation-conformance.ps1'),
            '-Offline')
        if (-not [string]::IsNullOrWhiteSpace($MavenRepository)) {
            $args += @('-MavenRepository', [System.IO.Path]::GetFullPath($MavenRepository))
        }
        Invoke-CaptureCommand 'implementation-conformance' $worktree $pwsh $args | Out-Null
        $gateRows.Add("implementation-conformance`tPASS")
    }

    if ($RunRuntime) {
        $runtimeSuites = @(
            'Cypher5ValDialectRealNeo4jTest',
            'OclValRealNeo4jCoverageTest',
            'AdapterAdequacyCertificateRealNeo4jTest',
            'OclValSemanticDiscriminatorRealNeo4jTest',
            'FamiliesToPersonsCaseStudyRealNeo4jTest'
        )
        $mavenArgs = @()
        if (-not [string]::IsNullOrWhiteSpace($MavenRepository)) {
            $mavenArgs += "-Dmaven.repo.local=$([System.IO.Path]::GetFullPath($MavenRepository))"
        }
        $mavenArgs += @('-pl', 'neo4j-tgg',
            "-Dtest=$($runtimeSuites -join ',')",
            '-Dneo4j.dialect.it=true', '-Dneo4j.oclval47.it=true',
            '-Dneo4j.adapter.certificate.it=true', '-Dneo4j.oclval.discriminator.it=true',
            '-Dneo4j.families.persons.it=true', 'test')
        Invoke-CaptureCommand 'real-neo4j-runtime' $worktree 'mvn' $mavenArgs | Out-Null
        $runtimeTotals = Get-SurefireTotals (Join-Path $worktree 'neo4j-tgg\target\surefire-reports') $runtimeSuites
        if ($runtimeTotals.failures -ne 0 -or $runtimeTotals.errors -ne 0 -or
                $runtimeTotals.skipped -ne 0) {
            throw "Runtime capture is not publishable: $($runtimeTotals | ConvertTo-Json -Compress)"
        }
        $gateRows.Add("real-neo4j-runtime`tPASS")

        $propertyMatrixOutput = Join-Path ([System.IO.Path]::GetTempPath()) (
                "ocl2cypher-property-matrix-$shortCommit-" + [guid]::NewGuid().ToString('N') + '.tsv')
        try {
            $matrixArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
                (Join-Path $worktree 'verification\scripts\invoke-runtime-matrix.ps1'), '-Offline',
                '-OutputPath', $propertyMatrixOutput)
            if (-not [string]::IsNullOrWhiteSpace($MavenRepository)) {
                $matrixArgs += @('-MavenRepository', [System.IO.Path]::GetFullPath($MavenRepository))
            }
            Invoke-CaptureCommand 'property-runtime-matrix' $worktree $pwsh $matrixArgs | Out-Null
            $gateRows.Add("property-runtime-matrix`tPASS")
        } finally {
            if (Test-Path -LiteralPath $propertyMatrixOutput -PathType Leaf) {
                Remove-Item -LiteralPath $propertyMatrixOutput -Force
            }
        }
    }

    $dirtyAfter = Invoke-Git $worktree @('status', '--porcelain')
    if (-not [string]::IsNullOrWhiteSpace($dirtyAfter)) {
        throw "Capture gates changed tracked files:`n$dirtyAfter"
    }

    $rows = [System.Collections.Generic.List[string]]::new()
    $rows.Add("# schema`tclean-evidence-capture-v1")
    $rows.Add("# capturedAt`t$capturedAt")
    $rows.Add("# sourceRevision`t$commit")
    $rows.Add("# gitDirtyAtCapture`tfalse")
    $rows.Add("# runtimeExecuted`t$($RunRuntime.ToString().ToLowerInvariant())")
    if ($null -ne $runtimeTotals) {
        $rows.Add("# runtimeTests`t$($runtimeTotals.tests)")
        $rows.Add("# runtimeFailures`t$($runtimeTotals.failures)")
        $rows.Add("# runtimeErrors`t$($runtimeTotals.errors)")
        $rows.Add("# runtimeSkipped`t$($runtimeTotals.skipped)")
        $rows.Add("# runtimeSeconds`t$($runtimeTotals.timeSeconds.ToString('0.000', [System.Globalization.CultureInfo]::InvariantCulture))")
    }
    $rows.Add('gate' + "`t" + 'status')
    $gateRows.ForEach({ param($row) $rows.Add($row) })
    $rows.Add('path' + "`t" + 'commitBlob' + "`t" + 'worktreeBlob' + "`t" + 'blobMatch' + "`t" + 'sha256Utf8Lf')
    foreach ($relative in $criticalPaths) {
        $absolute = Join-Path $worktree ($relative.Replace('/', '\'))
        if (-not (Test-Path -LiteralPath $absolute -PathType Leaf)) {
            throw "Critical capture path is missing from revision $commit`: $relative"
        }
        $commitBlob = Invoke-Git $workspace @('rev-parse', "$commit`:$relative")
        $worktreeBlob = Invoke-Git $worktree @('hash-object', '--', $relative)
        $match = $commitBlob -ceq $worktreeBlob
        if (-not $match) { throw "Commit/worktree blob mismatch: $relative" }
        $rows.Add("$relative`t$commitBlob`t$worktreeBlob`ttrue`t$(Get-Utf8LfSha256 $absolute)")
    }

    $parent = Split-Path $fullOutput -Parent
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) {
        [System.IO.Directory]::CreateDirectory($parent) | Out-Null
    }
    [System.IO.File]::WriteAllText($fullOutput, ($rows -join "`n") + "`n", $utf8NoBom)
    $completed = $true
    Write-Host "Clean evidence capture PASS: revision=$commit, output=$fullOutput"
} finally {
    if ($worktreeAdded -and (-not $KeepWorktree -or -not $completed)) {
        try {
            Invoke-Git $workspace @('worktree', 'remove', '--force', $worktree) | Out-Null
        } catch {
            Write-Warning "Could not remove temporary worktree $worktree`: $($_.Exception.Message)"
        }
    } elseif ($worktreeAdded) {
        Write-Host "Retained clean worktree: $worktree"
    }
}
