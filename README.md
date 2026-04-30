# Test-Suite-Minimization

# Authors and contributors

The main contributors Vyacheslav Kuzmin and Alexey Erofeev, students of SPbPU ICSC.
The advisor and contributor Vladimir A. Parkhomenko., Seniour Lecturer of SPbPU ICSC.

# Introduction

This repository contains an experimental implementation of test suite mininmization algorithms on Java projects (JUnit and Maven).
The system builds coverage matrices (JaCoCo), applies different minimization algorithms (PBE, LP, and heuristic approaches), and evaluates the results using mutation testing (Pitest).

# Instruction

## STEP 0. Clean previous results
```powershell
Remove-Item -Recurse -Force target\minimizer,target\pit-reports -ErrorAction SilentlyContinue
```

## STEP 1. Compilation
```powershell
mvn -DskipTests test-compile
```

## STEP 2. Common parameters
These parameters must be identical for both BEFORE and AFTER runs to ensure correct comparison.
```powershell
$targetClasses = "<TARGET_CLASSES_PATTERN>"
$beforeTests = "<TEST_CLASSES_PATTERN>"
$algorithm = "<ALGORITHM>"

# Set according to the number of CPU cores/threads
$threads = <THREAD_COUNT>

# Must be identical for BEFORE and AFTER
$timeoutFactor = <TIMEOUT_FACTOR>
$timeoutConstant = <TIMEOUT_CONSTANT>

$algorithmDir = switch ($algorithm.ToUpper()) {
  "PBE"               { "pbe" }
  "LP"                { "lp" }
  "NAIVE"             { "naive" }
  "GREEDY_ESSENTIAL"  { "greedy-essential" }
  "GENETIC"           { "genetic" }
  default             { throw "Unsupported algorithm: $algorithm" }
}

$includeClassRegex = '^(' + (($beforeTests -split ',' | ForEach-Object {
  $_.Trim() -replace '\.', '[.]' -replace '\*', '.*'
}) -join '|') + ')$'
```

## STEP 3. BEFORE - original test suite (before minimization)
```powershell
mvn org.pitest:pitest-maven:mutationCoverage `
  "-DtargetClasses=$targetClasses" `
  "-DtargetTests=$beforeTests" `
  -DreportsDirectory=target/pit-reports/before `
  -DtimestampedReports=false `
  "-Dthreads=$threads" `
  "-DtimeoutFactor=$timeoutFactor" `
  "-DtimeoutConstant=$timeoutConstant"
```

## STEP 4. Collect full coverage data
```powershell
mvn -DskipTests test-compile org.codehaus.mojo:exec-maven-plugin:3.6.1:java `
  "-Dexec.classpathScope=test" `
  "-Dexec.mainClass=minimizer.pipeline.MinimizerPipelineMain" `
  "-Dexec.args=collect-full" `
  "-Dminimizer.classPattern=$includeClassRegex"
```

## STEP 5. Run minimization
```powershell
mvn -DskipTests test-compile org.codehaus.mojo:exec-maven-plugin:3.6.1:java `
  "-Dexec.classpathScope=test" `
  "-Dexec.mainClass=minimizer.pipeline.MinimizerPipelineMain" `
  "-Dexec.args=minimize" `
  "-Dminimizer.algorithm=$algorithm" `
  "-Dminimizer.metric=METHOD"
```

## STEP 6. Generate minimized test wrappers
```powershell
mvn -DskipTests test-compile org.codehaus.mojo:exec-maven-plugin:3.6.1:java `
  "-Dexec.classpathScope=test" `
  "-Dexec.mainClass=minimizer.pitest.PitestMinimizedWrappersMain" `
  "-Dminimizer.algorithm=$algorithm" `
  "-Dminimizer.pit.includeClassRegex=$includeClassRegex"
```

## STEP 7. Store wrapper test list
```powershell
$afterTests = (Get-Content "target/minimizer/pit/$algorithmDir-target-tests-minwrappers.txt" -Raw).Trim()
```

## STEP 8. AFTER - mutation testing on minimized suite
```powershell
$selectedIdsFile = "target/minimizer/$algorithmDir/selected-test-ids.txt"

mvn org.pitest:pitest-maven:mutationCoverage `
  "-DtargetClasses=$targetClasses" `
  "-DtargetTests=$afterTests" `
  "-Dminimizer.pit.selectedIdsFile=$selectedIdsFile" `
  "-Dminimizer.algorithm=$algorithm" `
  "-DjvmArgs=-Dminimizer.pit.selectedIdsFile=$selectedIdsFile,-Dminimizer.algorithm=$algorithm" `
  -DreportsDirectory=target/pit-reports/after `
  -DtimestampedReports=false `
  "-Dthreads=$threads" `
  "-DtimeoutFactor=$timeoutFactor" `
  "-DtimeoutConstant=$timeoutConstant"
```

## Supported algorithms
- PBE
- ILP
- Naive
- Greedy Essential
- Genetic

## Output
### Minimization results
```
target/minimizer/<algorithm_name>/
```
### Mutation testing reports
```
target/pit-reports/before/
target/pit-reports/after/
```

## Examples
Jsoup and jHeaps were used as examples of projects where algorithms are tested.
### Jsoup original
```
https://github.com/jhy/jsoup
```

### jHeaps original
```
https://github.com/d-michail/jheaps
```
