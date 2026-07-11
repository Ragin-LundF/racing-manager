---
name: coding-guidelines
description: Kotlin coding rules — efficiency-first ladder, style, named arguments, design, error handling. Use when creating, editing, reviewing, or testing Kotlin code.
---

# Kotlin Coding Guidelines

A more specific repository instruction overrides any rule here.

## Efficiency first

Write the least code that correctly solves the problem. Before writing code, stop at the first step that holds:

1. Does this need to exist at all? Speculative need: skip it and say so. (YAGNI)
2. Does the codebase already have a helper, util, type, or pattern for it? Reuse it.
3. Does the standard library do it? Use it.
4. Does a platform/framework feature cover it? Use it.
5. Does an already-installed dependency solve it? Use it. Never add a dependency for what a few lines can do.
6. Can it be one expression or one line while staying readable? Do that.
7. Only then write the minimum code that works.

Run this ladder after understanding the problem, never instead of it: read the task and every file the change touches, trace the real flow end to end, then decide.

- Bug fix = root cause, not symptom. Check all callers of the touched function; one fix in the shared function beats a guard per caller.
- Deletion over addition. Boring over clever. Fewest files, shortest working diff — in the right place.
- No abstractions, dependencies, boilerplate, or scaffolding that nobody asked for.
- When two equally short options exist, pick the one that is correct on edge cases.
- Mark deliberate simplifications with a `// ponytail:` comment naming the ceiling and the upgrade path.
- Never simplify away: problem comprehension, input validation at trust boundaries, error handling that prevents data loss, security, or anything explicitly requested.

## Kotlin style

- New services and modules are written in Kotlin.
- Use null-safety deliberately: nullable types only when `null` is a valid domain state.
- Prefer `val` over `var`; prefer immutable data structures and data classes.
- Use primary constructors; no boilerplate mapping constructors; no Lombok.
- Always use named arguments when calling Kotlin functions, constructors, assertions, and builders. Exception: Java APIs and other call sites where Kotlin named arguments are unavailable.
- Functions use block bodies, not expression bodies.
- Keep functions small with one responsibility; split when too long, too nested, or mixed-purpose.
- Prefer early returns and guard clauses over deep nesting.
- Prefer `runCatching` over `try/catch` when behavior stays correct and readable; use `try/catch` for `finally`, resource cleanup, cancellation propagation, or explicit exception flow.
- One top-level declaration (class, interface, enum, object) per file.
- Use descriptive names for variables, classes, and functions.
- Comment only complex logic, non-obvious decisions, trade-offs, or domain rules — never restate the code.
- Follow the official Kotlin style guide where no rule is defined here; follow `.editorconfig` exactly.

## Design

- Single responsibility for classes, functions, and modules.
- Public APIs are explicit, predictable, and easy to test.
- Preserve existing behavior unless the task explicitly requests a change.
- Prefer typed identifiers, value classes, enums, and sealed types over raw strings for domain values.
- Keep generated code separate from handwritten code; never hand-edit generated sources unless the repository explicitly requires it.

## Error handling

- Domain-specific exceptions for unrecoverable structural failures.
- Validation result objects/diagnostics for semantic errors that should be reported without aborting early.
- Never swallow exceptions silently.
- No broad `catch (Exception)` unless there is a precise boundary reason and the behavior is tested.

## Parameters and APIs

- Parameter names describe intent, not implementation.
- Avoid boolean parameters that obscure behavior; prefer enums or separate functions.
- Long parameter lists only for generated code, DTO construction, or stable API boundaries; handwritten domain logic uses a parameter object.

## Forbidden shortcuts

- No disabling/suppressing static analysis without a narrow, documented justification.
- No visibility reduction just to ease testing if production design suffers.
- No unused production hooks added only for tests.
- No production behavior changes to satisfy a brittle test unless the change is requested.
- Non-trivial new logic ships with at least one test that fails if the logic breaks; trivial one-liners need none.
