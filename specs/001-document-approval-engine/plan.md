# Implementation Plan: Document Approval Workflow Engine

**Branch**: `001-document-approval-engine` | **Date**: 2026-06-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-document-approval-engine/spec.md`

## Summary

Deliver the document-approval use case end to end on a data-defined, human-centric workflow
engine. A flow is described as data (states, transitions, candidate groups, initiator group);
Temporal durably orchestrates each running flow; PostgreSQL is the system of record for
human-task state, business data, and an append-only audit log. People interact through a Ktor
REST API (submit, claim, release, decide, query); meaningful state changes are published as
CloudEvents through a transactional outbox. The codebase is a Kotlin/JVM Gradle multi-module
monorepo built strictly to hexagonal architecture: `domain` and `application` are
framework-free and reach infrastructure only through ports, with Temporal, Postgres/jOOQ,
Ktor, and CloudEvents living in adapters. Two deployables share the inner modules: an
**api-service** (REST + Temporal client signals + outbox publisher) and a **worker-service**
(Temporal workflows + activities).

## Technical Context

**Language/Version**: Kotlin 2.0+ on JDK 21 (LTS)

**Primary Dependencies**:
- Ktor (HTTP server, REST API) — *Spring Boot prohibited per constitution*
- Temporal Java SDK (durable orchestration), used from Kotlin
- jOOQ (type-safe SQL) + PostgreSQL JDBC driver
- Flyway (schema migrations; jOOQ codegen runs off the migrated schema)
- CloudEvents Java SDK (event envelope)
- kotlinx.serialization (REST DTO + event payload JSON)
- Koin (lightweight DI, **app/adapter composition roots only**; inner layers stay DI-free)
- Gradle (Kotlin DSL), version catalog (`gradle/libs.versions.toml`), convention plugins in `build-logic/`

**Storage**: PostgreSQL (current state in normalized tables + append-only audit log + transactional outbox)

**Testing**: JUnit 5 + kotlin.test; Testcontainers (PostgreSQL); Temporal `TestWorkflowEnvironment`/Testcontainers for orchestration; fast framework-free unit tests for `domain`/`application`

**Target Platform**: Linux server / containers (two JVM deployables); external Temporal cluster (self-hosted or Temporal Cloud) and PostgreSQL

**Project Type**: Backend — Gradle multi-module monorepo, two deployable services sharing inner modules

**Performance Goals**: ~1,000 active flows and ~100 concurrent reviewers; work-list and flow-status queries < 1s p95 (SC-008)

**Constraints**:
- Inward-only dependencies; `domain`/`application` reference no framework, SQL, or Temporal types (Principle I)
- Exactly one effective decision per task under concurrency (FR-013, SC-004)
- State and emitted events atomically consistent via transactional outbox (Principle V, FR-020)
- Every state change produces an immutable audit record (Principle III, FR-014)
- Internal flow progression via Temporal signals/activities only — no broker driving steps (Principle IV)

**Scale/Scope**: First deliverable = the document-approval flow only (US1–US5). Steps, admin operations (FR-022–026), external IdP, and a web frontend are explicitly out of scope.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Gate | Status |
|---|-----------|------|--------|
| I | Hexagonal Architecture (NON-NEGOTIABLE) | `domain`/`application` Gradle modules declare **no** dependency on Ktor, Temporal, jOOQ, Koin, or JDBC; all I/O via ports; apps are composition roots only | ✅ PASS — enforced by module graph (see Project Structure) |
| II | Test-First (NON-NEGOTIABLE) | TDD red→green→refactor; unit tests for inner layers; Testcontainers integration tests for every adapter & contract; explicit concurrency/audit/outbox tests | ✅ PASS — test strategy defined; `/speckit-tasks` will emit test-first tasks (overrides template "tests optional") |
| III | Auditability & Traceability | Append-only `audit_entry` table; every command writes an audit record in the same transaction; flow lifecycle reconstructable from history | ✅ PASS — see data-model.md |
| IV | Orchestration Behind a Port | `WorkflowEngine` port in `domain`; Temporal only in `adapters/temporal`; progression via signals/activities; CloudEvents/broker for external integration only | ✅ PASS — see research.md |
| V | Explicit Contracts & Consistency | Transactional outbox (state + outbox row in one tx); single-effective-decision via DB optimistic concurrency; CloudEvents envelope; intention-revealing ports | ✅ PASS — see data-model.md & contracts/ |

**Technology constraints** (constitution §Technology & Architecture): Kotlin/JVM ✅, Ktor for REST (not Spring) ✅, Temporal via port ✅, Postgres + jOOQ ✅, CloudEvents ✅, candidate-group/claim-to-act ✅, shared-module multi-deployable topology ✅.

**Result: PASS — no violations. Complexity Tracking left empty.**

## Project Structure

### Documentation (this feature)

```text
specs/001-document-approval-engine/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (openapi.yaml, events.md)
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
wrkflw/
├── settings.gradle.kts                # includes all modules
├── build.gradle.kts                   # root
├── gradle/libs.versions.toml          # version catalog (single source of versions)
├── build-logic/                       # Gradle convention plugins (kotlin, test, jooq, ktor)
│
├── domain/                            # PURE Kotlin — no framework/SQL/Temporal/DI deps
│   └── src/{main,test}/kotlin/dev/wrkflw/domain/
│       ├── flow/                      # FlowDefinition, State, Transition, FlowInstance, FlowStatus
│       ├── task/                      # Task, TaskStatus, Decision, Outcome, Claim
│       ├── identity/                  # ActorId, GroupId, candidate-group membership rules
│       ├── audit/                     # AuditEntry, AuditEventType
│       ├── event/                     # DomainEvent hierarchy (FlowStarted, TaskCreated, ...)
│       └── port/                      # FlowDefinitionRepository, FlowInstanceRepository,
│                                      #   TaskRepository, AuditLog, WorkflowEngine,
│                                      #   DomainEventPublisher, Clock, ActorContext
│
├── application/                       # Use cases — depends only on domain
│   └── src/{main,test}/kotlin/dev/wrkflw/application/
│       ├── command/                   # SubmitDocument, ClaimTask, ReleaseTask, SubmitDecision
│       ├── query/                     # GroupWorkList, MyTasks, FlowStatus
│       ├── service/                   # application services (transaction boundary, orchestrate ports)
│       └── port/                      # TransactionRunner (UnitOfWork) port if needed
│
├── adapters/
│   ├── persistence-postgres/          # jOOQ repos, AuditLog, Outbox; Flyway migrations; jOOQ-generated code
│   │   └── src/main/resources/db/migration/
│   ├── temporal/                      # WorkflowEngine impl (Temporal client), DocumentApprovalWorkflow,
│   │                                  #   activities that call application services, signal sender
│   ├── rest-api/                      # Ktor routes, request/response DTOs, mappers, ActorContext from auth
│   └── eventing-cloudevents/          # outbox poller → CloudEvents publisher (DomainEventPublisher impl)
│
└── apps/
    ├── api-service/                   # Ktor server main + Koin wiring + outbox publisher runner
    └── worker-service/                # Temporal worker host main + Koin wiring
```

**Structure Decision**: Gradle multi-module monorepo. The dependency graph mechanically enforces
Principle I: `domain` has zero infrastructure dependencies; `application` depends only on
`domain`; each adapter depends on `application`/`domain` and its own infrastructure library; the
two apps in `apps/` are the only modules that depend on adapters and wire them to ports. Both
apps reuse `domain` + `application` + `persistence-postgres`; they differ only in active driving
adapters (`rest-api` + `eventing-cloudevents` + Temporal client for api-service; `temporal`
worker host for worker-service). A build check (e.g., a convention-plugin dependency rule or
ArchUnit/Konsist test) will fail the build if `domain`/`application` gain a forbidden dependency.

## Complexity Tracking

> No constitution violations. Section intentionally empty.
