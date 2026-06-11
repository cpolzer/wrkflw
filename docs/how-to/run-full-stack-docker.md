# Run the full stack with Docker

This guide explains how to run the entire backend (API, worker, Temporal, PostgreSQL, Keycloak)
inside Docker while keeping the UI dev server on the host for hot-reload.

## How it works

The project uses two Docker Compose files:

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Infrastructure: PostgreSQL, Temporal, Temporal UI, Keycloak |
| `docker-compose.local.yml` | Application services: `api-service`, `worker-service` |

Running both together with `mise run local:up` is equivalent to:

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
```

The application images are built from `apps/api-service/Dockerfile` and
`apps/worker-service/Dockerfile`. Each uses a two-stage build:

1. **Builder** — `eclipse-temurin:21-jdk-alpine`, runs `./gradlew :apps/<service>:installDist`
2. **Runtime** — `eclipse-temurin:21-jre-alpine`, copies the distribution and runs it

A BuildKit layer cache (`/root/.gradle`) means subsequent builds only recompile changed modules.

## Service ports

| Service | URL |
|---------|-----|
| REST API | `http://localhost:8080` |
| Temporal gRPC | `localhost:7233` |
| Temporal UI | `http://localhost:8233` |
| Keycloak | `http://localhost:8180` (admin: `admin` / `admin`) |
| PostgreSQL | `localhost:5432` |
| Vite dev server | `http://localhost:5173` |

## Step-by-step

### 1. Start everything

```bash
mise run local:up
```

This builds the application images (first run takes a few minutes while Gradle downloads
dependencies) and starts all seven containers.

### 2. Apply the DB schema (first time only)

Wait until PostgreSQL is healthy, then:

```bash
mise run migrate
```

### 3. Start the UI dev server

```bash
mise run ui:install   # first time only
mise run ui:dev       # http://localhost:5173
```

The Vite dev server proxies `/api` to `localhost:8080`, so the browser never makes
cross-origin requests.

## Rebuild after code changes

```bash
mise run local:up   # --build is always passed; only changed layers rebuild
```

Only the modules that changed will recompile. Infrastructure containers are unaffected.

## Tear down

```bash
mise run local:down        # stop and remove containers, keep volumes
mise run local:down -v     # also remove volumes (wipes the database)
```

## Mise task reference

| Task | Command |
|------|---------|
| `mise run local:up` | Build images + start all services |
| `mise run local:down` | Stop all services |
| `mise run services:up` | Infrastructure only (no app images) |
| `mise run services:down` | Stop infrastructure only |

## Keycloak realm

The Keycloak realm is imported automatically from `ui/keycloak/` on first startup.
No manual realm configuration is needed.
