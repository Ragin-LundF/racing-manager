---
name: module-architecture
description: Layered module structure (rest-api, dto, domain-model, domain-service). Use for module boundaries, package layout, adapters, REST/event input layers, and cross-module changes.
---

# Module Architecture

Keep a consistent layered architecture: each technical domain is its own Gradle/Maven subproject with clear dependencies, so developers can switch modules without relearning structure.

## Standard layers

| Module | Responsibility |
|---|---|
| `<module>-rest-api` | REST interfaces/controllers, generated from `openapi-<name>.yaml`. Delegates to application services. No business logic. |
| `<module>-dto` | Data transfer objects for REST/application boundaries, generated from `openapi-<name>.yaml`. |
| `<module>-domain-model` | Domain entities and value objects, optimized for domain and persistence needs, not REST output. |
| `<module>-domain-service` | Core business logic, domain behavior, repositories, persistence. The module core. |