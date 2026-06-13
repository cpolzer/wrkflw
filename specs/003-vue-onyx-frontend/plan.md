# Implementation Plan: Vue/Onyx Web Frontend

**Branch**: `003-vue-onyx-frontend` | **Date**: 2026-06-10 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-vue-onyx-frontend/spec.md`

## Summary

Add a Vue 3 single-page application that serves as the human-facing layer of the wrkflw
document-approval engine. The UI allows submitters to initiate approval flows (using a
workflow-definition-driven form), reviewers to claim and decide on tasks via a worklist, and
all users to track flow history. Authentication is delegated to Keycloak via OIDC. The frontend
is a standalone TypeScript/Vite project living at `ui/` in the monorepo; it communicates
exclusively through the existing REST API defined in `specs/001-document-approval-engine/contracts/openapi.yaml`.

## Technical Context

**Language/Version**: TypeScript 5.x (strict mode)

**Primary Dependencies**:
- Vue 3 (Composition API) — UI framework
- Onyx (`@sit-onyx/...`) — Schwarz IT Vue component library (design system)
- Vite — build tooling and dev server
- Vue Router 4 — client-side routing
- Pinia — state management
- `oidc-client-ts` — standards-compliant OIDC/OAuth2 client (replaces `keycloak-js` for portability)
- `openapi-typescript` — generates TypeScript types from `openapi.yaml` at build time
- Axios or native `fetch` — HTTP client for backend API calls

**Storage**: None (all state is server-side via the backend API; Pinia holds transient in-session state only)

**Testing**:
- Vitest — unit and component tests
- Playwright — E2E browser tests against a running backend + Keycloak

**Target Platform**: Current-generation desktop browsers (Chrome, Firefox, Edge, Safari); desktop-first layout

**Project Type**: Single-page web application (Vue SPA)

**Performance Goals**: Worklist view loads within 2 s; all user actions reflect outcome within 3 s (SC-002, SC-004)

**Constraints**: Static build (no SSR); desktop-first; no file upload; no real-time push in v1

**Scale/Scope**: ~1,000 active flows; ~100 concurrent reviewers (inherits backend scale target)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Hexagonal Architecture | ✅ Pass | Frontend uses a layered internal structure: `api/` (adapter — wraps REST calls), `stores/` (application-layer state), `views/` and `components/` (presentation). Dependencies point inward; API types are generated contracts, not leaked internals. |
| II. Test-First Discipline | ✅ Pass | Vitest for unit/component tests; Playwright for E2E. Tests are written before implementation per Red→Green→Refactor. Local validation gate (`npm run check`) required before push. |
| III. Auditability | ✅ Pass | Audit records are the backend's responsibility. The frontend reads and displays the immutable history; it never produces audit data directly. |
| IV. Orchestration Behind a Port | ✅ N/A | Frontend interacts only with the REST API; it has no knowledge of Temporal or the workflow engine. |
| V. Explicit Contracts | ✅ Pass | TypeScript types are generated from the OpenAPI spec at build time. The frontend consumes the backend contract; it does not invent its own shapes. |
| VI. Local Validation Before Push | ✅ Pass | `npm run check` (lint + typecheck + unit tests + build) must pass locally before push. Playwright E2E require the full stack; run via `docker compose up` + `npm run test:e2e`. |
| Technology Constraint (Kotlin/Gradle) | ⚠️ Justified Exception | Web UIs cannot be Kotlin. The `ui/` project is a standalone Vite/Node project. It lives alongside the Gradle monorepo without modifying it. See Complexity Tracking. |

## Project Structure

### Documentation (this feature)

```text
specs/003-vue-onyx-frontend/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output — frontend DTO shapes
├── quickstart.md        # Phase 1 output — local dev setup
├── contracts/
│   └── api-consumption.md   # How the frontend consumes the backend REST API
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
ui/                              # Vue SPA — standalone Vite/TypeScript project
├── src/
│   ├── api/                     # Adapter layer: wraps backend REST calls
│   │   ├── client.ts            # Axios/fetch instance with auth headers
│   │   ├── flows.ts             # Flow endpoints
│   │   ├── tasks.ts             # Task endpoints (claim, release, decision)
│   │   ├── worklist.ts          # Worklist endpoints
│   │   └── types.ts             # Auto-generated from openapi.yaml (do not edit)
│   ├── auth/
│   │   └── oidc.ts              # oidc-client-ts setup; token lifecycle
│   ├── composables/             # Reusable Vue composables (application logic)
│   │   ├── useFlows.ts
│   │   ├── useWorklist.ts
│   │   └── useNotifications.ts
│   ├── stores/                  # Pinia stores
│   │   ├── auth.ts              # Authenticated user + token
│   │   └── notifications.ts     # In-app rework notification badge state
│   ├── views/                   # Route-level page components
│   │   ├── MySubmissionsView.vue
│   │   ├── SubmitFlowView.vue
│   │   ├── FlowDetailView.vue
│   │   ├── WorklistView.vue
│   │   └── TaskDetailView.vue
│   ├── components/              # Shared presentational components
│   │   ├── AppNav.vue           # Persistent sidebar/top nav (FR-015)
│   │   ├── FlowStatusBadge.vue
│   │   ├── ReworkBanner.vue     # In-app rework notification (FR-016)
│   │   └── DynamicFormField.vue # Renders a single workflow-definition field
│   ├── router/
│   │   └── index.ts             # Vue Router routes + auth guard
│   └── main.ts
├── tests/
│   ├── unit/                    # Vitest unit + component tests
│   └── e2e/                     # Playwright E2E tests
├── keycloak/
│   └── realm-export.json        # Committed realm config for local Keycloak
├── index.html
├── vite.config.ts
├── vitest.config.ts
├── playwright.config.ts
└── package.json

docker-compose.yml               # Extended: add keycloak + keycloak-db services
```

**Structure Decision**: Standalone `ui/` project at the repo root. The Gradle monorepo is unchanged. Keycloak is added to the existing `docker-compose.yml` alongside Postgres and Temporal. TypeScript types are code-generated from the OpenAPI spec; the generated file is committed to allow offline builds and to surface contract drift in PR diffs.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| Non-Kotlin project (`ui/`) added to Gradle monorepo | Web UIs require a browser-compatible JS/TS build pipeline; Kotlin transpilation to JS (Kotlin/JS) is experimental, poorly supported by Onyx, and not the Schwarz IT standard | Kotlin/JS lacks Onyx integration and is not aligned with the frontend ecosystem the team chose |
| Keycloak added to docker-compose | OIDC/SSO auth is required (spec clarification Q2); a real IdP is needed for E2E testing and local dev parity with production | A mock OIDC server would diverge from Keycloak's token/claim behaviour, risking login-flow defects in production |
