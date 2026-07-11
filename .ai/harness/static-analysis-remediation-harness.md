---
name: static-analysis-remediation-harness
description: Step-by-step remediation and prohibited shortcuts. Use when fixing Detekt, SonarQube, ktlint, or formatting findings.
---

# Static Analysis Remediation Harness

## Steps

1. Read the finding and locate the affected source.
2. Classify: correctness, security, maintainability, style, or generated-code noise.
3. Prefer a minimal design/readability fix over suppression.
4. Ensure tests still cover behavior after refactoring.
5. Run the relevant static analysis command if available.
6. If suppressing: keep it local and justify it.

## Do not

- Reformat unrelated files.
- Change behavior while claiming a style-only fix.
- Suppress entire files for one finding.
- Add global config relaxations or lower thresholds for a local issue without a documented project-wide reason.