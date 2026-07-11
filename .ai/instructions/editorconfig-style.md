---
name: editorconfig-style
description: Formatting defaults and disabled ktlint rules from the bundled .editorconfig. Use for formatting questions and when adopting the package in a repository.
---

# EditorConfig Style

The bundled `.editorconfig` is the formatting source of truth unless the host repository has stricter settings.

## Defaults

UTF-8, LF line endings, final newline required, 4-space indentation (tab width 4), continuation indent 8, max line length 120.

## Kotlin / ktlint notes

Disabled ktlint rules (to avoid formatter issues with generated code): `no-wildcard-imports`, `max-line-length`, `enum-entry-name-case`. Do not use these as a reason for unreadable handwritten code — keep imports, line lengths, and enum names clean.

## Adoption

When adding this package to an existing repository, compare with the existing `.editorconfig`, preserve stricter team-specific settings unless they break generated-code behavior, and never silently replace team settings without review.