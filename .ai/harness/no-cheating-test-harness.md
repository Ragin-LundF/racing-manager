---
name: no-cheating-test-harness
description: Red flags and review questions for dishonest or low-value tests. Use when reviewing or generating tests.
---

# No-Cheating Test Harness

## Red flags — reject or rewrite tests that

- Mock the unit under test.
- Assert only non-null for known outputs.
- Assert implementation details instead of behavior.
- Copy production logic into the expected-value calculation.
- Depend on execution order without a contract.
- Hide failures via `@Disabled`/ignores, assumptions, early returns, environment checks, or swallowed exceptions.
- Remove meaningful assertions or change expected values without explaining the behavior change.

## Review questions per test

1. What production regression would it catch?
2. Would it fail on a plausible wrong value?
3. Does it verify the contract or only the implementation shape?
4. Are negative and edge cases covered?
5. Is it deterministic?

## Mandatory action

A failing test is not kept for coverage: rewrite it, or explain why it is intentionally shallow (e.g. generated-code smoke test).