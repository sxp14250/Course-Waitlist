<#
.SYNOPSIS
    Runs every test (automated + manual acceptance scripts), compares each manual test case
    with its expected output, and writes the results to docs/MANUAL_TEST_RESULTS.md.

.DESCRIPTION
    1. Builds the project with the Maven Wrapper (this also runs the JUnit suite) and records
       the automated test summary.
    2. Pipes each manual-tests/ac*.txt script through the CLI, saves the actual transcript to
       manual-tests/results/, and compares it - case by case (AC1-1, AC1-2, ...) - with
       manual-tests/expected/*.expected.txt.
    3. Writes a results report naming the tester, date and Java version.

.PARAMETER Tester
    Name of the person running the tests (recorded in the report).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File manual-tests\run-and-record.ps1 -Tester "Your Name"
#>
param(
    [Parameter(Mandatory = $true)] [string] $Tester,
    [string] $ResultsFile = "docs\MANUAL_TEST_RESULTS.md",
    [string] $RawDir = "manual-tests\results"
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $root
[Environment]::CurrentDirectory = $root          # so cmd.exe and java start in the project root
# Send piped input to java as UTF-8 WITHOUT a byte-order mark (a BOM would corrupt the first command).
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
New-Item -ItemType Directory -Force $RawDir | Out-Null
$RawDir = (Resolve-Path $RawDir).Path
$jar = "target\course-waitlist-1.0.0.jar"

# ---------------------------------------------------------------------------------------
# 1. Automated tests (JUnit) + build the jar
# ---------------------------------------------------------------------------------------
Write-Host "Running automated tests and building the jar (mvnw clean package)..."
$autoLog = Join-Path $RawDir "automated-tests.txt"
cmd /c "`"$root\mvnw.cmd`" -B clean package > `"$autoLog`" 2>&1"
$autoLines   = Get-Content $autoLog
$autoSummary = ($autoLines | Select-String -Pattern 'Tests run: \d+, Failures: \d+, Errors: \d+, Skipped: \d+$' | Select-Object -Last 1)
$autoBuild   = ($autoLines | Select-String -Pattern 'BUILD (SUCCESS|FAILURE)' | Select-Object -Last 1)
$autoSummaryText = if ($autoSummary) { ($autoSummary.Line -replace '^\[\w+\]\s*', '') } else { "No test summary found" }
$autoBuildText   = if ($autoBuild) { ($autoBuild.Line -replace '^\[\w+\]\s*', '') } else { "BUILD result not found" }
$autoPass = ($autoBuildText -eq "BUILD SUCCESS")
Write-Host "  $autoSummaryText - $autoBuildText"

if (-not (Test-Path $jar)) {
    Write-Host "Jar was not built (see $autoLog). Manual tests cannot run." -ForegroundColor Red
    exit 1
}

$javaVersion = (cmd /c "java -version 2>&1" | Select-Object -First 1)

# ---------------------------------------------------------------------------------------
# Helper: split a transcript into test cases keyed by their "# ACx-y" marker line
# ---------------------------------------------------------------------------------------
function Split-Cases([string[]] $lines) {
    $cases = [ordered]@{}
    $current = "_preamble"
    $cases[$current] = New-Object System.Collections.Generic.List[string]
    foreach ($line in $lines) {
        if ($line -match '^> # (AC\d-\d+)\b') {
            $current = $Matches[1]
            $cases[$current] = New-Object System.Collections.Generic.List[string]
        }
        $cases[$current].Add($line)
    }
    return $cases
}

# ---------------------------------------------------------------------------------------
# 2. Manual acceptance scripts
# ---------------------------------------------------------------------------------------
$rows = New-Object System.Collections.Generic.List[string]
$passCount = 0; $failCount = 0

foreach ($script in Get-ChildItem "manual-tests\ac*.txt" | Sort-Object Name) {
    $name     = $script.BaseName
    $expected = "manual-tests\expected\$name.expected.txt"
    $actualF  = Join-Path $RawDir "$name.actual.txt"
    Write-Host "Running $($script.Name)..."

    $actual = @(Get-Content $script.FullName | & java -jar $jar)
    [System.IO.File]::WriteAllLines($actualF, [string[]] $actual)   # UTF-8, no BOM

    $actualCases   = Split-Cases $actual
    $expectedCases = Split-Cases (Get-Content $expected)

    foreach ($id in $expectedCases.Keys) {
        if ($id -eq "_preamble") { continue }
        $description = ($expectedCases[$id][0] -replace '^> # AC\d-\d+\s*', '') -replace '\|', '/'
        $exp = ($expectedCases[$id] -join "`n")
        $act = if ($actualCases.Contains($id)) { ($actualCases[$id] -join "`n") } else { "<missing>" }
        if ($exp -eq $act) {
            $result = "PASS"; $passCount++
        } else {
            $result = "**FAIL**"; $failCount++
        }
        $rows.Add("| $id | $description | $result | [$name.actual.txt](../manual-tests/results/$name.actual.txt) |")
    }
}

# ---------------------------------------------------------------------------------------
# 3. Write the report
# ---------------------------------------------------------------------------------------
$total   = $passCount + $failCount
$overall = if ($autoPass -and $failCount -eq 0) { "PASS" } else { "FAIL" }
$stamp   = Get-Date -Format "yyyy-MM-dd HH:mm"

$report = @"
# Manual Test Results

> Generated by ``manual-tests\run-and-record.ps1``. Each manual case's **actual** CLI output was
> compared line by line with its expected output in ``manual-tests/expected/``. The raw transcripts
> are saved in ``manual-tests/results/``.

## Run details

| Item | Value |
|---|---|
| Tester | $Tester |
| Date / time | $stamp |
| Java | $javaVersion |
| Build under test | ``$jar`` |
| **Overall result** | **$overall** |

## 1. Automated tests (JUnit)

| Command | Result | Log |
|---|---|---|
| ``mvnw.cmd clean package`` | $autoSummaryText - **$autoBuildText** | [automated-tests.txt](../manual-tests/results/automated-tests.txt) |

## 2. Manual acceptance tests

**$passCount of $total cases passed**, $failCount failed.

| Case | What it checks | Result | Actual transcript |
|---|---|---|---|
$($rows -join "`n")

The case IDs match [MANUAL_TEST_PLAN.md](MANUAL_TEST_PLAN.md), which lists the inputs and expected output for each one.

## 3. Not testable by hand (covered by automated tests above)

| Behaviour | Covered by |
|---|---|
| Concurrent requests (AC6) | ``CourseEnrollmentConcurrencyTest`` |
| Null / blank learner id | ``CourseEnrollmentServiceTest.InvalidAndRepeated`` |
| Learner deleted from the directory | ``deletedLearnerCanStillDrop``, ``missingLearnerIsSkipped`` |

## 4. Sign-off

Tested by: **$Tester**  ·  Date: $stamp

Tester's notes (optional): ______________________________________________
"@

[System.IO.File]::WriteAllText((Join-Path $root $ResultsFile), $report)   # UTF-8, no BOM
Write-Host ""
Write-Host "Automated: $autoSummaryText - $autoBuildText"
Write-Host "Manual:    $passCount / $total cases passed"
Write-Host "Overall:   $overall"
Write-Host "Report written to $ResultsFile"
