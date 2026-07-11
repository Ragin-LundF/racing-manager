---
name: rest-api-cucumber
description: Cucumber/Gherkin rules for REST API tests — scenario coverage, feature style, anti-cheating. Use when adding or changing REST API tests.
---

# REST API Cucumber / Gherkin Testing

## Policy

- Test REST APIs through Cucumber/Gherkin using the BDD Cucumber Gherkin library when available in the project.
- Feature files use two-space indentation per `.editorconfig`.
- Scenarios describe business behavior at the HTTP/API boundary.
- Reuse the library's request/response building and assertion primitives; do not build a second custom REST testing mini-framework.

## Scenario coverage

Per endpoint or changed behavior, cover:

- Successful request: expected status code and response body.
- Missing/invalid authentication on secured endpoints.
- Denied authorization scope where applicable.
- Validation errors → `400`; domain unprocessable cases → `422`; missing path resources → `404`.
- Paging (`page`, `size`) and sorting (`sorting`) for list/search endpoints.
- Sensitive search criteria via `POST .../search` body.
- Error object shape (`code`, `message`, `date`, `requestId`, `endpoint`) when error responses change.

## Feature style

- Clear `Feature`, `Background`, `Scenario`, `Scenario Outline` blocks; one behavior per scenario.
- Tables for request bodies, headers, or expected fields when they improve readability.
- Semantic step names over implementation-oriented ones.
- No brittle assertions on generated timestamps unless the value is controlled.

## Anti-cheating

- No direct controller calls in REST behavior tests.
- No bypassing authentication filters for secured endpoints unless the scenario explicitly tests post-authentication behavior.
- Never mock the endpoint under test.
- No status-code-only assertions when the body contract matters.
- No hard-coded happy-path fixtures that make validation/security branches unreachable.

## Example skeleton

```gherkin
Feature: Report search API

  Background:
    Given the API base path is "/api/v1"
    And an authenticated client has scope "com.example.reportservice.read"

  Scenario: Search reports with sensitive criteria
    When the client sends a POST request to "/reports/search" with JSON body:
      """
      { "caseId": "4e760145-2e65-4242-ac33-488943528c93" }
      """
    Then the response status is 200
    And the response body contains a paged result
```
