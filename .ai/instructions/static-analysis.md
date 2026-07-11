---
name: static-analysis
description: Static analysis rules — Detekt baseline overrides, SonarQube expectations, suppression and cleanup policy. Use for Detekt, SonarQube, ktlint, .editorconfig, and lint cleanup.
---

# Static Analysis and Formatting

## Tools

- Detekt for Kotlin static analysis; SonarQube standard rules where the host project configures them.
- The repository `.editorconfig` governs formatting.
- The bundled `config/detekt/detekt.yml` contains project-specific overrides; merge with the host project config when adopting.

## Detekt baseline overrides

- `ReturnCount` active, guard clauses excluded, max `5`.
- `MagicNumber` ignores annotations.
- `ForbiddenComment` disabled.
- `NamedArguments` active, threshold `15`, matching names not ignored.
- `LongParameterList` ignores defaults; `25` function / `15` constructor parameters.
- `TooManyFunctions` allows `20` per class/interface/object.
- `SpreadOperator` disabled (compiler optimizations reduce the concern).

These relaxed thresholds reduce noise; they are not permission to write unfocused code.

## SonarQube

- Introduce no code smells, duplicated logic, security hotspots, or reliability issues.
- Refactor duplication into an abstraction only when it improves clarity.

## Suppression policy

Suppression is a last resort. Before suppressing: understand the finding, try a small design/readability fix, confirm behavior stays tested, then use the narrowest possible suppression and document why. Never suppress to hide generated bad code, missing tests, security weaknesses, or rushed implementation.

## Cleanup policy

- Preserve behavior before refactoring style; separate mechanical formatting from behavioral changes.
- Do not reformat unrelated files unless the task is explicitly formatting-only.
- Run the host repository's checks before declaring completion, typically `./gradlew detekt` or `./gradlew check`. If a command cannot be run, state which and why.