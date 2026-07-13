---
name: kotlin-code-generation
description: Workflow for writing or changing Kotlin production code — load order, layer ownership, test-first steps.
---

# Skill: Kotlin Code Generation

## Load first

- `../instructions/coding-guidelines.md`
- `../instructions/testing.md`

## Workflow

1. Identify the owning layer.
2. Add or update unit tests first when behavior is clear.
3. Implement with small block-body functions and named arguments for Kotlin calls.
4. Keep domain logic in domain services; keep REST/event layers free of business logic.
5. Run or request tests and Detekt.

## Output expectations

Production code is readable, focused, and immutable by default; tests use `kotlin.test` and document public behavior.
