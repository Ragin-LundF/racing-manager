# AI Instruction Index

Canonical entry point for this repository. `AGENTS.md` and `CLAUDE.md` both
delegate here. Progressive loading: read only the files needed for the current
task, never everything.

## Project overview

Racing manager for small race events: create events, manage
participants, run qualification rounds and races, and show a spectator view.
A Gradle multi-module repo with a Kotlin backend and an Angular web UI.

### Modules

| Module | Purpose | Stack |
|---|---|---|
| `racingmanager-backend` | REST + WebSocket API, persistence, domain logic | Kotlin / Ktor |
| `racingmanager-webapp` | Operator and spectator web UI | Angular (TypeScript) |

The Gradle root (`settings.gradle.kts`) includes both modules. The webapp is
wired into Gradle via `@angular/build`, but day-to-day UI work uses npm directly.

### Backend — `racingmanager-backend`

- **Kotlin 2.4**, JVM toolchain **25**.
- **Ktor 3.5** server (Netty engine) — routing, content negotiation, status
  pages, call logging, WebSockets, CORS, compression.
- **kotlinx.serialization** (JSON) for wire models; **kotlinx.coroutines**.
- **Exposed 1.3** (JDBC DSL) over **SQLite** (`org.xerial:sqlite-jdbc`) with a
  **HikariCP** pool; SQLite runs in WAL mode. **Liquibase** manages the schema.
- **BCrypt** (`at.favre.lib:bcrypt`) for password hashing; sessions are stored
  server-side and carried via the `X-Session-Id` header.
- **Logging:** kotlin-logging over Log4j2.
- **Tests:** JUnit 5 platform + `kotlin.test`, Ktor `server-test-host`,
  `kotlinx-coroutines-test`.
- **Dependencies** are declared in the version catalog `gradle/libs.versions.toml`.

Package layers (`io.github.raginlundf.racingmanager.*`):

- `api/<domain>` — Ktor routes and request/response models (the HTTP boundary).
- `application/<domain>` — services / use cases, returning sealed result types.
- `domain/<domain>` — entities and value objects, no framework dependencies.
- `infrastructure/` — `repositories` (Exposed), `tables` (schema), `security`,
  `gateway`, database wiring.

Config: `src/main/resources/application.conf`. The `demo` profile seeds a default
`admin` / `admin` user on first run.

### Webapp — `racingmanager-webapp`

- **Angular 22**, standalone components, **zoneless** change detection
  (no `zone.js` — rendered state must be signals).
- **ngx-translate** for runtime i18n (en/de, no locale URL prefix).
- **RxJS** for HTTP; **TypeScript 6**; ESLint + Prettier; **Vitest** for tests.
- Dev server proxies `/api` (incl. WebSockets) to `http://localhost:8080`
  (`proxy.conf.json`).

Layout and conventions for pages, clients, and views are in
`instructions/webapp-ui-guidelines.md` — read it before touching the webapp.

### Common commands

Backend (from repo root):

```
./gradlew :racingmanager-backend:test         # run backend tests
./gradlew :racingmanager-backend:build        # compile + test the backend
./gradlew buildAll                            # build every module
```

The server has no Gradle `run` task — start it from the `ApplicationKt` IDE run
config (Ktor main class `io.github.raginlundf.racingmanager.ApplicationKt`),
which serves the API on `:8080`.

Webapp (from `racingmanager-webapp/`):

```
npm start                                     # ng serve on :4200, proxied to :8080
npm test                                      # Vitest
npm run build                                 # production build
npm run lint
```

IDE run configs live in `.run/` (`ApplicationKt`, `Angular Server`).

## Always read first

- `instructions/coding-guidelines.md` — creating, editing, reviewing, or testing Kotlin code.
- `instructions/testing.md` — tests are created, modified, reviewed, or behavior changes.

## Load by task scope

| Task | Read |
|---|---|
| Module boundaries, package layout, DTO/domain boundaries, adapters, Gradle/Maven structure | `instructions/module-architecture.md` |
| REST API design, `openapi-*.yaml`, status codes, paging, sorting, errors, scopes, compatibility | `instructions/api-guidelines.md`, `instructions/security.md`, `skills/openapi-authoring.skill.md` |
| Unit tests and coverage | `instructions/testing.md`, `harness/test-generation-harness.md`, `harness/no-cheating-test-harness.md` |
| REST API tests (Cucumber/Gherkin) | `instructions/rest-api-cucumber.md`, `harness/rest-api-test-harness.md`, `skills/rest-api-cucumber-testing.skill.md` |
| Static analysis, Detekt, SonarQube, ktlint, formatting | `instructions/static-analysis.md`, `instructions/editorconfig-style.md`, `harness/static-analysis-remediation-harness.md` |
| Code review or refactoring | `harness/code-change-harness.md`, `harness/review-harness.md` |
| Webapp UI — new/changed Angular page, view, component, or API client (`racingmanager-webapp`) | `instructions/webapp-ui-guidelines.md`, `skills/webapp-page-authoring.skill.md` |

## Conflict resolution

1. Explicit user instructions in the current task win.
2. Repository-local instructions beat this reusable package.
3. The most specific instruction file beats a general one.
4. Module-specific rules beat repository-wide rules.
5. Preserve behavior before refactoring style.
6. Never weaken tests, security, static analysis, or compatibility to ease implementation.
7. Explain unresolved conflicts before changing architecture, behavior, or public contracts.
