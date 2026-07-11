---
name: webapp-ui-guidelines
description: Rules for building Angular pages in racingmanager-webapp — folder layout, HTTP clients, standalone components with separate ts/html/scss, zoneless signals, routing, i18n, styling tokens. Use when adding or changing any UI page, component, or API client in the webapp.
---

# Webapp UI Guidelines

Canonical rules for `racingmanager-webapp`. Every new page must look like it was
written by the same person who wrote the existing ones. When in doubt, copy the
closest existing feature (`events/`, `participants/`) rather than inventing a new
shape.

## Stack and non-negotiables

- **Angular 22, standalone components** — no NgModules.
- **Zoneless change detection.** `zone.js` is not installed. State that a template
  renders **must** be a signal, or the view will not update. This is the single
  most common bug (data fetched, view stays empty). See "State and zoneless".
- **`inject()`**, not constructor parameters, for dependencies.
- **Separate files per component**: `*.component.ts`, `*.component.html`,
  `*.component.scss`. Never inline `template:` or `styles:`.
- **Styling via design tokens** in external SCSS. No inline `style=`, no hex
  colors — use `var(--…)` tokens from `src/styles.scss`.
- **All user-facing text via ngx-translate.** No hardcoded strings in templates.
- **No new dependencies** without a strong reason — the platform, RxJS, and
  signals cover almost everything.

## Folder layout

```
src/app/
  <feature>/                         # one folder per feature area
    <feature>-list.component.{ts,html,scss,spec.ts}
    <feature>-form.component.{ts,html,scss,spec.ts}
  libs/clients/
    core/            api.config.ts, session.interceptor.ts   # shared client plumbing
    <domain>/        <domain>.client.ts, <domain>.models.ts   # one folder per API domain
  core/              app-wide services (auth, selected-event, guards)
  i18n/              en.json, de.json, loader, locale service
  shell/             layout shells (racemanager, spectator)
  testing/           spec helpers (provideTestTranslate)
```

- A **feature folder** holds the views for one area. Put it directly under
  `src/app/`, named after the domain (`events`, `participants`, `races`).
- An **API client** lives under `libs/clients/<domain>/`. One `.client.ts` and one
  `.models.ts` per backend domain. Never call `HttpClient` directly from a
  component — always go through a client.
- **Cross-feature state** (selection, auth) goes in `core/` as a
  `providedIn: 'root'` service.

## HTTP clients

One injectable client per backend domain. Thin — one method per endpoint,
returning an `Observable`. No caching, no state, no header wrangling (the session
interceptor attaches `X-Session-Id` globally).

```ts
@Injectable({ providedIn: 'root' })
export class WidgetClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  findAll(): Observable<WidgetResponse[]> {
    return this.http.get<WidgetResponse[]>(`${this.baseUrl}/api/v1/widgets`);
  }
  create(request: CreateWidgetRequest): Observable<WidgetResponse> {
    return this.http.post<WidgetResponse>(`${this.baseUrl}/api/v1/widgets`, request);
  }
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/v1/widgets/${id}`);
  }
}
```

- Base URL: inject `API_BASE_URL` (from `libs/clients/core/api.config.ts`). Never
  hardcode `http://localhost:8080`.
- Request/response types are `interface`s in `<domain>.models.ts`. Mirror the
  backend DTO names (`WidgetResponse`, `CreateWidgetRequest`, `UpdateWidgetRequest`).
- Nullable backend fields are `field: T | null`.

## Components

Standalone, `OnPush`-by-nature (zoneless). Member conventions:

- `protected readonly` for anything the template touches (services, signals, methods).
- `private readonly` for dependencies the template does not touch.
- Selector `app-<feature>`, class `<Feature>Component`, files kebab-case.

```ts
@Component({
  selector: 'app-widget-list',
  standalone: true,
  imports: [RouterLink, DatePipe, TranslatePipe],
  templateUrl: './widget-list.component.html',
  styleUrl: './widget-list.component.scss',
})
export class WidgetListComponent {
  private readonly widgetClient = inject(WidgetClient);

  protected readonly widgets = signal<WidgetResponse[]>([]);
  protected readonly error = signal('');

  constructor() {
    this.load();
  }

  private load(): void {
    this.widgetClient.findAll().subscribe({
      next: (widgets) => this.widgets.set(widgets),
      error: () => this.error.set('Failed to load widgets.'),
    });
  }
}
```

Import only the directives/pipes actually used (`RouterLink`, `DatePipe`,
`TranslatePipe`, `FormsModule`). Unused imports are a build warning.

## State and zoneless — read this before rendering fetched data

Because there is no `zone.js`, assigning to a **plain field** inside an async
callback does **not** trigger change detection. The template will not update.

- **Rendered state → signals.** Read it in the template as `widgets()`.

  ```ts
  protected readonly widgets = signal<WidgetResponse[]>([]);
  // in subscribe:
  this.widgets.set(data);
  ```

- **Template-driven forms (`[(ngModel)]`)** bind to plain fields. When you load
  existing values into them asynchronously (edit mode), the inputs will not
  refresh on their own. Inject `ChangeDetectorRef` and call `markForCheck()` after
  populating:

  ```ts
  private readonly cdr = inject(ChangeDetectorRef);
  // in the load subscribe, after setting fields:
  this.cdr.markForCheck();
  ```

  User typing already triggers CD, so only the async load needs the nudge.

- Prefer signals for everything else (loading flags, errors, derived values via
  `computed`). Use `effect()` for side effects that follow a signal.

## Routing

- All routes live in `app.routes.ts`, nested under the appropriate shell
  (`racemanager` for the operator app), behind `authGuard`.
- Add child routes inside the `:id` event scope when the page is event-specific
  (participants, races, qualification).
- **Relative-navigation trap:** a route whose `path` spans two URL segments (e.g.
  `participants/:participantId`) consumes two segments, so `router.navigate(['..'])`
  goes up only one and lands on a non-existent path. Navigate to a known absolute
  target instead:

  ```ts
  this.router.navigate(['/', 'racemanager', eventId]); // back to the list
  ```

- A `<button>` may carry `[routerLink]` — prefer a button over an anchor for
  actions ("Delete", "Go to race control"); reserve `<a>` for pure navigation
  between pages.

## Internationalization

- Two catalogs: `i18n/en.json` and `i18n/de.json`, nested JSON, **keys sorted
  alphabetically** within each object. Add every new key to **both** files.
- Template text: `{{ 'feature.section.key' | translate }}`. Import `TranslatePipe`.
- Parameters: `{{ 'x.y' | translate: { name: value } }}` with `"{{name}}"` in JSON.
- Imperative text (confirm dialogs, error strings shown via signal):
  `inject(TranslateService).instant('feature.key', { name })`.
- No English fallback strings hardcoded in the component or template.

## Styling

- External `*.component.scss` referenced by `styleUrl`. One component, one SCSS
  file. No inline styles, no global leakage (component styles are scoped).
- Use tokens from `src/styles.scss`: colors (`--color-*`), spacing (`--space-*`),
  radius (`--radius-*`), control sizes. Never a raw color or pixel value where a
  token exists.
- Reuse existing class patterns (`.header`, `.actions`, `.chip`, `.empty`,
  `.panel`) so pages share a visual language. Copy from a sibling component first.
- Confirmation before destructive actions: native `confirm()` with a translated
  message is the accepted pattern — no custom modal unless asked.

## Auth and session

- The session id is attached to every request by `session.interceptor.ts`. Clients
  and components never set `X-Session-Id` themselves.
- Session state lives in `core/auth.service.ts` (`session`, `isAuthenticated`
  signals). Guard protected routes with `authGuard`.

## Testing

- Every component gets a `*.component.spec.ts`. Follow the sibling specs.
- Provide translations with `provideTestTranslate()` from `testing/`.
- Provide HTTP with `provideHttpClient()` + `provideHttpClientTesting()`, and flush
  every expected request (`httpTesting.expectOne(url).flush(...)`) so `verify()`
  passes.
- Assert observable behavior (rendered rows, empty state), not implementation.

## Definition of done

1. Client method(s) added under `libs/clients/<domain>/`, typed models updated.
2. Component with three files; rendered state in signals; forms nudge CD on async load.
3. Route wired in `app.routes.ts`; navigation uses safe absolute targets.
4. Every string translated in **both** `en.json` and `de.json`, keys alphabetized.
5. Styling in external SCSS using design tokens; buttons for actions.
6. Spec added and green; `npx ng build` clean with no new warnings.
