---
name: security
description: API and data security rules — endpoint security, scopes, sensitive data handling. Use for authentication, authorization, scopes, sensitive data, and API exposure decisions.
---

# API and Data Security

## Endpoint security

- Every API endpoint has security unless there is a deliberate, documented exception (typically login and health endpoints).
- Internal-only APIs still require security; infrastructure compromise and internal misuse are realistic threats.
- Define OAuth2/bearer security in the OpenAPI file: operation-level `security` blocks, or global security with local overrides. Each endpoint has an explicit scope strategy.

## Data security

- No guessable internal database IDs in paths or externally visible identifiers.
- No personal or protected data (account numbers, IBANs, names) in paths; avoid sensitive query parameters — proxies and firewalls log URLs. Use `POST` with a body for sensitive search criteria.
- Ensure logs and error responses leak no secrets, tokens, credentials, PII, or internal infrastructure details.