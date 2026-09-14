param(
    [string]$OutputPath = "examples/company2it-benchmark/models/Company2IT_10000_30000.xmi",
    [int]$CompanyCount = 1999,
    [int]$PersonCount = 8000,
    [int]$EmployeeLinkCount = 26002
)

$ErrorActionPreference = "Stop"

$outputDir = Split-Path -Parent $OutputPath
if (-not (Test-Path -LiteralPath $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
}

$sb = [System.Text.StringBuilder]::new()
[void]$sb.AppendLine('<Company2IT:BenchmarkRoot xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI" xmlns:Company2IT="platform:/resource/Company2IT/metamodels/Company2IT.ecore">')

$baseEmployeesPerCompany = [Math]::Floor($EmployeeLinkCount / $CompanyCount)
$extraEmployees = $EmployeeLinkCount % $CompanyCount

for ($i = 0; $i -lt $CompanyCount; $i++) {
    [void]$sb.AppendLine(("  <companies name=""Company_{0}"">" -f $i))

    $managerId = "p{0}" -f (($i * 7) % $PersonCount)
    $managerIndex = (($i * 7) % $PersonCount)
    $managerAge = 18 + ($managerIndex % 50)
    $managerSalary = 2000 + (($managerIndex * 37) % 7000)
    [void]$sb.AppendLine(("    <manager id=""{0}"" firstName=""P{1}"" age=""{2}"" salary=""{3}""/>" -f $managerId, $managerIndex, $managerAge, $managerSalary))

    $employeeCount = $baseEmployeesPerCompany + ($(if ($i -lt $extraEmployees) { 1 } else { 0 }))
    for ($j = 0; $j -lt $employeeCount; $j++) {
        $personIndex = (($i * 13) + $j) % $PersonCount
        $personId = "p{0}" -f $personIndex
        $age = 18 + ($personIndex % 50)
        $salary = 2000 + (($personIndex * 37) % 7000)
        [void]$sb.AppendLine(("    <employee id=""{0}"" firstName=""P{1}"" age=""{2}"" salary=""{3}""/>" -f $personId, $personIndex, $age, $salary))
    }

    [void]$sb.AppendLine("  </companies>")
}

[void]$sb.AppendLine("</Company2IT:BenchmarkRoot>")

[System.IO.File]::WriteAllText((Resolve-Path ".").Path + "\" + $OutputPath.Replace("/", "\"), $sb.ToString(), [System.Text.Encoding]::UTF8)
Write-Host "Generated $OutputPath"
