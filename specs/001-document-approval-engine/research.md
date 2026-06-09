# Phase 0 Research: Document Approval Workflow Engine

All major technology and integration decisions were made during brainstorming and ratified in
the constitution; there were **no open `NEEDS CLARIFICATION` items**. This document records the
decisions, rationale, and rejected alternatives so the design is traceable.

## D1 — Orchestration substrate

- **Decision**: Adopt Temporal for durable orchestration, accessed only through a
  `WorkflowEngine` port. Each running flow instance maps to one Temporal Workflow execution.
- **Rationale**: Temporal provides durability, retries, timers, and long-running "wait for a
  human" semantics out of the box; the port keeps it swappable and out of the domain (Principle IV).
- **Alternatives considered**: Camunda (rejected first cut — BPMN + licensing weight, though best
  human-task fit); Kafka/Zeebe (rejected — a log/transport, not an orchestrator); hand-built
  state-machine engine (rejected — reinvents durability).

## D2 — Temporal ↔ domain integration pattern (Approach A)

- **Decision**: Temporal owns orchestration *position*; PostgreSQL owns human-task *state*,
  business data, and audit. A human step runs a Temporal **Activity** that calls the application
  `CreateTask` path (writes a `task` row), then the workflow **blocks on a Signal**. A REST action
  (claim/decide) validates domain rules, writes state + audit + outbox in one transaction, then
  sends the Temporal **Signal** that advances the workflow.
- **Rationale**: Keeps task-list queries and admin operations as simple DB + signal operations;
  avoids querying Temporal for business state; keeps the domain Temporal-free.
- **Alternatives considered**: Temporal as source of truth with DB as read-model (rejected — poor
  cross-flow querying, awkward admin ops); domain-owned state machine with Temporal only for timers
  (rejected — underuses Temporal, drags back toward a hand-built engine).

## D3 — Workflow definition representation

- **Decision**: Flow definitions are **data** (states, transitions with trigger + guard, terminal
  outcomes, designated initiator group, per-state candidate group). A single generic Temporal
  workflow interprets the active definition; "document approval" is the first definition (seeded).
- **Rationale**: Satisfies FR-001/SC-007 (new flow shapes without bespoke code). The document-
  approval definition encodes the return-to-submitter rejection policy (per Clarifications).
- **Alternatives considered**: One Temporal workflow type hand-coded per flow (rejected — not
  data-defined, fails SC-007).

## D4 — Persistence & type-safe SQL

- **Decision**: PostgreSQL with **jOOQ** for type-safe SQL; **Flyway** for migrations; jOOQ code
  generated from the migrated schema during build.
- **Rationale**: Explicit SQL fits an append-only audit log and a transactional outbox better than
  ORM magic and keeps the adapter honest; constitution mandates jOOQ.
- **Alternatives considered**: Spring Data JPA (rejected — Spring + ORM), Exposed (viable, but jOOQ
  chosen for SQL fidelity and constitution alignment).

## D5 — Reliable event publication

- **Decision**: **Transactional outbox**. Domain events are written to an `outbox_event` table in
  the same transaction as the state change; a poller in api-service publishes them as **CloudEvents**
  and marks them dispatched (at-least-once; consumers dedupe on event id).
- **Rationale**: Guarantees state/event consistency (Principle V, FR-020) without distributed
  transactions; CloudEvents gives a vendor-neutral envelope (FR-019).
- **Alternatives considered**: Publish-then-commit (rejected — lost/ghost events); CDC/Debezium
  (rejected — heavier infra than needed for first deliverable; outbox poller is sufficient).

## D6 — Single-effective-decision under concurrency

- **Decision**: Enforce at the database. A task carries a `status` and `version`; claim and decide
  use conditional updates (optimistic concurrency: `UPDATE ... WHERE id = ? AND status = ? AND
  version = ?`). Zero rows updated ⇒ refuse the action. The Temporal signal is sent only after the
  authoritative DB transition commits.
- **Rationale**: Makes FR-013/SC-004 a hard guarantee at the source of truth; avoids races between
  concurrent claimers/deciders. Signals are idempotent at the workflow.
- **Alternatives considered**: Rely on Temporal signal ordering alone (rejected — DB is the task
  source of truth; UI reads must be consistent); pessimistic row locks (rejected — optimistic is
  sufficient and lower-contention at this scale).

## D7 — HTTP framework & DI

- **Decision**: **Ktor** for the REST API (constitution); **Koin** for dependency wiring in the
  app/adapter composition roots only — `domain`/`application` remain DI-framework-free
  (constructor injection).
- **Rationale**: Ktor mandated; Koin is lightweight, Ktor-idiomatic, and annotation-free so it does
  not leak into inner layers.
- **Alternatives considered**: Spring Boot (prohibited by constitution); manual wiring only (viable;
  Koin chosen for ergonomics while preserving purity).

## D8 — Identity / actor context (first deliverable)

- **Decision**: An `ActorContext` port supplies the acting person and their group memberships. The
  REST adapter populates it from a trusted source; for the first deliverable this is a simple
  authenticated header/token mapping (no full IdP). Persons and groups are stored as minimal
  references; group membership is queryable for assignment and authorization (initiator group,
  candidate group, claim eligibility).
- **Rationale**: Satisfies FR-021/FR-007/FR-002 group-based assignment and submission gating while
  deferring full OIDC/SSO (documented assumption). The port lets real identity slot in later.
- **Alternatives considered**: Full OIDC now (rejected — out of scope); no identity (rejected —
  assignment and authorization need actor + groups).

## D9 — Build & module-boundary enforcement

- **Decision**: Gradle Kotlin DSL, version catalog, convention plugins in `build-logic/`. A
  boundary test (Konsist or ArchUnit) fails the build if `domain`/`application` acquire a forbidden
  framework/infrastructure dependency.
- **Rationale**: Makes Principle I mechanically enforced, not aspirational.
- **Alternatives considered**: Manual review only (rejected — drifts over time).

## Open items intentionally deferred to implementation

- **Observability** (structured logging, metrics, tracing): standard practice; chosen during
  implementation, no spec-level impact.
- **External IdP integration** (OIDC/SSO): later phase behind `ActorContext`.
- **Concrete Temporal deployment** (self-hosted vs Temporal Cloud): an operational choice; the
  port and tests use `TestWorkflowEnvironment`/Testcontainers regardless.
