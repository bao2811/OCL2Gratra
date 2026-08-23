param(
    [string]$RegistryPath = (Join-Path $PSScriptRoot '..\..\verification\contract\proof-contract-registry.json'),
    [string]$MarkdownPath = (Join-Path $PSScriptRoot 'formal-theorems-and-proofs.md'),
    [string]$LatexPath = (Join-Path (Split-Path $PSScriptRoot -Parent) 'latex\theorem-lemma-proof.tex'),
    [string]$GeneratedEnglishLatexPath = (Join-Path (Split-Path $PSScriptRoot -Parent) 'latex\theorem-lemma-proof-en.tex'),
    [string]$WorkspacePath = (Join-Path $PSScriptRoot '..\..'),
    [string]$CoverageMatrixPath = '',
    [string]$AdmittedCaseSourcePath = '',
    [string]$VocabularyWorkspacePath = '',
    [string]$LatexArtifactPath = '',
    [string]$LatexManifestPath = '',
    [switch]$SkipArtifactBuild,
    [switch]$RequireLatexCompiler,
    [switch]$AllowLatexPackageInstall,
    [switch]$UpdateProjections
)

$ErrorActionPreference = 'Stop'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$errors = [System.Collections.Generic.List[string]]::new()
$latexBuildStatus = 'skipped'

function Read-Utf8Text([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "File does not exist: $Path"
    }
    return [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $Path), [System.Text.Encoding]::UTF8)
}

function Normalize-Text([string]$Value) {
    return ($Value -replace "`r`n?", "`n").Trim()
}

function Get-Utf8LfSha256FromText([string]$Value) {
    $canonical = $Value.Replace("`r`n", "`n").Replace("`r", "`n")
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $digest = $sha256.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($canonical))
        return ([System.BitConverter]::ToString($digest)).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha256.Dispose()
    }
}

function Get-CanonicalRegistryText([string]$MarkdownText) {
    $begin = '<!-- BEGIN CANONICAL PROOF-CONTRACT REGISTRY JSON -->'
    $end = '<!-- END CANONICAL PROOF-CONTRACT REGISTRY JSON -->'
    $beginCount = ([regex]::Matches($MarkdownText, [regex]::Escape($begin))).Count
    $endCount = ([regex]::Matches($MarkdownText, [regex]::Escape($end))).Count
    if ($beginCount -ne 1 -or $endCount -ne 1) {
        throw "Canonical formal source must contain exactly one registry JSON block; begin=$beginCount end=$endCount"
    }
    $pattern = '(?s)' + [regex]::Escape($begin) + '\r?\n(.*?)\r?\n' + [regex]::Escape($end)
    $match = [regex]::Match($MarkdownText, $pattern)
    if (-not $match.Success) {
        throw 'Canonical registry JSON markers are malformed or out of order'
    }
    return Normalize-Text $match.Groups[1].Value
}

function Add-CheckError([string]$Message) {
    $script:errors.Add($Message)
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
    $ids = @($Items | ForEach-Object { [string]$_.id })
    $duplicates = @($ids | Group-Object | Where-Object { $_.Count -gt 1 } | ForEach-Object { $_.Name })
    if ($duplicates.Count -gt 0) {
        Add-CheckError "$Label contains duplicate IDs: $($duplicates -join ', ')"
    }
}

function Resolve-ContractPath([string]$BasePath, [string]$RelativePath, [string]$Label) {
    $base = [System.IO.Path]::GetFullPath($BasePath)
    $full = [System.IO.Path]::GetFullPath((Join-Path $base $RelativePath))
    $prefix = $base.TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $full.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        Add-CheckError "$Label escapes its configured workspace: $RelativePath"
        return $null
    }
    return $full
}

function Get-MarkedBlock([string]$Text, [string]$BeginMarker, [string]$EndMarker, [string]$Label) {
    $beginCount = ([regex]::Matches($Text, [regex]::Escape($BeginMarker))).Count
    $endCount = ([regex]::Matches($Text, [regex]::Escape($EndMarker))).Count
    if ($beginCount -ne 1 -or $endCount -ne 1) {
        Add-CheckError "$Label must contain exactly one generated block; begin=$beginCount end=$endCount"
        return $null
    }
    $pattern = '(?s)' + [regex]::Escape($BeginMarker) + '.*?' + [regex]::Escape($EndMarker)
    $match = [regex]::Match($Text, $pattern)
    if (-not $match.Success) {
        Add-CheckError "$Label generated block markers are out of order"
        return $null
    }
    return (Normalize-Text $match.Value)
}

function Escape-Latex([string]$Value) {
    $escaped = $Value.Replace('\', '\textbackslash{}')
    $escaped = $escaped.Replace('&', '\&').Replace('%', '\%').Replace('$', '\$')
    $escaped = $escaped.Replace('#', '\#').Replace('_', '\_')
    $escaped = $escaped.Replace('{', '\{').Replace('}', '\}')
    return $escaped
}

function Get-BlockingObligations([object]$Registry) {
    $classifications = @($Registry.claimPolicy.blockingClassifications | ForEach-Object { [string]$_ })
    $statuses = @($Registry.claimPolicy.blockingStatuses | ForEach-Object { [string]$_ })
    return @($Registry.proofObligations | Where-Object {
        $classifications -ccontains [string]$_.classification -and
        $statuses -ccontains [string]$_.status
    })
}

function New-MarkdownKernel([object]$Registry) {
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('<!-- BEGIN GENERATED PROOF-CONTRACT-KERNEL -->')
    $lines.Add('| Contract ID | Statement kernel | Assumptions | Required results |')
    $lines.Add('|---|---|---|---|')
    foreach ($theorem in $Registry.theorems) {
        $lines.Add("| $($theorem.id) | $($theorem.statement) | $(@($theorem.assumptions) -join ', ') | $(@($theorem.requires) -join ', ') |")
    }
    $blocking = @(Get-BlockingObligations $Registry | ForEach-Object { $_.id })
    $blockingText = if ($blocking.Count -eq 0) { 'NONE' } else { $blocking -join ', ' }
    $runtimeIds = @($Registry.runtimeProfiles | ForEach-Object { $_.id }) -join ', '
    $claim = ([string]$Registry.claimPolicy.prototypeCorrectness).ToUpperInvariant()
    $lines.Add("<!-- PROTOTYPE-CORRECTNESS-CLAIM: $claim -->")
    $lines.Add("<!-- BLOCKING-OPEN-OR-PARTIAL: $blockingText -->")
    $lines.Add("<!-- RUNTIME-PROFILE: $runtimeIds -->")
    $lines.Add('<!-- END GENERATED PROOF-CONTRACT-KERNEL -->')
    return ($lines -join "`n")
}

function New-LatexKernel([object]$Registry) {
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('% BEGIN GENERATED PROOF-CONTRACT-KERNEL')
    $lines.Add('\begin{longtable}{p{1.2cm}p{4.5cm}p{2.4cm}p{5.0cm}}')
    $lines.Add('\toprule')
    $lines.Add('ID & Statement kernel & Assumptions & Required results\\')
    $lines.Add('\midrule')
    foreach ($theorem in $Registry.theorems) {
        $statement = Escape-Latex ([string]$theorem.statementVi)
        $assumptions = @($theorem.assumptions) -join ', '
        $requires = @($theorem.requires) -join ', '
        $lines.Add("$($theorem.id) & $statement & $assumptions & $requires\\")
    }
    $lines.Add('\bottomrule')
    $lines.Add('\end{longtable}')
    $blocking = @(Get-BlockingObligations $Registry | ForEach-Object { $_.id })
    $blockingText = if ($blocking.Count -eq 0) { 'NONE' } else { $blocking -join ', ' }
    $runtimeIds = @($Registry.runtimeProfiles | ForEach-Object { $_.id }) -join ', '
    $claim = ([string]$Registry.claimPolicy.prototypeCorrectness).ToUpperInvariant()
    $lines.Add("% PROTOTYPE-CORRECTNESS-CLAIM: $claim")
    $lines.Add("% BLOCKING-OPEN-OR-PARTIAL: $blockingText")
    $lines.Add("% RUNTIME-PROFILE: $runtimeIds")
    $lines.Add('% END GENERATED PROOF-CONTRACT-KERNEL')
    return ($lines -join "`n")
}

function Set-GeneratedProjection([string]$Path, [string]$Text, [string]$BeginMarker,
                                 [string]$EndMarker, [string]$ExpectedBlock,
                                 [string]$RegistryHash) {
    $pattern = '(?s)' + [regex]::Escape($BeginMarker) + '.*?' + [regex]::Escape($EndMarker)
    if (-not [regex]::IsMatch($Text, $pattern)) {
        throw "Cannot update missing generated block in $Path"
    }
    $updated = [regex]::Replace($Text, $pattern, [System.Text.RegularExpressions.MatchEvaluator]{ param($m) $ExpectedBlock }, 1)
    $updated = [regex]::Replace($updated, 'PROOF-REGISTRY-SHA256: [0-9a-fA-F]{64}', "PROOF-REGISTRY-SHA256: $RegistryHash")
    [System.IO.File]::WriteAllText((Resolve-Path -LiteralPath $Path), $updated, $utf8NoBom)
}

function Write-GeneratedEnglishProjection([string]$SourcePath, [string]$DestinationPath,
                                           [string]$SourceHash) {
    $pandoc = Get-Command 'pandoc' -ErrorAction SilentlyContinue
    if ($null -eq $pandoc) {
        throw 'Cannot update the generated English LaTeX projection because pandoc was not found on PATH'
    }

    $destinationFullPath = [System.IO.Path]::GetFullPath($DestinationPath)
    $destinationDirectory = Split-Path $destinationFullPath -Parent
    [void][System.IO.Directory]::CreateDirectory($destinationDirectory)
    $temporaryPath = Join-Path $destinationDirectory (
        '.' + [System.IO.Path]::GetFileName($destinationFullPath) + '.' +
        [guid]::NewGuid().ToString('N') + '.tmp'
    )

    try {
        $arguments = @(
            (Resolve-Path -LiteralPath $SourcePath).Path,
            '--from=gfm+tex_math_dollars',
            '--to=latex',
            '--standalone',
            '--toc',
            '--toc-depth=3',
            '--metadata',
            'title=Detailed Proof Report on Conditional Semantic Preservation for OCL-to-Cypher Validation',
            '--metadata',
            'lang=en',
            '-V',
            'geometry:margin=2.5cm',
            '-V',
            'fontsize=11pt',
            '-V',
            'papersize=a4',
            '-o',
            $temporaryPath
        )
        $pandocOutput = @(& $pandoc.Source @arguments 2>&1)
        $pandocExitCode = $LASTEXITCODE
        if ($pandocExitCode -ne 0 -or -not (Test-Path -LiteralPath $temporaryPath -PathType Leaf)) {
            throw "pandoc failed to generate the English LaTeX projection (exit=$pandocExitCode): $($pandocOutput -join ' | ')"
        }
        $sourceHashAfterPandoc = Get-Utf8LfSha256FromText (Read-Utf8Text $SourcePath)
        if ($sourceHashAfterPandoc -cne $SourceHash) {
            throw "Canonical Markdown changed while Pandoc was generating the English projection; expected $SourceHash, found $sourceHashAfterPandoc. Rerun projection generation from a stable source."
        }

        $generatedBody = [System.IO.File]::ReadAllText($temporaryPath, [System.Text.Encoding]::UTF8)
        $provenanceHeader = @(
            '% GENERATED FILE -- DO NOT EDIT THE SEMANTIC BODY DIRECTLY.',
            '% CANONICAL-MARKDOWN-PATH: ../research/formal-theorems-and-proofs.md',
            "% CANONICAL-MARKDOWN-SHA256: $SourceHash",
            '% Regenerate with md/research/check-proof-sync.ps1 -UpdateProjections.',
            ''
        ) -join "`n"
        [System.IO.File]::WriteAllText(
            $destinationFullPath,
            ($provenanceHeader + $generatedBody.TrimStart([char]0xFEFF)),
            $utf8NoBom
        )
    } finally {
        if (Test-Path -LiteralPath $temporaryPath -PathType Leaf) {
            Remove-Item -LiteralPath $temporaryPath -Force
        }
    }
}

function Test-MarkdownStructure([string]$Text) {
    $fenceCount = ([regex]::Matches($Text, '(?m)^```')).Count
    if (($fenceCount % 2) -ne 0) {
        Add-CheckError "Markdown: unbalanced fenced code blocks ($fenceCount fences)"
    }
    foreach ($heading in @('# 1. Scope and Assumptions', '# 16. Checklist of Remaining Proof Obligations')) {
        if (-not $Text.Contains($heading)) {
            Add-CheckError "Markdown: missing required top-level heading '$heading'"
        }
    }
}

function Test-LatexStructure([string]$Text) {
    $stack = [System.Collections.Generic.Stack[string]]::new()
    foreach ($match in [regex]::Matches($Text, '\\(begin|end)\{([^}]+)\}')) {
        $kind = $match.Groups[1].Value
        $environment = $match.Groups[2].Value
        if ($kind -eq 'begin') {
            $stack.Push($environment)
        } elseif ($stack.Count -eq 0) {
            Add-CheckError "LaTeX: unmatched end environment '$environment'"
        } else {
            $opened = $stack.Pop()
            if ($opened -cne $environment) {
                Add-CheckError "LaTeX: environment mismatch; opened '$opened', closed '$environment'"
            }
        }
    }
    if ($stack.Count -gt 0) {
        Add-CheckError "LaTeX: unclosed environments: $(@($stack.ToArray()) -join ', ')"
    }
}

function Test-LatexCompilation([string]$Path) {
    $compiler = Get-Command 'pdflatex' -ErrorAction SilentlyContinue
    if ($null -eq $compiler) {
        $script:latexBuildStatus = 'compiler-unavailable'
        if ($RequireLatexCompiler) {
            Add-CheckError 'LaTeX: pdflatex is required but was not found on PATH'
        } else {
            Write-Warning 'pdflatex was not found; structural LaTeX parsing passed, compilation was not run.'
        }
        return
    }
    $tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    $outputPath = Join-Path $tempRoot ("proof-contract-" + [guid]::NewGuid().ToString('N'))
    [void][System.IO.Directory]::CreateDirectory($outputPath)
    try {
        Push-Location (Split-Path (Resolve-Path -LiteralPath $Path) -Parent)
        try {
            $previousPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try {
                $compilerArguments = @('-interaction=nonstopmode', '-halt-on-error', '-jobname=proof-contract-check', "-output-directory=$outputPath")
                if ($AllowLatexPackageInstall) {
                    if ($compiler.Source -notmatch 'MiKTeX') {
                        Add-CheckError 'LaTeX: -AllowLatexPackageInstall is supported only for MiKTeX'
                    } else {
                        $compilerArguments = @('--enable-installer') + $compilerArguments
                    }
                } elseif ($compiler.Source -match 'MiKTeX') {
                    # A fresh MiKTeX installation can otherwise block on an
                    # interactive package-install prompt despite nonstopmode.
                    $compilerArguments = @('--disable-installer') + $compilerArguments
                }
                $compilerArguments += (Resolve-Path -LiteralPath $Path)
                $compilerOutput = @(& $compiler.Source @compilerArguments 2>&1)
                $exitCode = $LASTEXITCODE
            } finally {
                $ErrorActionPreference = $previousPreference
            }
        } finally {
            Pop-Location
        }
        $compiledPdf = Join-Path $outputPath 'proof-contract-check.pdf'
        $compiledPdfExists = Test-Path -LiteralPath $compiledPdf -PathType Leaf
        $compiledPdfBytes = if ($compiledPdfExists) { (Get-Item -LiteralPath $compiledPdf).Length } else { 0 }
        if ($exitCode -ne 0 -or -not $compiledPdfExists -or $compiledPdfBytes -le 0) {
            $script:latexBuildStatus = 'failed'
            $tail = @($compilerOutput | Select-Object -Last 25) -join ' | '
            Add-CheckError "LaTeX compilation failed or produced an empty PDF with exit code $exitCode`: $tail"
        } else {
            $script:latexBuildStatus = 'compiled'
            if (-not [string]::IsNullOrWhiteSpace($LatexArtifactPath)) {
                $artifactFullPath = if ([System.IO.Path]::IsPathRooted($LatexArtifactPath)) {
                    [System.IO.Path]::GetFullPath($LatexArtifactPath)
                } else {
                    [System.IO.Path]::GetFullPath((Join-Path $workspace $LatexArtifactPath))
                }
                [void][System.IO.Directory]::CreateDirectory((Split-Path $artifactFullPath -Parent))
                Copy-Item -LiteralPath $compiledPdf -Destination $artifactFullPath -Force

                $artifactInfo = Get-Item -LiteralPath $artifactFullPath
                if ($artifactInfo.Length -le 0) {
                    Add-CheckError "LaTeX artifact is empty after copy: $artifactFullPath"
                } else {
                    $script:latexBuildStatus = 'compiled-artifact'
                    if (-not [string]::IsNullOrWhiteSpace($LatexManifestPath)) {
                        $manifestFullPath = if ([System.IO.Path]::IsPathRooted($LatexManifestPath)) {
                            [System.IO.Path]::GetFullPath($LatexManifestPath)
                        } else {
                            [System.IO.Path]::GetFullPath((Join-Path $workspace $LatexManifestPath))
                        }
                        [void][System.IO.Directory]::CreateDirectory((Split-Path $manifestFullPath -Parent))
                        $compilerVersion = @(& $compiler.Source '--version' 2>&1 | Select-Object -First 1) -join ''
                        $sourceFullPath = [System.IO.Path]::GetFullPath($Path)
                        $workspacePrefix = $workspace.TrimEnd(
                            [System.IO.Path]::DirectorySeparatorChar,
                            [System.IO.Path]::AltDirectorySeparatorChar
                        ) + [System.IO.Path]::DirectorySeparatorChar
                        $sourceDisplayPath = if ($sourceFullPath.StartsWith(
                            $workspacePrefix,
                            [System.StringComparison]::OrdinalIgnoreCase
                        )) {
                            $sourceFullPath.Substring($workspacePrefix.Length).Replace('\', '/')
                        } else {
                            $sourceFullPath.Replace('\', '/')
                        }
                        $manifest = [ordered]@{
                            schemaVersion = 1
                            proofContract = [string]$registry.version
                            registrySha256 = $registryHash
                            sourcePath = $sourceDisplayPath
                            sourceSha256 = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
                            artifactFile = $artifactInfo.Name
                            artifactBytes = $artifactInfo.Length
                            artifactSha256 = (Get-FileHash -LiteralPath $artifactFullPath -Algorithm SHA256).Hash.ToLowerInvariant()
                            compiler = $compilerVersion
                            sourceDateEpoch = [string]$env:SOURCE_DATE_EPOCH
                        }
                        [System.IO.File]::WriteAllText(
                            $manifestFullPath,
                            (($manifest | ConvertTo-Json -Depth 4) + "`n"),
                            $utf8NoBom
                        )
                    }
                }
            }
        }
    } finally {
        $resolvedOutput = [System.IO.Path]::GetFullPath($outputPath)
        if ($resolvedOutput.StartsWith($tempRoot, [System.StringComparison]::OrdinalIgnoreCase) -and
            (Split-Path $resolvedOutput -Leaf).StartsWith('proof-contract-')) {
            Remove-Item -LiteralPath $resolvedOutput -Recurse -Force
        }
    }
}

$workspace = [System.IO.Path]::GetFullPath($WorkspacePath)
$vocabularyWorkspace = if ([string]::IsNullOrWhiteSpace($VocabularyWorkspacePath)) {
    $workspace
} else {
    [System.IO.Path]::GetFullPath($VocabularyWorkspacePath)
}

try {
    $markdown = Read-Utf8Text $MarkdownPath
    $canonicalRegistryText = Get-CanonicalRegistryText $markdown
    $registry = $canonicalRegistryText | ConvertFrom-Json
    $derivedRegistryText = Read-Utf8Text $RegistryPath
    $latex = Read-Utf8Text $LatexPath
    $generatedEnglishLatex = Read-Utf8Text $GeneratedEnglishLatexPath
} catch {
    Write-Error $_
    exit 1
}

$canonicalRegistryFileText = $canonicalRegistryText + "`n"
if ($UpdateProjections) {
    [System.IO.File]::WriteAllText(
        (Resolve-Path -LiteralPath $RegistryPath),
        $canonicalRegistryFileText,
        $utf8NoBom
    )
    $derivedRegistryText = Read-Utf8Text $RegistryPath
} elseif ((Normalize-Text $derivedRegistryText) -cne $canonicalRegistryText) {
    Add-CheckError 'Derived proof registry is stale relative to the canonical JSON block in formal-theorems-and-proofs.md; run sync-proof-contract-from-formal.ps1 -UpdateDerived'
}

$registryHash = Get-Utf8LfSha256FromText $canonicalRegistryFileText
$markdownBegin = '<!-- BEGIN GENERATED PROOF-CONTRACT-KERNEL -->'
$markdownEnd = '<!-- END GENERATED PROOF-CONTRACT-KERNEL -->'
$latexBegin = '% BEGIN GENERATED PROOF-CONTRACT-KERNEL'
$latexEnd = '% END GENERATED PROOF-CONTRACT-KERNEL'
$expectedMarkdownBlock = New-MarkdownKernel $registry
$expectedLatexBlock = New-LatexKernel $registry

if ($UpdateProjections) {
    Set-GeneratedProjection $MarkdownPath $markdown $markdownBegin $markdownEnd $expectedMarkdownBlock $registryHash
    Set-GeneratedProjection $LatexPath $latex $latexBegin $latexEnd $expectedLatexBlock $registryHash
    $markdown = Read-Utf8Text $MarkdownPath
    $latex = Read-Utf8Text $LatexPath
}
$markdownSourceHash = Get-Utf8LfSha256FromText $markdown
if ($UpdateProjections) {
    Write-GeneratedEnglishProjection $MarkdownPath $GeneratedEnglishLatexPath $markdownSourceHash
    $generatedEnglishLatex = Read-Utf8Text $GeneratedEnglishLatexPath
}

if ([int]$registry.registrySchemaVersion -ne 4) {
    Add-CheckError "Registry: expected schema version 4, actual '$($registry.registrySchemaVersion)'"
}
if ([string]$registry.authority.kind -cne 'derived_projection_metadata' -or
    [string]$registry.authority.canonicalPath -cne 'md/research/formal-theorems-and-proofs.md' -or
    [string]$registry.authority.canonicalBlock -cne 'CANONICAL PROOF-CONTRACT REGISTRY JSON') {
    Add-CheckError 'Registry authority metadata must identify formal-theorems-and-proofs.md as the canonical source'
}
Test-ExactSequence @($registry.assumptions.id) @('A1','A2','A3','A4','A5','A6','A7','A8','A9') 'Registry assumptions'
Test-ExactSequence @($registry.scopeLemmas.id) @('M1','M2','M3','M3a','M4','M5') 'Registry scope lemmas'
Test-ExactSequence @($registry.theorems.id) @('PC-T0','PC-T1','PC-T2','PC-T3','PC-T4','PC-T5','PC-T6') 'Registry theorems'
Test-ExactSequence @($registry.semanticFunctions.id) @(1..29 | ForEach-Object { 'SF-{0:d2}' -f $_ }) 'Registry semantic functions'
Test-ExactSequence @($registry.proofObligations.id) @(1..24 | ForEach-Object { 'PO-{0:d2}' -f $_ }) 'Registry proof obligations'
Test-ExactSequence @($registry.implementationVocabulary.term) @('ObjectInstanceOf','InstanceOf','modelKey','classKey','attributeKey','associationKey','sourceQualifiers','targetQualifiers','scalarCodecV1','__oclBottom') 'Registry implementation vocabulary'
Test-UniqueIds @($registry.assumptions) 'Registry assumptions'
Test-UniqueIds @($registry.scopeLemmas) 'Registry scope lemmas'
Test-UniqueIds @($registry.theorems) 'Registry theorems'
Test-UniqueIds @($registry.semanticFunctions) 'Registry semantic functions'
Test-UniqueIds @($registry.evidence) 'Registry evidence'
Test-UniqueIds @($registry.proofObligations) 'Registry proof obligations'

$assumptionIds = @($registry.assumptions | ForEach-Object { [string]$_.id })
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

$evidenceById = @{}
foreach ($evidence in @($registry.evidence)) {
    $evidenceById[[string]$evidence.id] = $evidence
    if (@('file','source','test') -cnotcontains [string]$evidence.kind) {
        Add-CheckError "Evidence $($evidence.id) has invalid kind '$($evidence.kind)'"
    }
    $evidencePath = Resolve-ContractPath $workspace ([string]$evidence.path) "Evidence $($evidence.id)"
    if ($null -eq $evidencePath -or -not (Test-Path -LiteralPath $evidencePath -PathType Leaf)) {
        Add-CheckError "Evidence $($evidence.id) file is missing: $($evidence.path)"
    } elseif (-not [string]::IsNullOrWhiteSpace([string]$evidence.symbol)) {
        $evidenceText = Read-Utf8Text $evidencePath
        if (-not $evidenceText.Contains([string]$evidence.symbol)) {
            Add-CheckError "Evidence $($evidence.id) symbol is missing: $($evidence.symbol)"
        }
    } elseif ([string]$evidence.kind -eq 'test') {
        Add-CheckError "Test evidence $($evidence.id) must name a source symbol"
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
    $CoverageMatrixPath = Resolve-ContractPath $workspace ([string]$registry.constructorCoverage.matrixPath) 'Coverage matrix'
}
if ([string]::IsNullOrWhiteSpace($AdmittedCaseSourcePath)) {
    $AdmittedCaseSourcePath = Resolve-ContractPath $workspace ([string]$registry.constructorCoverage.admittedCaseSourcePath) 'Admitted-case source'
}
if (-not (Test-Path -LiteralPath $CoverageMatrixPath -PathType Leaf)) {
    Add-CheckError "Constructor coverage matrix is missing: $CoverageMatrixPath"
} else {
    $coverageRows = @((Read-Utf8Text $CoverageMatrixPath) | ConvertFrom-Csv)
    Test-ExactSequence @($coverageRows | ForEach-Object { $_.constructor }) @($registry.constructorCoverage.features) 'Constructor registry/matrix'
    foreach ($row in $coverageRows) {
        foreach ($column in @($registry.constructorCoverage.requiredColumns.PSObject.Properties)) {
            if ([string]$row.($column.Name) -cne [string]$column.Value) {
                Add-CheckError "Coverage row '$($row.constructor)' column '$($column.Name)' drift: expected '$($column.Value)', actual '$($row.($column.Name))'"
            }
        }
    }
}
if (-not (Test-Path -LiteralPath $AdmittedCaseSourcePath -PathType Leaf)) {
    Add-CheckError "Admitted-case source is missing: $AdmittedCaseSourcePath"
} else {
    $admittedSource = Read-Utf8Text $AdmittedCaseSourcePath
    $admittedFeatures = @([regex]::Matches($admittedSource, '\bc\("([^"]+)"') | ForEach-Object { $_.Groups[1].Value })
    Test-ExactSequence $admittedFeatures @($registry.constructorCoverage.features) 'Constructor registry/admission source'
}
foreach ($artifactPath in @($registry.constructorCoverage.artifactPaths)) {
    $resolvedArtifact = Resolve-ContractPath $workspace ([string]$artifactPath) 'Constructor artifact'
    if ($null -eq $resolvedArtifact -or -not (Test-Path -LiteralPath $resolvedArtifact -PathType Leaf)) {
        Add-CheckError "Constructor coverage artifact is missing: $artifactPath"
    }
}

foreach ($vocabulary in @($registry.implementationVocabulary)) {
    $sourcePath = Resolve-ContractPath $vocabularyWorkspace ([string]$vocabulary.path) "Vocabulary $($vocabulary.term)"
    if ($null -eq $sourcePath -or -not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
        Add-CheckError "Vocabulary source is missing for $($vocabulary.term): $($vocabulary.path)"
    } else {
        $sourceText = Read-Utf8Text $sourcePath
        if (-not $sourceText.Contains([string]$vocabulary.literal)) {
            Add-CheckError "Vocabulary drift for $($vocabulary.term); expected exact implementation literal '$($vocabulary.literal)'"
        }
    }
}

$expectedMechanizationTheorems = @(
    'image_reflects_membership',
    'image_preserves_subset',
    'exists_over_image',
    'forall_over_image',
    'lift1_source_bound',
    'lift1_bound_validation',
    'lift1_present',
    'lift1_absent',
    'lift1_consumer_agreement',
    'encodeValue_injective',
    'implies_rewrite',
    'forall_rewrite',
    'notEmpty_rewrite',
    'classConforms_trans',
    'every_certified_type_conforms_to_oclAny',
    'unlimitedNatural_conforms_to_integer',
    'set_conformance_is_covariant',
    'decideConforms_iff',
    'extractedHierarchy_decideConforms_iff',
    'nested_decode_encode_value',
    'nested_encodeValue_injective',
    'extensional_nested_encodeValue_injective',
    'extensional_nested_encode_preserves_finiteness',
    'extensional_nested_payload_encode_injective',
    'extensional_nested_payload_encode_preserves_finiteness',
    'canonical_scalar_codec_injective',
    'canonical_scalar_escape_injective',
    'extensional_nested_canonical_payload_encode_injective',
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
    'prod_plan_sim_sound',
    'certified_nva_grammar_complete',
    'spec_plan_sim_sound',
    'bound_va_abstraction',
    'pa_comp',
    'theorem6_forward',
    'theorem6_backward',
    'theorem6_at_object',
    'case_study_entity_id_injective',
    'nested_sequence_payload_injective',
    'nested_sequence_preserves_width',
    'nested_sequence_preserves_inner_widths',
    'nested_scalar_bottom_separated',
    'nary_projection_agreement',
    'nary_projection_noGhost',
    'nested_entity_noGhost'
)
$expectedMechanizationModules = @(
    'verification/lean/Ocl2Cypher/SemanticTypes.lean',
    'verification/lean/Ocl2Cypher/ExtensionalNestedSet.lean',
    'verification/lean/Ocl2Cypher/CanonicalScalarCodec.lean',
    'verification/lean/Ocl2Cypher/CaseStudyVerticalSlices.lean'
)
$expectedMechanizationCoverage = @(
    'MK-FINITE-SET:mechanized',
    'MK-LIFT1:mechanized',
    'MK-ENCODE:mechanized',
    'MK-CONCRETE-TYPING:partial',
    'MK-NESTED-ENCODE:mechanized',
    'MK-NORMALIZE:partial',
    'MK-T4:mechanized',
    'MK-PRODPLAN:mechanized',
    'MK-SPECPLAN:mechanized',
    'MK-BOUND-VA:mechanized',
    'MK-PA-COMP:mechanized',
    'MK-T6:mechanized'
)
$expectedAxiomAuditTheorems = @(
    'Ocl2CypherProof.encodeValue_injective:propext',
    'Ocl2CypherProof.ConcreteOclTyping.classConforms_trans:',
    'Ocl2CypherProof.ConcreteOclTyping.set_conformance_is_covariant:',
    'Ocl2CypherProof.ConcreteOclTyping.decideConforms_iff:propext',
    'Ocl2CypherProof.ConcreteOclTyping.extractedHierarchy_decideConforms_iff:propext',
    'Ocl2CypherProof.NestedEncoding.nested_decode_encode_value:propext',
    'Ocl2CypherProof.NestedEncoding.nested_encodeValue_injective:propext',
    'Ocl2CypherProof.extensional_nested_encodeValue_injective:propext,Quot.sound',
    'Ocl2CypherProof.extensional_nested_encode_preserves_finiteness:propext,Quot.sound',
    'Ocl2CypherProof.extensional_nested_payload_encode_injective:propext,Quot.sound',
    'Ocl2CypherProof.extensional_nested_payload_encode_preserves_finiteness:propext,Quot.sound',
    'Ocl2CypherProof.canonical_scalar_codec_injective:propext,Quot.sound',
    'Ocl2CypherProof.canonical_scalar_escape_injective:propext,Quot.sound',
    'Ocl2CypherProof.extensional_nested_canonical_payload_encode_injective:propext,Quot.sound',
    'Ocl2CypherProof.ToOneLift.lift1_consumer_agreement:',
    'Ocl2CypherProof.BoolExpr.normalize_preserves_eval:propext',
    'Ocl2CypherProof.BoolExpr.normalize_reaches_redex_free:propext',
    'Ocl2CypherProof.Formula.structural_preservation:propext',
    'Ocl2CypherProof.JavaIrRefinement.java_ir_eval_refinement:propext',
    'Ocl2CypherProof.JavaIrRefinement.prod_plan_sim_sound:propext',
    'Ocl2CypherProof.SpecificationPlanRefinement.certified_nva_grammar_complete:propext',
    'Ocl2CypherProof.SpecificationPlanRefinement.spec_plan_sim_sound:',
    'Ocl2CypherProof.BoundVaAbstraction.bound_va_abstraction:',
    'Ocl2CypherProof.AdapterComposition.pa_comp:',
    'Ocl2CypherProof.theorem6_at_object:',
    'Ocl2CypherProof.case_study_entity_id_injective:',
    'Ocl2CypherProof.nested_sequence_payload_injective:propext',
    'Ocl2CypherProof.nested_sequence_preserves_width:propext',
    'Ocl2CypherProof.nested_sequence_preserves_inner_widths:propext,Quot.sound',
    'Ocl2CypherProof.nested_scalar_bottom_separated:propext',
    'Ocl2CypherProof.nary_projection_agreement:propext,Quot.sound',
    'Ocl2CypherProof.nary_projection_noGhost:',
    'Ocl2CypherProof.nested_entity_noGhost:',
    'Ocl2CypherProof.Normalization.all_rewrite_semantics:',
    'Ocl2CypherProof.Normalization.typed_rewrite_preserves_type:propext',
    'Ocl2CypherProof.Normalization.Scoped.scoped_rename_preserves_binder_boundary:propext',
    'Ocl2CypherProof.Normalization.NamedBridge.named_to_scoped_semantic_correspondence:propext,Quot.sound',
    'Ocl2CypherProof.Normalization.NamedBridge.java_capture_guard_sound:propext',
    'Ocl2CypherProof.Normalization.NamedBridge.java_guarded_rename_preserves_scoped_semantics:propext,Quot.sound',
    'Ocl2CypherProof.Normalization.RewriteExpr.normalize_reaches_normal_form:propext',
    'Ocl2CypherProof.Normalization.RewriteExpr.normalize_idempotent:propext',
    'Ocl2CypherProof.Normalization.RewriteExpr.RootRule.root_rewrite_strictly_decreases:propext'
)
if ($null -eq $registry.mechanization) {
    Add-CheckError 'Registry: missing mechanization contract'
} else {
    if ([string]$registry.mechanization.framework -cne 'Lean') {
        Add-CheckError "Mechanization framework drift: expected 'Lean', actual '$($registry.mechanization.framework)'"
    }
    if ([string]$registry.mechanization.version -cne '4.32.2') {
        Add-CheckError "Mechanization version drift: expected '4.32.2', actual '$($registry.mechanization.version)'"
    }
    if ([string]$registry.mechanization.toolchain -cne 'leanprover/lean4:v4.32.2') {
        Add-CheckError "Mechanization toolchain drift: expected 'leanprover/lean4:v4.32.2', actual '$($registry.mechanization.toolchain)'"
    }
    if ([string]$registry.mechanization.releaseAsset -cne 'lean-4.32.2-windows.zip' -or
        [long]$registry.mechanization.releaseAssetBytes -ne 832110051 -or
        [string]$registry.mechanization.releaseAssetSha256 -cne '369c2b480a2a6f8bfb727af42c333c894c4872a73b3503099abad7bef67549fa') {
        Add-CheckError 'Mechanization release-asset identity drift'
    }
    Test-ExactSequence @($registry.mechanization.requiredTheorems) $expectedMechanizationTheorems 'Registry mechanization theorems'
    Test-ExactSequence @($registry.mechanization.axiomAudit.allowedCoreAxioms) @('propext', 'Quot.sound') 'Registry allowed Lean core axioms'
    $actualAxiomAuditTheorems = @($registry.mechanization.axiomAudit.theorems | ForEach-Object {
        "$($_.name):$(@($_.expected) -join ',')"
    })
    Test-ExactSequence $actualAxiomAuditTheorems $expectedAxiomAuditTheorems 'Registry Lean axiom audit'
    $actualMechanizationCoverage = @($registry.mechanization.coverage | ForEach-Object { "$($_.id):$($_.status)" })
    Test-ExactSequence $actualMechanizationCoverage $expectedMechanizationCoverage 'Registry mechanization coverage'
    foreach ($coverage in @($registry.mechanization.coverage)) {
        if ([string]::IsNullOrWhiteSpace([string]$coverage.scope)) {
            Add-CheckError "Mechanization coverage $($coverage.id) lacks an explicit scope"
        }
    }
    if (@($registry.mechanization.openScope).Count -eq 0) {
        Add-CheckError 'Mechanization contract must state its open scope'
    }
    foreach ($property in @('sourcePath','checkerPath','mutationCheckerPath')) {
        $relativePath = [string]$registry.mechanization.$property
        $resolvedPath = Resolve-ContractPath $workspace $relativePath "Mechanization $property"
        if ([string]::IsNullOrWhiteSpace($relativePath) -or $null -eq $resolvedPath -or
            -not (Test-Path -LiteralPath $resolvedPath -PathType Leaf)) {
            Add-CheckError "Mechanization $property is missing: $relativePath"
        }
    }
    Test-ExactSequence @($registry.mechanization.modulePaths) $expectedMechanizationModules 'Registry mechanization modules'
    foreach ($modulePath in @($registry.mechanization.modulePaths)) {
        $resolvedModulePath = Resolve-ContractPath $workspace ([string]$modulePath) 'Mechanization module'
        if ($null -eq $resolvedModulePath -or -not (Test-Path -LiteralPath $resolvedModulePath -PathType Leaf)) {
            Add-CheckError "Mechanization module is missing: $modulePath"
        }
    }
    $mechanizedSourcePath = Resolve-ContractPath $workspace ([string]$registry.mechanization.sourcePath) 'Mechanization source'
    if ($null -ne $mechanizedSourcePath -and (Test-Path -LiteralPath $mechanizedSourcePath -PathType Leaf)) {
        $mechanizedSource = Read-Utf8Text $mechanizedSourcePath
        if (-not $mechanizedSource.Contains("def proofContractVersion : String := `"$($registry.version)`"")) {
            Add-CheckError "Mechanization source does not pin contract $($registry.version)"
        }
        if (-not $mechanizedSource.Contains($registryHash)) {
            Add-CheckError "Mechanization source has stale registry SHA-256; expected $registryHash"
        }
        foreach ($theorem in $expectedMechanizationTheorems) {
            if ($mechanizedSource -notmatch ('(?m)^\s*theorem\s+' + [regex]::Escape($theorem) + '\b')) {
                Add-CheckError "Mechanization source is missing required theorem $theorem"
            }
        }
        foreach ($audit in @($registry.mechanization.axiomAudit.theorems)) {
            if (-not $mechanizedSource.Contains("#print axioms $($audit.name)")) {
                Add-CheckError "Mechanization source is missing axiom audit for $($audit.name)"
            }
        }
    }
}

$blockingObligations = @(Get-BlockingObligations $registry)
if ([string]$registry.claimPolicy.prototypeCorrectness -ceq [string]$registry.claimPolicy.activeValue -and
    $blockingObligations.Count -gt 0) {
    Add-CheckError "Prototype correctness claim is active while required obligations remain open/partial: $(@($blockingObligations.id) -join ', ')"
}

$documents = [ordered]@{ Markdown = $markdown; LaTeX = $latex }
foreach ($entry in $documents.GetEnumerator()) {
    if (-not $entry.Value.Contains([string]$registry.version)) {
        Add-CheckError "$($entry.Key): missing contract version $($registry.version)"
    }
    if (-not $entry.Value.Contains("PROOF-REGISTRY-SHA256: $registryHash")) {
        Add-CheckError "$($entry.Key): stale registry projection; expected SHA256 $registryHash"
    }
    foreach ($item in @($registry.assumptions) + @($registry.scopeLemmas) + @($registry.theorems) + @($registry.semanticFunctions)) {
        $idPattern = '(?<![A-Za-z0-9])' + [regex]::Escape([string]$item.id) + '(?![A-Za-z0-9])'
        if ($entry.Value -notmatch $idPattern) {
            Add-CheckError "$($entry.Key): missing registry identifier $($item.id)"
        }
    }
    foreach ($theorem in @($registry.theorems)) {
        $heading = if ($entry.Key -eq 'LaTeX') {
            "$($theorem.number): $($theorem.titleVi)"
        } else {
            "Theorem $($theorem.number): $($theorem.title)"
        }
        if (-not $entry.Value.Contains($heading)) {
            Add-CheckError "$($entry.Key): theorem title drift for $($theorem.id); expected '$heading'"
        }
    }
    $boundaryTokens = if ($entry.Key -eq 'LaTeX') {
        @('decode_{val}', '\ASTval', '\BoundOCLval')
    } else {
        @('decode_val', 'AST_val', 'BoundOCL_val')
    }
    foreach ($token in $boundaryTokens) {
        if (-not $entry.Value.Contains($token)) {
            Add-CheckError "$($entry.Key): missing source/AST/bound boundary token $token"
        }
    }
    $staleBoundary = if ($entry.Key -eq 'LaTeX') {
        'T_{\mathrm{BIND}}:AST_{OCL}\times MM\rightharpoonup\OCLval'
    } else {
        'T_BIND : OCL_AST x MM ->partial OCL_val'
    }
    if ($entry.Value.Contains($staleBoundary)) {
        Add-CheckError "$($entry.Key): stale conflation of source OCL_val with T_BIND output"
    }
}

$actualMarkdownBlock = Get-MarkedBlock $markdown $markdownBegin $markdownEnd 'Markdown'
if ($null -ne $actualMarkdownBlock -and $actualMarkdownBlock -cne (Normalize-Text $expectedMarkdownBlock)) {
        Add-CheckError 'Markdown: generated theorem/dependency/governance block differs from the canonical formal contract data'
}
$actualLatexBlock = Get-MarkedBlock $latex $latexBegin $latexEnd 'LaTeX'
if ($null -ne $actualLatexBlock -and $actualLatexBlock -cne (Normalize-Text $expectedLatexBlock)) {
        Add-CheckError 'LaTeX: generated theorem/dependency/governance block differs from the canonical formal contract data'
}

Test-MarkdownStructure $markdown
Test-LatexStructure $latex
Test-LatexStructure $generatedEnglishLatex
$englishSourceMarker = '% CANONICAL-MARKDOWN-PATH: ../research/formal-theorems-and-proofs.md'
if (([regex]::Matches($generatedEnglishLatex, '(?m)^' + [regex]::Escape($englishSourceMarker) + '\r?$')).Count -ne 1) {
    Add-CheckError 'Generated English LaTeX must contain exactly one canonical Markdown path marker'
}
$englishHashMatches = [regex]::Matches(
    $generatedEnglishLatex,
    '(?m)^% CANONICAL-MARKDOWN-SHA256: ([0-9a-f]{64})\r?$'
)
if ($englishHashMatches.Count -ne 1) {
    Add-CheckError "Generated English LaTeX must contain exactly one lowercase canonical Markdown SHA256 marker; found $($englishHashMatches.Count)"
} elseif ($englishHashMatches[0].Groups[1].Value -cne $markdownSourceHash) {
    Add-CheckError "Generated English LaTeX is stale; expected canonical Markdown SHA256 $markdownSourceHash, found $($englishHashMatches[0].Groups[1].Value)"
}
if (-not $generatedEnglishLatex.StartsWith('% GENERATED FILE -- DO NOT EDIT THE SEMANTIC BODY DIRECTLY.')) {
    Add-CheckError 'Generated English LaTeX is missing its generated-file banner at byte zero'
}
if (([regex]::Matches($latex, '(?m)^% HISTORICAL-NON-NORMATIVE:')).Count -ne 1) {
    Add-CheckError 'Vietnamese LaTeX must contain exactly one HISTORICAL-NON-NORMATIVE banner'
}
if (-not $latex.Contains('% CANONICAL-SOURCE: ../research/formal-theorems-and-proofs.md')) {
    Add-CheckError 'Vietnamese LaTeX historical banner must identify the canonical Markdown source'
}
if (-not [string]::IsNullOrWhiteSpace($LatexManifestPath) -and
    [string]::IsNullOrWhiteSpace($LatexArtifactPath)) {
    Add-CheckError 'LaTeX: -LatexManifestPath requires -LatexArtifactPath'
}
if ($SkipArtifactBuild -and
    (-not [string]::IsNullOrWhiteSpace($LatexArtifactPath) -or
     -not [string]::IsNullOrWhiteSpace($LatexManifestPath))) {
    Add-CheckError 'LaTeX: artifact output cannot be requested with -SkipArtifactBuild'
}
if (-not $SkipArtifactBuild) {
    Test-LatexCompilation $GeneratedEnglishLatexPath
}

$markdownHashAfterChecks = Get-Utf8LfSha256FromText (Read-Utf8Text $MarkdownPath)
if ($markdownHashAfterChecks -cne $markdownSourceHash) {
    Add-CheckError "Canonical Markdown changed during synchronization checking; expected stable SHA256 $markdownSourceHash, found $markdownHashAfterChecks"
}

if ($errors.Count -gt 0) {
    foreach ($message in $errors) {
        Write-Error $message -ErrorAction Continue
    }
    exit 1
}

Write-Host "Proof synchronization PASS: contract=$($registry.version), schema=$($registry.registrySchemaVersion), theorems=$(@($registry.theorems).Count), obligations=$(@($registry.proofObligations).Count), constructors=$(@($registry.constructorCoverage.features).Count), vocabulary=$(@($registry.implementationVocabulary).Count), latex=$latexBuildStatus."
