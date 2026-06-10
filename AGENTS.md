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

## References

- Constitution: `.specify/memory/constitution.md`
- Plan: `specs/001-document-approval-engine/plan.md`
- Spec: `specs/001-document-approval-engine/spec.md`
- Data model: `specs/001-document-approval-engine/data-model.md`
- Quickstart: `specs/001-document-approval-engine/quickstart.md`
- Contracts: `specs/001-document-approval-engine/contracts/`
- Tasks: `specs/001-document-approval-engine/tasks.md`
