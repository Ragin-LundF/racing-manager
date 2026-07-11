---
name: static-analysis-cleanup
description: Workflow for fixing Detekt, SonarQube, ktlint, or formatting findings — priorities, suppressions, verification.
---

# Skill: Static Analysis Cleanup

## Load first

- `../instructions/static-analysis.md`
- `../instructions/editorconfig-style.md`
- `../harness/static-analysis-remediation-harness.md`

## Workflow

1. Fix correctness and security findings before style findings.
2. Prefer code improvements over suppressions; keep suppressions narrow and justified.
3. Preserve behavior and tests; never change global thresholds for local issues.
4. Run the relevant static analysis command when possible.