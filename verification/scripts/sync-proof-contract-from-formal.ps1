param(
    [string]$FormalPath = (Join-Path $PSScriptRoot '..\..\md\research\formal-theorems-and-proofs.md'),
    [string]$RegistryPath = (Join-Path $PSScriptRoot '..\contract\proof-contract-registry.json'),
    [switch]$UpdateDerived,
    [switch]$BootstrapFromDerivedRegistry
)

$ErrorActionPreference = 'Stop'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$beginMarker = '<!-- BEGIN CANONICAL PROOF-CONTRACT REGISTRY JSON -->'
$endMarker = '<!-- END CANONICAL PROOF-CONTRACT REGISTRY JSON -->'

function Read-Utf8([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Missing file: $Path"
    }
    return [System.IO.File]::ReadAllText(
        (Resolve-Path -LiteralPath $Path),
        [System.Text.Encoding]::UTF8
    )
}

function Normalize-Lf([string]$Text) {
    return $Text.Replace("`r`n", "`n").Replace("`r", "`n").Trim()
}

function Get-CanonicalRegistryText([string]$FormalText) {
    $beginCount = ([regex]::Matches($FormalText, [regex]::Escape($beginMarker))).Count
    $endCount = ([regex]::Matches($FormalText, [regex]::Escape($endMarker))).Count
    if ($beginCount -ne 1 -or $endCount -ne 1) {
        throw "The formal source must contain exactly one canonical registry block; begin=$beginCount end=$endCount"
    }
    $pattern = '(?s)' + [regex]::Escape($beginMarker) + '\r?\n(.*?)\r?\n' + [regex]::Escape($endMarker)
    $match = [regex]::Match($FormalText, $pattern)
    if (-not $match.Success) {
        throw 'Canonical registry block markers are malformed or out of order'
    }
    $json = Normalize-Lf $match.Groups[1].Value
    try {
        [void]($json | ConvertFrom-Json)
    } catch {
        throw "Canonical registry block is not valid JSON: $($_.Exception.Message)"
    }
    return $json
}

$formal = Read-Utf8 $FormalPath

if ($BootstrapFromDerivedRegistry) {
    if ($formal.Contains($beginMarker) -or $formal.Contains($endMarker)) {
        throw 'Refusing to bootstrap: the formal source already contains a canonical registry marker'
    }
    $registry = Normalize-Lf (Read-Utf8 $RegistryPath)
    try {
        [void]($registry | ConvertFrom-Json)
    } catch {
        throw "Derived registry is not valid JSON: $($_.Exception.Message)"
    }
    $anchor = '<!-- BEGIN GENERATED PROOF-CONTRACT-KERNEL -->'
    if (([regex]::Matches($formal, [regex]::Escape($anchor))).Count -ne 1) {
        throw 'Cannot bootstrap because the generated proof-contract kernel anchor is not unique'
    }
    $block = @(
        '<!-- The JSON block below is canonical contract data owned by this formal document.',
        '     Edit it here, then run verification/scripts/sync-proof-contract-from-formal.ps1 -UpdateDerived.',
        '     verification/contract/proof-contract-registry.json is generated from this block. -->',
        $beginMarker,
        $registry,
        $endMarker,
        ''
    ) -join "`n"
    $formal = $formal.Replace($anchor, $block + $anchor)
    [System.IO.File]::WriteAllText((Resolve-Path -LiteralPath $FormalPath), $formal, $utf8NoBom)
    Write-Host "Bootstrapped canonical registry data into $FormalPath"
    exit 0
}

$canonical = Get-CanonicalRegistryText $formal
$derived = Normalize-Lf (Read-Utf8 $RegistryPath)

if ($UpdateDerived) {
    [System.IO.File]::WriteAllText(
        (Resolve-Path -LiteralPath $RegistryPath),
        $canonical + "`n",
        $utf8NoBom
    )
    Write-Host "Updated derived registry from canonical formal source: $RegistryPath"
    exit 0
}

if ($derived -cne $canonical) {
    Write-Error "Derived registry is stale. Edit the canonical JSON block in $FormalPath and run this script with -UpdateDerived."
    exit 1
}

Write-Host 'Canonical formal source and derived proof registry are synchronized.'
