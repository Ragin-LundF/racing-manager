---
name: code-change-harness
description: Intake, planning, and implementation checklist. Use before and during any non-trivial code change.
---

# Code Change Harness

## Intake

1. Restate the requested behavior change in one sentence.
2. Identify touched modules; load the matching instruction files from `.ai/README.md`.
3. Identify affected public contracts: REST API, DTOs, events, database schema, configuration, libraries.
4. Identify required tests before implementing.

## Planning checklist

- What behavior exists today, and what should change?
- Which layer owns the change? Does OpenAPI change first? Does generated code need regeneration?
- Which unit tests must be added/updated? Which static analysis findings are likely?

## Implementation rules

- Make the smallest coherent change; no unrelated refactoring.
- Business logic in domain services; REST/event layers only map and delegate; application services orchestrate, they hold no business rules.
- Add tests close to the changed behavior.
- Run formatting, static analysis, and test checks where possible.

## Completion report

Include: changed behavior, tests added/updated, verification commands run, commands not run and why, remaining risk or follow-up.