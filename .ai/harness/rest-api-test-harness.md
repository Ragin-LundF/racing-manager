---
name: rest-api-test-harness
description: Endpoint inventory, scenario matrix, and required assertions. Use for changed REST endpoints.
---

# REST API Test Harness

## Endpoint inventory

Per changed endpoint identify: method and path, required scopes, request headers, path/query parameters, request/response body schemas, success status code, expected error status codes, paging/sorting behavior.

## Scenario matrix

| Case | Required for |
|---|---|
| Valid authenticated request | Always |
| Missing bearer token | Secured endpoints |
| Missing required scope | Scoped endpoints |
| Invalid request body | Body endpoints |
| Invalid path/query parameter | Parameterized endpoints |
| Resource not found | Dynamic path resources |
| Domain cannot process request | Domain rules / external state |
| Paging defaults and custom page/size | List/search endpoints |
| Sorting | Sortable endpoints |
| Error object shape | Error contract changes |

## Assertions

- Status code; relevant response body fields; headers if part of the contract.
- Error `code` and meaningful `message` for error cases.
- Paging metadata for paged responses; sort order where sorting is supported.

## Prohibited shortcuts

- No direct controller calls for REST behavior scenarios.
- No bypassing filters except in tests explicitly below the HTTP boundary.
- No status-code-only tests for endpoints with response contracts.
