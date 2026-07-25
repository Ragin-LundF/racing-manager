# Racing Manager

Racing Manager is a race-day operations tool for racing events (e.g.
Pinewood-Derby-style events with two or more lanes). It covers the whole
event lifecycle: create an event, register participants, run qualification
and knockout rounds, control races heat by heat, and publish results — with
a live spectator view for the audience.

## Features

- **Event management** — create, edit, activate, archive and reactivate
  events; each event tracks its own status (draft, active, completed,
  archived).
- **Participants** — add participants manually, import them via CSV, or
  randomize lane/start-number assignments.
- **Qualification** — configure the number of runs, generate the heat
  schedule, track live progress, and view ranked results.
- **Knockout phase** — bracket-based elimination rounds following
  qualification.
- **Race control** — operate heats in real time (arm, start, finish, cancel).
- **Results** — qualification and knockout rankings, event completion and
  reopening.
- **Export & backup** — export results as CSV, HTML or JSON, and export/
  restore a full event backup.
- **Spectator view** — a read-only live view for the audience, shared via a
  one-time exchange code (no login required).
- **Audit log** — a chronological log of every state-changing action taken
  on an event.
- **Diagnostics** — database/health checks and recovery tools for
  unfinished heats (admin only).
- **Multi-tenant / hosted mode** — run as a single local installation, or
  as a hosted multi-tenant service where organizations self-register.

## Specification of the API

You can find the specification of the API under [SYSTEM_SPEC.md](SYSTEM_SPEC.md).

## Project structure

A Gradle multi-module repository:

| Module | Purpose | Stack |
|---|---|---|
| `racingmanager-backend` | REST + WebSocket API, persistence, domain logic | Kotlin / Ktor |
| `racingmanager-webapp` | Operator and spectator web UI | Angular |

## Requirements

- JDK 25
- Node.js with npm 10 (see `packageManager` in `racingmanager-webapp/package.json`)

## Getting started — local setup

Local mode runs a single implicit tenant with no cloud dependency — the
default, and the right choice for running the app at your own event.

1. **Start the backend** (serves the API on `:8080`):
   ```
   ./gradlew :racingmanager-backend:build
   ```
   Then run the `ApplicationKt` main class (`io.github.raginlundf.racingmanager.ApplicationKt`)
   from your IDE — an IDE run config is already provided under `.run/`. There
   is no Gradle `run` task.

2. **Start the web UI** (serves the UI on `:4200`, proxying `/api` to `:8080`):
   ```
   cd racingmanager-webapp
   npm install
   npm start
   ```

3. Open `http://localhost:4200`.
   - With the default `demo` profile, a default administrator account
     (`admin` / `admin`) is already seeded — just log in.
   - With any other profile (e.g. `prod`), no account exists yet: the app
     redirects you to a one-time setup page to create the first
     administrator.

Configuration lives in `racingmanager-backend/src/main/resources/application.conf`
and can be overridden with environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `RACINGMANAGER_PROFILE` | `demo` | `demo` seeds the default admin; set to e.g. `prod` to disable seeding |
| `RACINGMANAGER_MODE` | `local` | `local` or `hosted` (see below) |
| `RACINGMANAGER_DB_PATH` | `~/.racingmanager/racingmanager.db` | SQLite file location (ignored when `RACINGMANAGER_DB_URL` is set) |
| `RACINGMANAGER_DB_URL` | _(unset → SQLite)_ | JDBC URL to use another database, e.g. `jdbc:mariadb://host:3306/racingmanager` |
| `RACINGMANAGER_DB_USER` | — | Database user (for `RACINGMANAGER_DB_URL`) |
| `RACINGMANAGER_DB_PASSWORD` | — | Database password (for `RACINGMANAGER_DB_URL`) |

## Database

Racing Manager supports two databases from the same build; the active one is
chosen at runtime by the JDBC URL:

- **SQLite** (default) — an embedded file, zero setup. Ideal for local and
  offline installs. Used automatically when `RACINGMANAGER_DB_URL` is unset.
- **MariaDB** — for hosted/server deployments. Set `RACINGMANAGER_DB_URL` (plus
  user/password) and the app connects to MariaDB instead; the driver is inferred
  from the URL prefix. Schema migrations (Liquibase) run automatically on start
  against whichever database is configured.

To run MariaDB locally for development or testing, a Compose file is provided:

```
docker compose -f devops/docker-compose.yml up -d
```

Then start the backend pointed at it (dev credentials from that Compose file):

```
RACINGMANAGER_DB_URL=jdbc:mariadb://localhost:3306/racingmanager \
RACINGMANAGER_DB_USER=racingmanager \
RACINGMANAGER_DB_PASSWORD=racingmanager \
  ./gradlew :racingmanager-backend:build   # then run ApplicationKt with the same env
```

> **Note:** SQLite file backups (created automatically before each migration)
> apply to the SQLite backend only. For MariaDB, use your usual server backup
> (e.g. `mariadb-dump`).

## Getting started — hosted (SaaS) setup

Hosted mode is multi-tenant: any number of organizations can register their
own workspace and manage their own events and users, isolated from each
other. Unlike local mode, hosted mode never generates its JWT signing key
automatically — the deployment must supply its own key, managed through its
own deployment pipeline and rotation policy. Either an RSA key pair (RS256)
or a single shared secret (HS256) works; a secret is simpler to operate for
a single-instance deployment, at the cost of every verifier needing the same
secret (an RSA key can be retired to verification-only during rotation — a
secret can't).

1. **Generate a signing key.** Pick one:

   - RSA key pair, DER-encoded (PKCS8 for the private key, X.509
     `SubjectPublicKeyInfo` for the public key), base64-encoded:
     ```
     openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -outform DER -out private.der
     openssl pkey -in private.der -inform DER -pubout -outform DER -out public.der
     PRIVATE_KEY_B64=$(base64 < private.der | tr -d '\n')
     PUBLIC_KEY_B64=$(base64 < public.der | tr -d '\n')
     ```
   - or a shared secret (32+ random bytes recommended):
     ```
     JWT_SECRET=$(openssl rand -base64 48)
     ```

2. **Create a hosted config file** (e.g. `application-hosted.conf`, kept out
   of version control) that supplies the key — see the commented example in
   `racingmanager-backend/src/main/resources/application.conf`. Passing
   `-config=` replaces the bundled config rather than merging with it, so
   `include` it explicitly:
   ```hocon
   include classpath("application.conf")

   # RSA:
   racingmanager.jwt.keys = [
     { kid = "2026-01", publicKey = "${PUBLIC_KEY_B64}", privateKey = "${PRIVATE_KEY_B64}", active = true }
   ]

   # or a shared secret:
   racingmanager.jwt.keys = [
     { kid = "2026-01", algorithm = "HS256", secret = "${JWT_SECRET}", active = true }
   ]
   ```

3. Start the backend with hosted mode enabled and the extra config file
   merged in:
   ```
   RACINGMANAGER_MODE=hosted RACINGMANAGER_PROFILE=prod \
     ./gradlew :racingmanager-backend:build
   ```
   Then run `ApplicationKt` with the same environment variables set, and
   pass the extra file as a program argument so Ktor merges it on top of the
   bundled `application.conf`:
   ```
   -config=application-hosted.conf
   ```
   (In IntelliJ, add this under "Program arguments" on the `ApplicationKt`
   run configuration.)

4. Start the web UI as in the local setup above.
5. Open `http://localhost:4200/register` and register a new organization —
   this creates the tenant and its first administrator account and signs
   you straight in. Further users of that organization can then log in
   under its workspace slug from `/login`.

In `local` mode, `/register` is disabled and the backend responds with
`403 NOT_HOSTED`, and `racingmanager.jwt.keys` is not read at all — the
signing key is generated and persisted automatically on first run.

## Deploying on a webserver (systemd)

For a hosted deployment, run the self-contained fat JAR under `systemd` and
supply **all** configuration — including database credentials and signing
keys — through environment variables. Keep secrets in a root-only
`EnvironmentFile`; do not place a config file with credentials next to the JAR.

1. **Build the fat JAR** and copy it to the server (e.g.
   `/opt/racingmanager/`):
   ```
   ./gradlew :racingmanager-backend:fatJar
   # -> racingmanager-backend/build/libs/*-fat.jar
   ```

2. **Provision MariaDB** and create the database + user (adjust names/password):
   ```sql
   CREATE DATABASE racingmanager CHARACTER SET utf8mb4;
   CREATE USER 'racingmanager'@'%' IDENTIFIED BY 'CHANGE_ME';
   GRANT ALL PRIVILEGES ON racingmanager.* TO 'racingmanager'@'%';
   ```

3. **Create a secrets file** readable only by root, e.g.
   `/etc/racingmanager/racingmanager.env`:
   ```
   PORT=8080
   RACINGMANAGER_MODE=hosted
   RACINGMANAGER_PROFILE=prod
   RACINGMANAGER_DB_URL=jdbc:mariadb://localhost:3306/racingmanager
   RACINGMANAGER_DB_USER=racingmanager
   RACINGMANAGER_DB_PASSWORD=CHANGE_ME
   ```
   ```
   sudo install -d -m 700 /etc/racingmanager
   sudo chmod 600 /etc/racingmanager/racingmanager.env
   ```
   The JWT signing key is required in hosted mode and is passed via a hosted
   config file (see the previous section); reference it with a `-config=` program
   argument in the unit below, or inline the key material as additional
   environment entries.

4. **Create the systemd unit** at
   `/etc/systemd/system/racingmanager.service`:
   ```ini
   [Unit]
   Description=Racing Manager
   After=network.target mariadb.service

   [Service]
   User=racingmanager
   Group=racingmanager
   EnvironmentFile=/etc/racingmanager/racingmanager.env
   ExecStart=/usr/bin/java -jar /opt/racingmanager/racingmanager-backend-fat.jar -config=/etc/racingmanager/application-hosted.conf
   Restart=on-failure
   RestartSec=5

   [Install]
   WantedBy=multi-user.target
   ```

5. **Enable and start it:**
   ```
   sudo systemctl daemon-reload
   sudo systemctl enable --now racingmanager
   sudo journalctl -u racingmanager -f
   ```

6. **Put it behind a reverse proxy** (TLS termination + serve on 443). Minimal
   nginx location:
   ```nginx
   location / {
       proxy_pass http://127.0.0.1:8080;
       proxy_set_header Host $host;
       proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
       proxy_set_header X-Forwarded-Proto $scheme;
       # WebSocket support for live race/spectator updates:
       proxy_http_version 1.1;
       proxy_set_header Upgrade $http_upgrade;
       proxy_set_header Connection "upgrade";
   }
   ```

For Apache it looks like this:

```apache
ProxyPreserveHost On

ProxyPass        "/" "http://localhost:8085/"
ProxyPassReverse "/" "http://localhost:8085/"
 
RewriteEngine On                                                                                                                                                                                                                              

# Real backend requests pass through untouched:                                                                                                                                                                                               
#  - anything with a file extension (.js/.css/.png/index.html …)                                                                                                                                                                              
#  - your API namespace (adjust /api to whatever the backend uses)                                                                                                                                                                            
RewriteCond %{REQUEST_URI} !\.[a-zA-Z0-9]+$                                                                                                                                                                                                   
RewriteCond %{REQUEST_URI} !^/api/                                                                                                                                                                                                            
# Everything else is an Angular route → serve the SPA shell                                                                                                                                                                                   
RewriteRule ^ /index.html [PT]
```

### Changing the port

The app listens on `8080` by default. If that port is already taken by another
application, set the `PORT` environment variable to a free port — e.g. in the
`EnvironmentFile` from step 3:

```
PORT=8090
```

Then reload and restart, and point the reverse proxy at the new port
(`proxy_pass http://127.0.0.1:8090;`):

```
sudo systemctl daemon-reload
sudo systemctl restart racingmanager
```

## Common commands

Backend (from repo root):

```
./gradlew :racingmanager-backend:test         # run backend tests
./gradlew :racingmanager-backend:build        # compile + test the backend
./gradlew buildAll                            # build every module
```

Webapp (from `racingmanager-webapp/`):

```
npm start                                     # ng serve on :4200, proxied to :8080
npm test                                      # Vitest
npm run build                                 # production build
npm run lint
```
