---
name: test-generation-harness
description: Steps, required assertions, and test-data rules. Use when generating or updating tests.
---

# Test Generation Harness

## Steps

1. Identify the unit under test and its public behavior.
2. List inputs, outputs, side effects, and error modes.
3. Write at least one positive-path test.
4. Write negative tests for invalid inputs and rejected states.
5. Write edge/boundary tests: parameter limits, empty values, min/max, valid nullability.
6. Use `kotlin.test` assertions with named arguments.
7. Ensure each test fails for a realistic regression.
8. Remove duplicate or meaningless assertions.

## Required assertions

- Exact values when the contract defines them; collection sizes and relevant contents.
- Important fields of mapped DTOs and domain models.
- Exact status codes and error codes for REST tests.
- Positive and negative authorization/security paths where applicable.

## Test data

- Minimal fixtures; builders only when repeated setup hides irrelevant noise.
- No random data unless the seed is fixed or randomness is under test.
- No single giant fixture reused for unrelated behavior.

## Quality gate

A suite that only raises line coverage without checking behavior is unacceptable.