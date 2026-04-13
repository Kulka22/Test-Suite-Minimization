# Launch Instruction: PIT Before/After Minimization

This file describes a safe, repeatable flow for mutation testing with PIT:

1. `before` on source tests (without minimized selection),
2. run minimization pipeline,
3. `after` on minimized selection via generated `*Min` wrapper tests.

The flow does not modify production code and does not change normal pipeline behavior.

## Prerequisites

- Run from project root.
- Use JDK (not JRE).
- `collect-full`, `minimize`, and PIT integration are already available in this project.

## Recommended PowerShell Variables

```powershell
$Algorithm       = "PBE"   # or "LP"
$Metric          = "METHOD" # or "LINE"

# Keep these consistent between before/after:
$TargetClasses   = "org.jsoup.helper.*,org.jsoup.select.*"
$TargetTestsBase = "org.jsoup.helper.*Test,org.jsoup.select.*Test"
$ClassRegex      = "org[.]jsoup[.](helper|select)[.].*Test"

# Optional PIT stability options:
$TimeoutFactor   = 2
$TimeoutConstant = 5000

$OutputRoot      = "target/minimizer"
$ReportsRoot     = "target/pit-reports"
$AlgoLower       = $Algorithm.ToLower()
```

## Step 0: Clean Previous Artifacts

```powershell
Remove-Item -Recurse -Force target\minimizer,target\pit-reports -ErrorAction SilentlyContinue
```

## Step 1: Compile

```powershell
mvn -DskipTests test-compile
```

## Step 2 (Before): PIT on Source Tests

This is a baseline "without minimization".

```powershell
mvn org.pitest:pitest-maven:mutationCoverage "-DtargetClasses=$TargetClasses" "-DtargetTests=$TargetTestsBase" -DreportsDirectory=$ReportsRoot/before-base -DtimestampedReports=false -DtimeoutFactor=$TimeoutFactor -DtimeoutConstant=$TimeoutConstant
```

If some test class is unstable under PIT, exclude it in both before and after:

```powershell
"-DexcludedTestClasses=org.jsoup.helper.HttpClientExecutorTest"
```

## Step 3: Collect Full Coverage for Minimization Input

```powershell
mvn --% -DskipTests test-compile org.codehaus.mojo:exec-maven-plugin:3.6.1:java -Dexec.classpathScope=test -Dexec.mainClass=minimizer.pipeline.MinimizerPipelineMain -Dexec.args=collect-full "-Dminimizer.classPattern=$ClassRegex"
```

## Step 4: Minimize

```powershell
mvn --% -DskipTests test-compile org.codehaus.mojo:exec-maven-plugin:3.6.1:java -Dexec.classpathScope=test -Dexec.mainClass=minimizer.pipeline.MinimizerPipelineMain -Dexec.args=minimize -Dminimizer.algorithm=$Algorithm -Dminimizer.metric=$Metric
```

Selected IDs file produced by minimizer:

```text
target/minimizer/<pbe|lp>/selected-test-ids.txt
```

## Step 5: Generate `*Min` Wrapper Tests for Minimized IDs

Wrappers are generated under `target/minimizer/pit/generated-src/...` and compiled to `target/test-classes`.

```powershell
mvn --% -DskipTests test-compile org.codehaus.mojo:exec-maven-plugin:3.6.1:java -Dexec.classpathScope=test -Dexec.mainClass=minimizer.pitest.PitestMinimizedWrappersMain -Dminimizer.algorithm=$Algorithm "-Dminimizer.pit.includeClassRegex=$ClassRegex"
```

Read generated PIT `targetTests` list:

```powershell
$AfterTargetTestsFile = "$OutputRoot/pit/$AlgoLower-target-tests-minwrappers.txt"
$AfterTargetTests = (Get-Content $AfterTargetTestsFile -Raw).Trim()
```

## Step 6 (After): PIT on Minimized Selection (via Wrappers)

Important: pass the same selected IDs file to PIT run, so wrappers execute the intended ID set.

```powershell
$SelectedIdsFile = "$OutputRoot/$AlgoLower/selected-test-ids.txt"

mvn org.pitest:pitest-maven:mutationCoverage "-DtargetClasses=$TargetClasses" "-DtargetTests=$AfterTargetTests" "-Dminimizer.pit.selectedIdsFile=$SelectedIdsFile" -DreportsDirectory=$ReportsRoot/after-$AlgoLower-minwrappers -DtimestampedReports=false -DtimeoutFactor=$TimeoutFactor -DtimeoutConstant=$TimeoutConstant
```

If you used `excludedTestClasses` in before, pass the same value here.

## Optional: Strictly Symmetric Before (also via Wrappers)

Use this if you want both before and after to run through the same wrapper mechanism.

Create a full successful-ID file **without BOM**:

```powershell
$ids = Import-Csv "$OutputRoot/full-run/tests.csv" | Where-Object { $_.status -eq 'SUCCESS' } | Select-Object -ExpandProperty unique_test_id
$FullIdsFile = "$OutputRoot/pit/full-success-ids.txt"
[System.IO.Directory]::CreateDirectory((Split-Path $FullIdsFile)) | Out-Null
[System.IO.File]::WriteAllLines((Resolve-Path .\).Path + "\" + $FullIdsFile, $ids, (New-Object System.Text.UTF8Encoding($false)))
```

Generate wrappers from full IDs:

```powershell
mvn --% -DskipTests test-compile org.codehaus.mojo:exec-maven-plugin:3.6.1:java -Dexec.classpathScope=test -Dexec.mainClass=minimizer.pitest.PitestMinimizedWrappersMain -Dminimizer.algorithm=$Algorithm "-Dminimizer.pit.selectedIdsFile=$FullIdsFile" "-Dminimizer.pit.outputFile=$OutputRoot/pit/full-target-tests-minwrappers.txt" "-Dminimizer.pit.includeClassRegex=$ClassRegex"
```

Run strict before:

```powershell
$BeforeWrap = (Get-Content "$OutputRoot/pit/full-target-tests-minwrappers.txt" -Raw).Trim()
mvn org.pitest:pitest-maven:mutationCoverage "-DtargetClasses=$TargetClasses" "-DtargetTests=$BeforeWrap" "-Dminimizer.pit.selectedIdsFile=$FullIdsFile" -DreportsDirectory=$ReportsRoot/before-minwrappers -DtimestampedReports=false -DtimeoutFactor=$TimeoutFactor -DtimeoutConstant=$TimeoutConstant
```

## Report Locations

- Before (base): `target/pit-reports/before-base`
- After (min wrappers): `target/pit-reports/after-<pbe|lp>-minwrappers`
- Optional strict before wrappers: `target/pit-reports/before-minwrappers`

Main files:

- `index.html` (human-readable summary)
- `mutations.xml` (machine-readable for tables/scripts)

## How to Compare Correctly

Keep these identical between before and after:

- `targetClasses`
- timeout settings
- excluded test classes (if any)
- PIT version and mutators (default unless intentionally changed)

Change only test set:

- before: source tests,
- after: minimized wrappers.

## Common Issues

- `Tests failing without mutation`:
  - baseline is not green under PIT; exclude unstable classes in both runs.
- `?[engine:junit-jupiter] is not a well-formed UniqueId segment`:
  - input ID file was written with BOM; regenerate without BOM (see strict before section).
- After run uses wrong IDs:
  - pass `-Dminimizer.pit.selectedIdsFile=...` explicitly in PIT command.
