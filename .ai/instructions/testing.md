---
name: testing
description: Testing rules — coverage expectations, kotlin.test usage, anti-cheating, completion criteria. Use when adding, modifying, or reviewing tests, or when behavior changes.
---

# Testing Instructions

## Principles

- High coverage is mandatory for new and changed code; tests verify behavior, not line execution.
- Every behavior change requires new or updated tests.
- Never weaken, delete, skip, or rewrite tests just to make a change pass.
- Never change production code solely to satisfy a brittle test unless the behavior change is required.
- Test public behavior, not implementation details.
- Prefer small, focused tests with behavior-focused names.

## Unit tests

- Use `kotlin.test` annotations and assertions when possible.
- Use named arguments in assertions: `assertEquals(expected = expectedValue, actual = actualValue)`.
- Cover positive paths, negative paths, edge cases, boundary values, valid nullability, and error paths.
- Assert exact exception types; assert messages only when they are part of the contract.
- Use fixtures/builders to reduce noise, but keep scenario-relevant inputs visible.

## REST API tests

- Test REST APIs through Ktor's `testApplication { }` host, driving the routes with the built-in `client` over the real HTTP boundary (see `harness/rest-api-test-harness.md`).
- Configure the application under test with the same plugins/routing the server uses (`configureSerialization`, `configureStatusPages`, `configureRouting`, …); assert `response.status` and the response body.
- Direct service/route-handler calls do not replace HTTP-level REST tests.

## Coverage expectations

- Near-complete branch coverage for new domain logic.
- Explicit positive and negative tests for critical business logic, validation, security checks, and error mapping.
- Generated code needs no direct tests unless custom behavior is added around it.
- Test mapping code that contains conditions, transformations, defaults, or compatibility behavior.
- No meaningless coverage-chasing tests.

## Anti-cheating rules

Never:

- Assert only non-null when exact behavior is known.
- Mock the unit under test instead of its dependencies.
- Test a copy of the implementation logic.
- Use `Thread.sleep` to make timing pass.
- Add `@Disabled`, ignores, assumptions, or conditional returns to avoid failures.
- Delete negative tests for validation/security/error cases.
- Replace integration tests with mocked unit tests without equivalent coverage.
- Reduce assertions after implementation changes.

## Test naming

Name the behavior, not the method: `returns report when request is valid`, `rejects missing bearer token`, `maps expired consent to unprocessable entity`.

## Completion criteria

A change is complete only when:

1. Unit tests cover changed function behavior and parameters.
2. REST behavior is covered by Ktor `testApplication` tests when relevant.
3. Negative, edge, and error paths are covered.
4. Tests fail without the production change or with a realistic regression.
5. Static analysis and formatting checks pass.