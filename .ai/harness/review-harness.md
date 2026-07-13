---
name: review-harness
description: Review order, findings format, and approval criteria. Use for code review, self-review, or PR preparation.
---

# Review Harness

## Review order

1. Contract: API, events, database, configuration, public functions.
2. Architecture: layer ownership and dependency direction.
3. Behavior: correctness of domain and application logic.
4. Security: authentication, authorization, data leakage, logging.
5. Tests: coverage, meaningful assertions, no cheating.
6. Static analysis: Detekt, SonarQube, formatting.
7. Maintainability: names, function size, duplication, comments.

## Findings format

Per finding: severity (blocker/major/minor/nit), location, issue, why it matters, concrete fix.

## Approval criteria

Approve only when behavior matches the requested change, relevant meaningful tests exist, REST changes have Ktor `testApplication` coverage, static analysis and formatting are addressed, and no security or compatibility regression is visible.