# AGENTS.md — wrkflw

## Repo Status

- **Phase**: Planning/spec only. No source code, no Gradle files, no `src/` yet.
- **Current feature**: `001-document-approval-engine` (branch + `specs/001-document-approval-engine/`)
- **Implementation starts from**: `specs/001-document-approval-engine/tasks.md`

## Spec Kit Workflow

This repo uses the Spec Kit lifecycle. Follow this order:
```
constitution → specify → (clarify) → plan → tasks → implement
```
- Business specs stay in `specs/NNN-feature/` — business-facing, technology-agnostic.
- Technology decisions live in `plan.md`, not `spec.md`.
- Constitution at `.specify/memory/constitution.md` — **supersedes all other guidance**.

## Constitution (5 Core Principles)

All non-negotiable. Violations require explicit, recorded justification.

| # | Principle | What it means |
|---|-----------|---------------|
| I | Hexagonal Architecture | `domain` ← `application` ← `adapters` ← `apps`. Inner layers reference **no** framework, SQL, Temporal, or DI types. |
| II | Test-First (NON-NEGOTIABLE) | TDD red→green→refactor. Tests are **never optional** — overrides any template saying otherwise. |
| III | Auditability | Every state change → immutable, ordered audit record. Append-only. |
| IV | Orchestration Behind a Port | `WorkflowEngine` port in domain. Temporal lives only in `adapters/temporal`. |
| V | Explicit Contracts & Consistency | Transactional outbox for state↔event consistency. Single-effective-decision under concurrency. |

## Target Architecture (when implementation begins)

**Kotlin 2.0+ / JDK 21** — Gradle multi-module monorepo, two deployables sharing inner modules.

```
domain/              # Pure Kotlin — zero infra deps
application/         # Use cases — depends only on domain
adapters/
  persistence-postgres/   # jOOQ + Flyway migrations
  temporal/               # WorkflowEngine impl
  rest-api/               # Ktor routes + DTOs
  eventing-cloudevents/   # Outbox poller → CloudEvents
apps/
  api-service/            # Ktor server + outbox publisher
  worker-service/         # Temporal worker host
```

**Key constraints**:
- **Ktor** for REST. **Spring Boot is PROHIBITED**.
- **Koin** for DI — composition roots (apps) only, never in inner layers.
- **kotlinx.serialization** for JSON (REST DTOs + event payloads).
- **jOOQ** for type-safe SQL; **Flyway** for migrations; jOOQ codegen runs off migrated schema.
- Version catalog: `gradle/libs.versions.toml` — single source of versions.
- Convention plugins in `build-logic/`.

## Developer Commands (target — not yet available)

```bash
./gradlew build                                    # compile + all tests
./gradlew :adapters:persistence-postgres:flywayMigrate  # apply schema
./gradlew :apps:worker-service:run                 # Temporal worker
./gradlew :apps:api-service:run                    # REST API on :8080
docker compose up -d postgres temporal             # local deps
```

## Branching

- Feature branches: `NNN-short-name` convention (e.g., `001-document-approval-engine`).
- Artifacts live under `specs/NNN-short-name/`.

## Testing Strategy

- `domain`/`application`: fast unit tests, no infrastructure.
- Adapters: integration tests with **real** backing services (Testcontainers for PostgreSQL, Temporal `TestWorkflowEnvironment`).
- Concurrency, audit, and outbox guarantees must have explicit tests.

## Frontend (ui/)

**Vue 3 / Vite / TypeScript** — located in `ui/`, tested with Vitest + Playwright.

```
ui/src/
  api/         # Adapter layer — generated types + API client
  auth/        # OIDC via oidc-client-ts
  composables/ # Application logic (useFlows, useWorklist, useErrorHandler)
  stores/      # Pinia stores (auth, notifications)
  components/  # Shared UI (AppNav, DynamicFormField, FlowStatusBadge, ReworkBanner)
  views/       # Pages (MySubmissionsView, SubmitFlowView, FlowDetailView, WorklistView, TaskDetailView)
  router/      # Vue Router with auth guard and smart landing
```

**Local setup**:
```bash
docker compose up -d           # PostgreSQL + Temporal + Keycloak at :8180
cd ui && npm install
npm run dev                    # Dev server at :5173 with /api proxy to :8080
npm run check                  # lint + typecheck + unit tests + build (local gate)
npm run test:e2e               # Playwright E2E (requires full stack)
```

**Test users** (Keycloak realm `wrkflw`, all password `password`):
- `alice` — group `initiators` (can submit documents)
- `bob` — group `legal-reviewers` (can review/decide)
- `carol` — both groups

**Key design decisions**:
- OIDC/PKCE via `oidc-client-ts`; auth headers sent as `Authorization: Bearer` + compat `X-Actor-Id`/`X-Actor-Groups`
- Form data encoded as JSON in backend's `documentRef` field until a richer API exists
- `getSubmitterFlows` has no backend endpoint yet — `MySubmissionsView` will remain empty until backend adds `GET /flows`

## References

- Constitution: `.specify/memory/constitution.md`
- Frontend spec: `specs/003-vue-onyx-frontend/spec.md`
- Frontend plan: `specs/003-vue-onyx-frontend/plan.md`
- Frontend tasks: `specs/003-vue-onyx-frontend/tasks.md`
- Backend spec: `specs/001-document-approval-engine/spec.md`
- Backend plan: `specs/001-document-approval-engine/plan.md`
- Backend contracts: `specs/001-document-approval-engine/contracts/`
