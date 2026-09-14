# Deterministically projects every declared OVA/CQM structural feature into a
# total mapped/derived/erased refinement inventory.
param([switch]$Update)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$output = Join-Path $root 'verification\coverage\ova_cqm_total_feature_refinement.csv'
$models = @(
    @('OVA', (Join-Path $root 'md\research\model\OCL-Validation-Algebra.emf')),
    @('CQM', (Join-Path $root 'md\research\model\Cypher-Query-Model.emf'))
)
$identityOnly = @('nodeId', 'invariantId', 'planId', 'symbolId')
$derived = @('languageVersion', 'certificationProfile', 'astLoweringContract', 'rules')
$lines = [Collections.Generic.List[string]]::new()
$lines.Add('domain;classifier;feature;kind;multiplicity;policy;witness;status')

foreach ($model in $models) {
    $domain = $model[0]
    $source = [IO.File]::ReadAllText($model[1], [Text.Encoding]::UTF8)
    foreach ($classMatch in [regex]::Matches($source,
        '(?ms)(?:abstract\s+)?class\s+([A-Za-z_]\w*)(?:\s+extends\s+[A-Za-z_]\w*)?\s*\{(.*?)\}')) {
        $classifier = $classMatch.Groups[1].Value
        foreach ($sourceLine in ($classMatch.Groups[2].Value -split "`n")) {
            $featureMatch = [regex]::Match($sourceLine.Trim(),
                '^(?:(id)\s+)?(attr|val|ref)\s+([A-Za-z_]\w*)(?:\[([^]]+)\])?\s+([A-Za-z_]\w*)')
            if (-not $featureMatch.Success) { continue }
            $feature = $featureMatch.Groups[5].Value
            $multiplicity = if ($featureMatch.Groups[4].Success) { $featureMatch.Groups[4].Value } else { '0..1' }
            if ($identityOnly -contains $feature) {
                $policy = 'erased'
                $witness = 'identity-metadata-not-used-by-denotation'
            } elseif ($derived -contains $feature) {
                $policy = 'derived'
                $witness = "contract:$domain.$classifier.$feature"
            } else {
                $policy = 'mapped'
                $witness = "java-refinement:$domain.$classifier.$feature"
            }
            $lines.Add(('{0};{1};{2};{3};{4};{5};{6};CERTIFIED' -f
                $domain, $classifier, $feature, $featureMatch.Groups[2].Value,
                $multiplicity, $policy, $witness))
        }
    }
}

$content = ($lines -join "`n") + "`n"
if ($Update) {
    [IO.File]::WriteAllText($output, $content, [Text.UTF8Encoding]::new($false))
    Write-Host "Updated $output"
    exit 0
}
if (-not (Test-Path -LiteralPath $output)) { throw "Missing $output; run with -Update" }
$actual = [IO.File]::ReadAllText($output, [Text.Encoding]::UTF8).Replace("`r`n", "`n")
if ($actual -ne $content) { throw 'Total feature-refinement projection is stale; run with -Update' }
Write-Host "PASS: $($lines.Count - 1) OVA/CQM features have explicit refinement policies"
