---
name: api-guidelines
description: REST API and OpenAPI design rules — paths, methods, status codes, paging, sorting, error object, compatibility. Use when designing or changing REST APIs or openapi-*.yaml files.
---

# REST API and OpenAPI Guidelines

## Contract

- Define APIs with OpenAPI 3 in YAML, files named `openapi-<short-name>.yaml`.
- The API is a contract between backend and clients.
- Endpoints represent business resources, not RPC-style actions hidden in parameters.

## Versioning and compatibility

- Paths include only the major version: `/api/v1`. A versioned path stays backward compatible.
- Breaking (not allowed within a version): changing URIs; removing services, parameters, or response fields; making optional request fields mandatory; adding response enum values clients must handle exhaustively; breaking changes to list/page result sets.
- Compatible (allowed): new services, new resource fields, new optional parameters, documentation fixes.

## Paths

- Start with `/api/v<major>`; camelCase for multi-word elements: `/api/v1/racing`.
- Resources, not methods — no action prefixes like `/create/myData`; the HTTP method expresses the action.
- Plural nouns for CRUD collections; singular only for single-instance resources.
- No personal, protected, or sensitive data in path or query parameters.

## HTTP methods

- `GET`: read only, no state change, no body.
- `POST`: create, or submit sensitive criteria in a body (e.g. searches).
- `PUT`: replace an existing complete resource.
- `DELETE`: delete an existing resource.
- `PATCH`: do not use (not supported by all frameworks).

## Status codes

- `200`: successful GET/PUT/POST without resource creation.
- `201`: successful creation.
- `400`: invalid request shape, missing required fields, validation mismatch.
- `401`: authentication missing or invalid.
- `403`: authenticated but no access.
- `404`: endpoint or path resource not found.
- `422`: resource exists but cannot be processed as requested.
- `423`: resource locked.
- `451`: blocked for legal reasons.
- `500`: unexpected server-side failure.

## Searches

- `POST` with request body, `200` response, path `<resource>/search` (e.g. `/api/v1/racing/search`).
- Paging and sorting stay query parameters even for POST searches.

## Headers

- `Content-Type: application/json` for JSON bodies; `Authorization: Bearer <token>` for authentication.

## Objects and validation

- List required fields in the schema `required` array.
- UTF-8 bodies unless specified otherwise.
- RFC 3339: dates `yyyy-mm-dd` (`type: string, format: date`), date-times `yyyy-mm-ddThh:mm:ss.SSSZ` (`format: date-time`).
- Declare validation constraints in OpenAPI so generated validation is reused.
- No validation patterns on enums; their values already constrain them.

## Error object

Consistent shape:

```json
{
  "code": "INVALID_PARAMETER",
  "message": "Invalid input parameter.",
  "date": "2020-01-01 00:00:00.000",
  "requestId": "4e760145-2e65-4242-ac33-488943528c93",
  "endpoint": "https://api.example.com"
}
```

- `code`: uppercase technical code (`INVALID_PARAMETER`, `ENTITY_NOT_FOUND`, `UNEXPECTED_ERROR`).
- `message`: user-meaningful explanation; one code may have multiple messages.
- `date`, `requestId`, `endpoint`: log/trace correlation and support diagnostics.

## Paging

- List endpoints with potentially large results must support paging.
- Query parameters `page` (zero-based, default `0`) and `size` (default documented per endpoint).
- Paged responses include `number`, `size`, `totalElements`, `totalPages`; define a reusable `Paging` schema combined via `allOf`.

## Sorting

- Query parameter is always `sorting`: comma-separated fields with `:asc`/`:desc`, e.g. `sorting=username:asc,creationDate:desc`.
- Stays a query parameter for POST methods.

## OpenAPI formatting

- One sentence per line; longer descriptions use block style `description: |`.
- HTML line breaks (`<p>`, `<br>`) on their own line.
- Notes as Markdown quotes starting with `Note:`, e.g. `> Note: *This is important.*`.
- Enum descriptions use HTML lists; enum values, technical formats, and code elements wrapped in `<code>`.
