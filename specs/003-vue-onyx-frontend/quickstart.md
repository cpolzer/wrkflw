# Quickstart: Vue/Onyx Web Frontend

## Prerequisites

- Node.js 20+ (managed via `mise`: `mise install`)
- Docker + Docker Compose (for full stack including Keycloak)
- Backend running (see `docs/tutorials/run-locally.md`)

## First-time setup

```bash
# Install Node dependencies
cd ui && npm install

# Generate TypeScript types from the OpenAPI spec
npm run generate:types
```

## Start the full local stack

```bash
# From repo root — starts Postgres, Temporal, Keycloak, api-service, worker-service
docker compose up -d

# Start the Vue dev server (HMR enabled)
cd ui && npm run dev
# → http://localhost:5173
```

## Keycloak

Keycloak starts at **http://localhost:8180**.

Admin console: http://localhost:8180 (admin / admin)

The `wrkflw` realm is imported automatically on first boot from `ui/keycloak/realm-export.json`.

**Test users** (password: `password` for all):

| User | Groups | Role |
|------|--------|------|
| `alice` | `initiators` | Submitter only |
| `bob` | `legal-reviewers` | Reviewer only |
| `carol` | `initiators`, `legal-reviewers` | Both (dual-role) |

## Local validation (run before every push)

```bash
cd ui
npm run check   # lint + typecheck + unit tests + build
```

For E2E tests (requires full stack running):

```bash
npm run test:e2e
```

## Environment variables

Copy `ui/.env.example` to `ui/.env.local` for local overrides.

| Variable | Default | Description |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080/api/v1` | Backend API base URL |
| `VITE_OIDC_AUTHORITY` | `http://localhost:8180/realms/wrkflw` | Keycloak realm URL |
| `VITE_OIDC_CLIENT_ID` | `wrkflw-ui` | OIDC client ID |
| `VITE_API_TIMEOUT_MS` | `10000` | Request timeout in ms |
