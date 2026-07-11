---
name: openapi-authoring
description: Workflow for creating or updating openapi-*.yaml — schemas, security, paging/sorting, compatibility guard.
---

# Skill: OpenAPI Authoring

## Load first

- `../instructions/api-guidelines.md`
- `../instructions/security.md`

## Workflow

1. Confirm resource-oriented path, HTTP method, and version-path compatibility.
2. Define security and scopes.
3. Define request/response schemas with required and nullable fields, validation constraints, and standard error responses.
4. Add paging/sorting query parameters where relevant.
5. Format descriptions with block style and `<code>` tags where appropriate.

## Compatibility guard

Before changing an existing API, verify backward compatibility. If broken, propose a new major path or a compatibility layer.