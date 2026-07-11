---
name: rest-api-cucumber-testing
description: Workflow for creating or modifying REST API Cucumber tests — scenario matrix, library reuse, assertions.
---

# Skill: REST API Cucumber Testing

## Load first

- `../instructions/api-guidelines.md`
- `../instructions/rest-api-cucumber.md`
- `../harness/rest-api-test-harness.md`
- `../harness/no-cheating-test-harness.md`

## Workflow

1. Read the OpenAPI operation or controller endpoint.
2. Build the scenario matrix from the harness.
3. Write feature scenarios using the project's BDD Cucumber Gherkin library steps where available; add only missing step definitions, reusing library primitives first.
4. Assert status, body, errors, paging, sorting, and security behavior.
5. Verify scenarios fail for meaningful regressions.

## Output expectations

Feature files are business-readable and exercise HTTP boundary behavior; controller-only unit tests are no substitute for REST scenarios.
