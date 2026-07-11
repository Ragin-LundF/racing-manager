# AI Instruction Index

Reusable, model-agnostic AI instructions for Kotlin/JVM projects with API-first, layered module architecture.
Progressive loading: read only the files needed for the current task, never everything.

## Always read first

- `instructions/coding-guidelines.md` — creating, editing, reviewing, or testing Kotlin code.
- `instructions/testing.md` — tests are created, modified, reviewed, or behavior changes.

## Load by task scope

| Task | Read |
|---|---|
| Module boundaries, package layout, DTO/domain boundaries, adapters, Gradle/Maven structure | `instructions/module-architecture.md` |
| REST API design, `openapi-*.yaml`, status codes, paging, sorting, errors, scopes, compatibility | `instructions/api-guidelines.md`, `instructions/security.md`, `skills/openapi-authoring.skill.md` |
| Unit tests and coverage | `instructions/testing.md`, `harness/test-generation-harness.md`, `harness/no-cheating-test-harness.md` |
| REST API tests (Cucumber/Gherkin) | `instructions/rest-api-cucumber.md`, `harness/rest-api-test-harness.md`, `skills/rest-api-cucumber-testing.skill.md` |
| Static analysis, Detekt, SonarQube, ktlint, formatting | `instructions/static-analysis.md`, `instructions/editorconfig-style.md`, `harness/static-analysis-remediation-harness.md` |
| Code review or refactoring | `harness/code-change-harness.md`, `harness/review-harness.md` |

## Conflict resolution

1. Explicit user instructions in the current task win.
2. Repository-local instructions beat this reusable package.
3. The most specific instruction file beats a general one.
4. Module-specific rules beat repository-wide rules.
5. Preserve behavior before refactoring style.
6. Never weaken tests, security, static analysis, or compatibility to ease implementation.
7. Explain unresolved conflicts before changing architecture, behavior, or public contracts.