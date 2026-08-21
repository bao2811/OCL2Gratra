# Generate-EcoreFromEmfatic.ps1 -- deterministic OVA/CQM Ecore generator.
param(
    [Parameter(Mandatory=$true)][string]$InputPath,
    [Parameter(Mandatory=$true)][string]$OutputPath
)

$ErrorActionPreference = 'Stop'

function Xml([string]$value) {
    if ($null -eq $value) { return '' }
    return [System.Security.SecurityElement]::Escape($value)
}

function TypeRef([string]$type) {
    switch ($type) {
        'String'  { return 'ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString' }
        'boolean' { return 'ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EBoolean' }
        'int'     { return 'ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EInt' }
        'long'    { return 'ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//ELong' }
        'double'  { return 'ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EDouble' }
        default   { return "#//$type" }
    }
}

function Bounds([string]$multiplicity) {
    if ([string]::IsNullOrWhiteSpace($multiplicity)) { return @{ lower = 0; upper = 1 } }
    switch ($multiplicity) {
        '1' { return @{ lower = 1; upper = 1 } }
        '+' { return @{ lower = 1; upper = -1 } }
        '*' { return @{ lower = 0; upper = -1 } }
        default { throw "Unsupported multiplicity [$multiplicity]" }
    }
}

$source = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $InputPath), [Text.Encoding]::UTF8)
$source = [regex]::Replace($source, '(?m)^\s*//.*$', '')
$namespace = [regex]::Match($source, '@namespace\(uri="([^"]+)",\s*prefix="([^"]+)"\)')
$package = [regex]::Match($source, '(?m)^package\s+([A-Za-z_][A-Za-z0-9_]*)\s*;')
if (-not $namespace.Success -or -not $package.Success) { throw "Missing namespace or package in $InputPath" }
$name = $package.Groups[1].Value
$nsUri = $namespace.Groups[1].Value
$nsPrefix = $namespace.Groups[2].Value

$enums = New-Object System.Collections.Generic.List[object]
foreach ($match in [regex]::Matches($source, '(?ms)enum\s+([A-Za-z_][A-Za-z0-9_]*)\s*\{(.*?)\}')) {
    $literals = New-Object System.Collections.Generic.List[string]
    foreach ($literal in [regex]::Matches($match.Groups[2].Value, '(?m)^\s*([A-Za-z_][A-Za-z0-9_]*)\s*;')) {
        $literals.Add($literal.Groups[1].Value)
    }
    $enums.Add([pscustomobject]@{ name = $match.Groups[1].Value; literals = $literals })
}

$classes = New-Object System.Collections.Generic.List[object]
foreach ($match in [regex]::Matches($source, '(?ms)(abstract\s+)?class\s+([A-Za-z_][A-Za-z0-9_]*)(?:\s+extends\s+([A-Za-z_][A-Za-z0-9_]*))?\s*\{(.*?)\}')) {
    $features = New-Object System.Collections.Generic.List[object]
    foreach ($line in ($match.Groups[4].Value -split "`n")) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed)) { continue }
        $feature = [regex]::Match($trimmed, '^(?:(id)\s+)?(attr|val|ref)\s+([A-Za-z_][A-Za-z0-9_]*)(?:\[([+*1])\])?\s+([A-Za-z_][A-Za-z0-9_]*)(?:\s*=\s*(.*?))?;\s*$')
        if (-not $feature.Success) { throw "Cannot parse feature '$trimmed' in $InputPath" }
        $bounds = Bounds $feature.Groups[4].Value
        $default = $feature.Groups[6].Value.Trim()
        if ($default.StartsWith('"') -and $default.EndsWith('"')) { $default = $default.Substring(1, $default.Length - 2) }
        $features.Add([pscustomobject]@{
            id = $feature.Groups[1].Success
            kind = $feature.Groups[2].Value
            type = $feature.Groups[3].Value
            lower = $bounds.lower
            upper = $bounds.upper
            name = $feature.Groups[5].Value
            default = $default
        })
    }
    $classes.Add([pscustomobject]@{
        name = $match.Groups[2].Value
        abstract = $match.Groups[1].Success
        super = $match.Groups[3].Value
        features = $features
    })
}

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add('<?xml version="1.0" encoding="UTF-8"?>')
$lines.Add('<ecore:EPackage xmi:version="2.0"')
$lines.Add('    xmlns:xmi="http://www.omg.org/XMI"')
$lines.Add('    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"')
$lines.Add('    xmlns:ecore="http://www.eclipse.org/emf/2002/Ecore"')
$lines.Add(('    name="{0}" nsURI="{1}" nsPrefix="{2}">' -f (Xml $name), (Xml $nsUri), (Xml $nsPrefix)))
$lines.Add('')
foreach ($enum in $enums) {
    $lines.Add(('  <eClassifiers xsi:type="ecore:EEnum" name="{0}">' -f (Xml $enum.name)))
    $index = 0
    foreach ($literal in $enum.literals) {
        $lines.Add(('    <eLiterals name="{0}" value="{1}"/>' -f (Xml $literal), $index))
        $index++
    }
    $lines.Add('  </eClassifiers>')
}
foreach ($class in $classes) {
    $abstract = if ($class.abstract) { ' abstract="true"' } else { '' }
    $super = if ([string]::IsNullOrWhiteSpace($class.super)) { '' } else { ' eSuperTypes="#//' + $class.super + '"' }
    $lines.Add(('  <eClassifiers xsi:type="ecore:EClass" name="{0}"{1}{2}>' -f (Xml $class.name), $abstract, $super))
    foreach ($feature in $class.features) {
        $type = TypeRef $feature.type
        $bounds = ''
        if ($feature.lower -ne 0) { $bounds += (' lowerBound="{0}"' -f $feature.lower) }
        if ($feature.upper -ne 1) { $bounds += (' upperBound="{0}"' -f $feature.upper) }
        if ($feature.kind -eq 'attr') {
            $id = if ($feature.id) { ' iD="true"' } else { '' }
            $default = if ([string]::IsNullOrWhiteSpace($feature.default)) { '' } else { ' defaultValueLiteral="' + (Xml $feature.default) + '"' }
            $lines.Add(('    <eStructuralFeatures xsi:type="ecore:EAttribute" name="{0}"{1}{2}{3} eType="{4}"/>' -f (Xml $feature.name), $bounds, $id, $default, $type))
        } else {
            $containment = if ($feature.kind -eq 'val') { ' containment="true"' } else { '' }
            $id = if ($feature.id) { ' iD="true"' } else { '' }
            $lines.Add(('    <eStructuralFeatures xsi:type="ecore:EReference" name="{0}"{1}{2}{3} eType="{4}"/>' -f (Xml $feature.name), $bounds, $containment, $id, $type))
        }
    }
    $lines.Add('  </eClassifiers>')
}
$lines.Add('</ecore:EPackage>')
$utf8 = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText((Join-Path (Get-Location) $OutputPath), ($lines -join "`n"), $utf8)
Write-Host "Generated $OutputPath from $InputPath"
