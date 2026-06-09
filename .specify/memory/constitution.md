<!--
Sync Impact Report
==================
Version change: (template / unversioned) → 1.0.0
Ratification: initial adoption of the wrkflw constitution.

Principles defined:
  I.   Hexagonal Architecture (NON-NEGOTIABLE)
  II.  Test-First Discipline (NON-NEGOTIABLE)
  III. Auditability & Traceability
  IV.  Orchestration Behind a Port
  V.   Explicit Contracts & Consistency

Added sections:
  - Technology & Architecture Constraints
  - Development Workflow & Quality Gates
  - Governance

Removed sections: none (template placeholders replaced).

Templates requiring updates:
  ✅ .specify/templates/plan-template.md — "Constitution Check" gate aligns; concrete gates
     derived from Principles I–V (no structural change needed; gate content is filled per-feature).
  ⚠ .specify/templates/tasks-template.md — template states tests are OPTIONAL; this is
     OVERRIDDEN by Principle II (Test-First, NON-NEGOTIABLE). Generated tasks.md MUST include
     test tasks for domain/application units and adapter/contract integration tests.
  ✅ .specify/templates/spec-template.md — no change required; remains business-facing and
     technology-agnostic, consistent with the spec/plan separation in Development Workflow.

Follow-up TODOs: none. All placeholders resolved.
-->

# wrkflw Constitution

## Core Principles

### I. Hexagonal Architecture (NON-NEGOTIABLE)

Every part of the system is structured as ports and adapters, with dependencies pointing
inward only: `domain` ← `application` ← `adapters` ← `apps`.

- The `domain` and `application` layers MUST NOT reference any framework, transport,
  persistence library, or orchestration SDK. No HTTP framework, no database/SQL library, and
  no workflow-engine types may appear in these layers.
- All interaction with the outside world (persistence, messaging, orchestration, HTTP,
  identity) MUST occur through a port — an interface owned by the domain or application layer —
  with the concrete implementation living in an adapter.
- Apps are thin composition roots: they wire adapters to ports and own configuration; they
  contain no business logic.

**Rationale**: Keeping business logic pure and framework-free makes it fast to test, cheap to
reason about, and ensures infrastructure choices (including the orchestration engine) remain
replaceable rather than load-bearing.

### II. Test-First Discipline (NON-NEGOTIABLE)

Tests are written before the implementation they describe, follow Red→Green→Refactor, and gate
every change.

- `domain` and `application` logic MUST be covered by fast, isolated unit tests that touch no
  infrastructure.
- Every adapter and every cross-boundary contract (persistence, orchestration, HTTP, events)
  MUST be covered by integration tests that exercise real backing services rather than mocks
  of those services (e.g., containerized Postgres and a real workflow-engine test environment).
- Concurrency and correctness guarantees (single-effective-decision, audit completeness,
  event/state consistency) MUST have explicit tests; they may not be assumed.
- This principle OVERRIDES any downstream template that treats tests as optional.

**Rationale**: A workflow engine's value rests on correctness under concurrency and on
trustworthy state transitions — properties that cannot be verified by inspection.

### III. Auditability & Traceability

The system is a trustworthy record of who did what, when.

- Every state transition, assignment, claim, release, and decision MUST produce an immutable,
  ordered audit record capturing the acting party, the change, and the timestamp.
- Audit records are append-only: they are never updated or deleted.
- A flow's complete lifecycle MUST be reconstructable from its audit history alone.
- Administrative actions (reassign, restart, reset) MUST be audited with the administrator's
  identity, with the same rigor as ordinary actions.

**Rationale**: Approvals are compliance-sensitive; the history must be complete and tamper-
evident to be relied upon for accountability and recovery.

### IV. Orchestration Behind a Port

Durable orchestration is a swappable capability, not an architectural assumption.

- Durable, long-running orchestration MUST be provided by a dedicated engine accessed only
  through a `WorkflowEngine` (or equivalent) port; the domain MUST remain unaware of the
  orchestration technology.
- Internal flow progression MUST use the orchestration engine's own mechanisms (e.g., signals
  and activities). Message brokers and event streams MUST NOT be used to drive internal flow
  steps.
- Brokers and emitted events are for integration with the outside world only.

**Rationale**: Adopting a proven engine avoids reinventing durability, while the port keeps the
engine replaceable and prevents orchestration concerns from leaking into business rules.

### V. Explicit Contracts & Consistency

Boundaries are explicit, and persisted state and emitted facts never disagree.

- A state change and any integration events it produces MUST be atomically consistent: if the
  state change commits, its events are eventually delivered exactly once; if it rolls back, no
  event is emitted (i.e., a transactional outbox or equivalent guarantee).
- Concurrent actions on the same unit of work MUST resolve to at most one effective outcome.
- Integration events MUST use a vendor-neutral envelope so consumers are not coupled to
  internal representations.
- Ports MUST expose intention-revealing contracts (commands, queries, and domain events), not
  leak storage or transport shapes.

**Rationale**: Distributed correctness and safe, decoupled integration depend on consistency
between what the system records and what it tells the world.

## Technology & Architecture Constraints

These constraints apply at the adapter and app layers only; the domain and application layers
remain free of all of them per Principle I.

- **Language & build**: Kotlin on the JVM, organized as a Gradle multi-module monorepo. Module
  boundaries enforce the inward-only dependency rule of Principle I.
- **REST / HTTP**: Ktor is the HTTP framework for REST APIs. Spring Boot MUST NOT be used for
  the REST layer.
- **Orchestration**: Temporal is the orchestration engine, accessed only via the port defined
  in Principle IV.
- **Persistence**: PostgreSQL for current state plus the append-only audit log, accessed with
  type-safe SQL (jOOQ). Reliable event publication uses a transactional outbox.
- **Integration events**: CloudEvents as the vendor-neutral envelope (Principle V).
- **Assignment model**: group/role candidate groups with a claim-to-act pattern; named external
  identity integration may be added later behind a port.
- **Topology**: deployables share the same `domain`/`application`/`persistence` modules and
  differ only in which driving adapters they activate.

A constraint in this section may be revised by amendment (see Governance) when a better-fitting
technology is justified, provided the Core Principles continue to hold.

## Development Workflow & Quality Gates

- **Spec-driven flow**: Work proceeds through the Spec Kit lifecycle — constitution → specify →
  (clarify) → plan → tasks → implement. Specifications stay business-facing and technology-
  agnostic; technology decisions live in the plan.
- **Feature branches**: Each feature is developed on its own branch under the `NNN-short-name`
  convention, with its artifacts under `specs/NNN-short-name/`.
- **Constitution gate**: Every implementation plan MUST pass the Constitution Check gate before
  design proceeds, and re-check after design. Violations MUST be recorded with justification in
  the plan's Complexity Tracking, or the design MUST change.
- **Definition of done**: All tests (unit and integration) pass; the inward-only dependency
  rule is not violated; new state-changing behavior has corresponding audit records and, where
  applicable, integration events.
- **Review**: Changes are reviewed for conformance to the Core Principles before merge.

## Governance

- **Authority**: This constitution supersedes other practices. Where guidance conflicts, the
  Core Principles win.
- **Amendments**: Proposed in a pull request that states the change, its rationale, and its
  impact on dependent templates and existing features. Amendments take effect when merged.
- **Versioning**: This constitution is versioned with semantic versioning:
  - MAJOR — backward-incompatible governance changes or removal/redefinition of a principle.
  - MINOR — a new principle or section, or materially expanded guidance.
  - PATCH — clarifications and wording fixes with no change in meaning.
- **Compliance review**: Plans and pull requests are checked against the Core Principles.
  Deviations require explicit, recorded justification; unjustified deviations block merge.

**Version**: 1.0.0 | **Ratified**: 2026-06-09 | **Last Amended**: 2026-06-09
