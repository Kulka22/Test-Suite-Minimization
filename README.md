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
