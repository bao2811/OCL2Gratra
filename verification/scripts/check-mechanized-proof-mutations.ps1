param(
    [string]$CheckerPath = (Join-Path $PSScriptRoot 'check-mechanized-proof.ps1'),
    [string]$RegistryPath = (Join-Path $PSScriptRoot '..\contract\proof-contract-registry.json'),
    [string]$MechanizedPath = (Join-Path $PSScriptRoot '..\lean'),
    [string]$LeanPath = ''
)

$ErrorActionPreference = 'Stop'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$workspace = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$checker = [System.IO.Path]::GetFullPath($CheckerPath)
$hostExecutable = (Get-Process -Id $PID).Path
$mutationRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("lean-proof-mutations-" + [guid]::NewGuid().ToString('N'))
$passed = 0
$total = 6

function Read-Text([string]$Path) {
    return [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
}

function Get-Utf8LfSha256([string]$Path) {
    $canonical = (Read-Text $Path).Replace("`r`n", "`n").Replace("`r", "`n")
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $digest = $sha256.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($canonical))
        return ([System.BitConverter]::ToString($digest)).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha256.Dispose()
    }
}

function Write-Text([string]$Path, [string]$Value) {
    [System.IO.File]::WriteAllText($Path, $Value, $utf8NoBom)
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
    throw 'Lean 4.32.2 is required for mechanized-proof mutation testing'
}

function New-Case([string]$Name) {
    $casePath = Join-Path $mutationRoot $Name
    $mechanizedCase = Join-Path $casePath 'mechanized'
    [void][System.IO.Directory]::CreateDirectory($mechanizedCase)
    foreach ($name in @('Ocl2CypherProof.lean','lean-toolchain','lakefile.lean','lake-manifest.json')) {
        Copy-Item -LiteralPath (Join-Path $MechanizedPath $name) -Destination (Join-Path $mechanizedCase $name)
    }
    return [pscustomobject]@{
        Root = $casePath
        Mechanized = $mechanizedCase
        Proof = Join-Path $mechanizedCase 'Ocl2CypherProof.lean'
    }
}

function Invoke-Checker([object]$Case) {
    $arguments = @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $checker,
        '-RegistryPath', (Resolve-Path -LiteralPath $RegistryPath),
        '-MechanizedPath', $Case.Mechanized,
        '-LeanPath', $LeanPath
    )
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

function Assert-Killed([string]$Name, [object]$Case, [string]$ExpectedText) {
    $result = Invoke-Checker $Case
    if ($result.ExitCode -eq 0) {
        throw "Mutation '$Name' survived"
    }
    # PowerShell can inject InvocationInfo between wrapped fragments of a
    # native child error when the checkout path is long. Match all expected
    # message tokens in order so the mutation must still fail for the intended
    # reason without depending on host-specific line wrapping.
    $expectedTokens = @([regex]::Split($ExpectedText.Trim(), '\s+') |
        Where-Object { $_ } | ForEach-Object { [regex]::Escape($_) })
    $expectedPattern = '(?s)' + ($expectedTokens -join '.*?')
    if (-not [regex]::IsMatch($result.Output, $expectedPattern)) {
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
        throw "Mechanized-proof baseline failed: $($baselineResult.Output)"
    }

    $case = New-Case 'stale-registry'
    $text = Read-Text $case.Proof
    $registryHash = Get-Utf8LfSha256 $RegistryPath
    Write-Text $case.Proof ($text.Replace($registryHash, (('0' * 64) -join '')))
    Assert-Killed 'stale proof registry hash' $case 'Lean proof has stale registry SHA-256'

    $case = New-Case 'sorry-placeholder'
    $text = Read-Text $case.Proof
    Write-Text $case.Proof ($text + "`n theorem injectedSorry : True := by sorry`n")
    Assert-Killed 'sorry placeholder' $case 'placeholder'

    $case = New-Case 'axiom-declaration'
    $text = Read-Text $case.Proof
    Write-Text $case.Proof ($text + "`naxiom injectedBridge : True`n")
    Assert-Killed 'axiom declaration' $case 'declaration'

    $case = New-Case 'missing-theorem'
    $text = Read-Text $case.Proof
    Write-Text $case.Proof ($text.Replace('theorem theorem6_backward ', 'theorem theorem6_backward_REMOVED '))
    Assert-Killed 'missing required theorem' $case 'theorem6_backward'

    $case = New-Case 'kernel-type-error'
    $text = Read-Text $case.Proof
    $validLine = '  exact PSet.mem_image id graphViolates object ((agreement object).mp h)'
    if (-not $text.Contains($validLine)) { throw 'Cannot find theorem6_forward proof line' }
    Write-Text $case.Proof ($text.Replace($validLine, '  exact h'))
    Assert-Killed 'kernel type error' $case 'kernel rejected'

    $case = New-Case 'unapproved-core-axiom'
    $text = Read-Text $case.Proof
    $newline = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
    $headerPattern = '(?s)(theorem theorem6_at_object\b.*? := by\r?\n)'
    $headerMatch = [regex]::Match($text, $headerPattern)
    if (-not $headerMatch.Success) { throw 'Cannot find theorem6_at_object proof header' }
    $mutatedHeader = $headerMatch.Value +
        '  have chosen : True := Classical.choice (Nonempty.intro True.intro)' + $newline +
        '  cases chosen' + $newline
    Write-Text $case.Proof ($text.Remove($headerMatch.Index, $headerMatch.Length).Insert($headerMatch.Index, $mutatedHeader))
    Assert-Killed 'unapproved core axiom' $case 'core axiom'

    Write-Host "Mechanized proof mutation tests PASS: $passed/$total killed."
} finally {
    $resolvedRoot = [System.IO.Path]::GetFullPath($mutationRoot)
    $tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if ($resolvedRoot.StartsWith($tempRoot, [System.StringComparison]::OrdinalIgnoreCase) -and
        (Split-Path $resolvedRoot -Leaf).StartsWith('lean-proof-mutations-')) {
        Remove-Item -LiteralPath $resolvedRoot -Recurse -Force
    }
}

# The last deliberately rejected child checker exits non-zero. Do not leak that
# expected mutation exit code to the calling PowerShell/GitHub Actions process.
$global:LASTEXITCODE = 0
