---
name: webapp-page-authoring
description: Workflow for adding or changing a page in racingmanager-webapp — client, standalone component (ts/html/scss), route, i18n, spec. Use when implementing a new UI screen, list, form, or detail view, or an API client for it.
---

# Skill: Webapp Page Authoring

## Load first

- `../instructions/webapp-ui-guidelines.md`

## When to use

Adding a new screen (list, form, detail), a new feature area, or the HTTP client
that feeds it. Also for reshaping an existing page so it stays consistent with the
rest of the app.

## Workflow

1. **Find the closest existing page** (`events/`, `participants/`) and mirror its
   structure. Consistency beats cleverness.
2. **Client first.** In `libs/clients/<domain>/`, add or extend
   `<domain>.client.ts` (one method per endpoint, returns `Observable`) and the
   request/response `interface`s in `<domain>.models.ts`. Inject `API_BASE_URL`;
   never hardcode the host or set session headers.
3. **Component (three files).** Create `<feature>.component.{ts,html,scss}`:
   - Standalone, selector `app-<feature>`, `templateUrl` + `styleUrl`.
   - Dependencies via `inject()`.
   - **Rendered state as signals** (`signal`, `computed`) — zoneless will not
     render plain fields set in a subscribe.
   - Template-driven forms: bind `[(ngModel)]` to fields, and after an async load
     of existing values call `inject(ChangeDetectorRef).markForCheck()`.
4. **Route.** Wire it in `app.routes.ts` under the correct shell/guard. For
   event-scoped pages nest under `:id`. Avoid `router.navigate(['..'])` across
   two-segment routes — navigate to an absolute target (`['/', 'racemanager', id]`).
5. **i18n.** Add every string to **both** `i18n/en.json` and `i18n/de.json`, keys
   alphabetized. Use `TranslatePipe` in templates, `TranslateService.instant` for
   confirm/error strings.
6. **Styling.** External SCSS with `var(--…)` tokens from `src/styles.scss`. Reuse
   existing class patterns; use `<button>` for actions, `<a>` for navigation;
   native `confirm()` for destructive confirmation.
7. **Spec.** Add `*.component.spec.ts` using `provideTestTranslate()` and
   `provideHttpClientTesting()`; flush expected requests.
8. **Verify.** `npx ng build` is clean with no new warnings; the spec is green.

## Output expectations

A new page is indistinguishable in structure and style from the existing ones:
client under `libs/clients/`, three-file standalone component, signal-backed state
that actually renders under zoneless, both translation catalogs updated, and a
passing spec. If any rendered data does not appear, the cause is almost always a
plain field that should be a signal — fix that before anything else.
