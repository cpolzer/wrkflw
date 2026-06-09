---
description: "Task list for Document Approval Workflow Engine implementation"
---

# Tasks: Document Approval Workflow Engine

**Input**: Design documents from `/specs/001-document-approval-engine/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Test tasks are **INCLUDED and REQUIRED** — Constitution Principle II (Test-First,
NON-NEGOTIABLE) overrides the template default. Write tests first; they MUST fail before
implementation (red→green→refactor).

**Organization**: Tasks are grouped by user story. Module paths follow plan.md
(`dev.wrkflw` package root). Inner modules (`domain`, `application`) stay framework-free.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files/modules, no incomplete dependencies)
- **[Story]**: US1–US5 for user-story phases; Setup/Foundational/Polish carry no story label

## Path Conventions (from plan.md)

- `domain/src/{main,test}/kotlin/dev/wrkflw/domain/…`
- `application/src/{main,test}/kotlin/dev/wrkflw/application/…`
- `adapters/{persistence-postgres,temporal,rest-api,eventing-cloudevents}/src/…`
- `apps/{api-service,worker-service}/src/…`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Monorepo skeleton and build tooling.

- [x] T001 Initialize Gradle multi-module monorepo: `settings.gradle.kts` (include `domain`, `application`, `adapters:persistence-postgres`, `adapters:temporal`, `adapters:rest-api`, `adapters:eventing-cloudevents`, `apps:api-service`, `apps:worker-service`), root `build.gradle.kts`, and `gradle/libs.versions.toml` (Kotlin 2.0+, JDK 21, Ktor, Temporal Java SDK, jOOQ, Flyway, CloudEvents, Koin, JUnit 5, Testcontainers)
- [x] T002 [P] Create Gradle convention plugins in `build-logic/` (`kotlin-jvm`, `testing`, `ktor-app`)
- [ ] T003 [P] Configure ktlint + detekt and formatting via `build-logic/`
- [x] T004 [P] Add `docker-compose.yml` at repo root (PostgreSQL, Temporal dev server, Temporal UI)
- [x] T005 Create all module skeletons with correct inter-module dependencies (`domain`→none; `application`→`domain`; adapters→`application`+`domain`+infra libs; apps→adapters) so an empty `./gradlew build` is green

**Checkpoint**: Repo builds; module graph in place.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared domain core, ports, persistence/Temporal/Ktor bootstraps, audit. **No user story can begin until this is complete.**

**⚠️ CRITICAL**: Blocks all user stories.

- [x] T006 [P] Domain value objects & IDs in `domain/src/main/kotlin/dev/wrkflw/domain/identity/` (`ActorId`, `GroupId`, `FlowDefinitionKey`, `FlowInstanceId`, `TaskId`)
- [x] T007 [P] Domain `FlowDefinition` model (`State`, `Transition`, triggers `SUBMIT|APPROVE|REJECT`, terminal outcomes) + validation in `domain/src/main/kotlin/dev/wrkflw/domain/flow/`
- [x] T008 Domain flow interpreter (given current state + trigger → next state or terminal) in `domain/src/main/kotlin/dev/wrkflw/domain/flow/FlowInterpreter.kt` (depends on T007)
- [x] T009 [P] Domain `FlowInstance` aggregate (currentState, status, terminalOutcome) in `domain/src/main/kotlin/dev/wrkflw/domain/flow/FlowInstance.kt`
- [x] T010 [P] Domain `Task` aggregate (status PENDING/CLAIMED/COMPLETED, ownerId, version; claim/release/decide invariants) in `domain/src/main/kotlin/dev/wrkflw/domain/task/Task.kt`
- [x] T011 [P] Domain `AuditEntry` + `AuditEventType` in `domain/src/main/kotlin/dev/wrkflw/domain/audit/`
- [x] T012 [P] Domain `DomainEvent` hierarchy (FlowStarted, TaskCreated, TaskClaimed, TaskReleased, DecisionRecorded, FlowCompleted) in `domain/src/main/kotlin/dev/wrkflw/domain/event/`
- [x] T013 Domain ports in `domain/src/main/kotlin/dev/wrkflw/domain/port/` (`FlowDefinitionRepository`, `FlowInstanceRepository`, `TaskRepository`, `AuditLog`, `WorkflowEngine`, `DomainEventPublisher`, `Clock`, `ActorContext`) (depends on T006–T012)
- [ ] T014 Architecture boundary test (Konsist or ArchUnit) asserting `domain` & `application` have **no** dependency on Ktor/Temporal/jOOQ/JDBC/Koin, in `application/src/test/kotlin/dev/wrkflw/arch/BoundaryTest.kt`
- [x] T015 [P] Flyway baseline migration (all tables: flow_definition, flow_instance, task, decision, person, group, group_membership, audit_entry, outbox_event) in `adapters/persistence-postgres/src/main/resources/db/migration/V1__baseline.sql`
- [x] T016 Wire jOOQ code generation to the Flyway-migrated schema (Testcontainers-backed) in `build-logic/` + `adapters:persistence-postgres/build.gradle.kts` (depends on T015)
- [x] T017 [P] Seed migration for the `document-approval` `FlowDefinition` (Submitted→FinalReview→Approved; REJECT→ReworkRequested→resubmit; abandon→Rejected) in `adapters/persistence-postgres/src/main/resources/db/migration/V2__seed_document_approval.sql`
- [x] T018 Persistence base: `DSLContext` provider + `TransactionRunner`/UnitOfWork in `adapters/persistence-postgres/src/main/kotlin/dev/wrkflw/persistence/` (depends on T016)
- [x] T019 [P] `AuditLog` port jOOQ impl (append-only insert) in `adapters/persistence-postgres/src/main/kotlin/dev/wrkflw/persistence/AuditLogPostgres.kt`
- [x] T020 Temporal base: worker-service bootstrap + generic flow workflow skeleton + activity interface in `adapters/temporal/src/main/kotlin/dev/wrkflw/temporal/` and `apps/worker-service/`
- [x] T021 `WorkflowEngine` port impl (start workflow, send signal) via Temporal client in `adapters/temporal/src/main/kotlin/dev/wrkflw/temporal/TemporalWorkflowEngine.kt` (depends on T013, T020)
- [x] T022 Ktor base: api-service bootstrap (server, routing skeleton, content negotiation, error→HTTP mapping) in `apps/api-service/` + `adapters/rest-api/`
- [x] T023 `ActorContext` adapter from `X-Actor-Id`/`X-Actor-Groups` headers in `adapters/rest-api/src/main/kotlin/dev/wrkflw/rest/HeaderActorContext.kt`
- [ ] T024 Koin wiring modules for both apps in `apps/api-service/` and `apps/worker-service/` (composition roots only)

**Checkpoint**: Foundation ready — user stories can begin.

---

## Phase 3: User Story 1 - Submit a document for approval (Priority: P1) 🎯 MVP

**Goal**: A member of the initiator group submits a document; a flow starts and the first review task appears for the responsible group, all recorded in history.

**Independent Test**: POST a submission → flow `RUNNING`, one `PENDING` task for the reviewer group, `FLOW_STARTED` + `TASK_CREATED` in history.

### Tests for User Story 1 (write first, must fail) ⚠️

- [ ] T025 [P] [US1] Contract test for `POST /flows` (per contracts/openapi.yaml) in `adapters/rest-api/src/test/kotlin/dev/wrkflw/rest/SubmitFlowContractTest.kt`
- [ ] T026 [P] [US1] Integration test (Testcontainers Postgres + Temporal test env): submit → flow created, first task PENDING for candidate group, audit entries written, in `apps/api-service/src/test/kotlin/dev/wrkflw/SubmitDocumentE2ETest.kt`
- [ ] T027 [P] [US1] Unit test for `SubmitDocument` use case: initiator-group authorization + unknown/invalid definition refusal, in `application/src/test/kotlin/dev/wrkflw/application/SubmitDocumentTest.kt`

### Implementation for User Story 1

- [ ] T028 [US1] `SubmitDocument` command + application service (validate initiator-group membership via ActorContext, create FlowInstance, start workflow via WorkflowEngine, write FLOW_STARTED audit) in `application/src/main/kotlin/dev/wrkflw/application/command/SubmitDocument.kt`
- [ ] T029 [US1] `FlowDefinitionRepository` + `FlowInstanceRepository` jOOQ impls in `adapters/persistence-postgres/src/main/kotlin/dev/wrkflw/persistence/` (depends on T018)
- [ ] T030 [US1] `TaskRepository` jOOQ impl (create + read) in `adapters/persistence-postgres/src/main/kotlin/dev/wrkflw/persistence/TaskRepositoryPostgres.kt`
- [ ] T031 [US1] Temporal activity `CreateHumanTask` (writes Task row + TASK_CREATED audit) invoked by the workflow on entering a HUMAN_TASK state, in `adapters/temporal/src/main/kotlin/dev/wrkflw/temporal/activity/`
- [ ] T032 [US1] `POST /flows` route + request/response DTOs + mapper in `adapters/rest-api/src/main/kotlin/dev/wrkflw/rest/FlowRoutes.kt`
- [ ] T033 [US1] Wire start-flow → workflow → CreateHumanTask end to end; make T025–T027 pass

**Checkpoint**: US1 fully functional and independently testable (MVP).

---

## Phase 4: User Story 2 - Claim and decide on an approval task (Priority: P1)

**Goal**: A group member claims a task, optionally releases it, and records an approve/reject decision that advances the flow; only the owner may decide and only one decision takes effect.

**Independent Test**: Claim then approve → task COMPLETED, flow advances, decision recorded; a second reviewer is refused (409); two simultaneous claims → exactly one succeeds.

### Tests for User Story 2 (write first, must fail) ⚠️

- [ ] T034 [P] [US2] Contract tests for `POST /tasks/{id}/claim`, `/release`, `/decision` in `adapters/rest-api/src/test/kotlin/dev/wrkflw/rest/TaskActionContractTest.kt`
- [ ] T035 [P] [US2] Integration test: claim → approve advances flow; non-owner decide → 403; decide on completed → 409, in `apps/api-service/src/test/kotlin/dev/wrkflw/ClaimDecideE2ETest.kt`
- [ ] T036 [P] [US2] Unit tests for ClaimTask/ReleaseTask/SubmitDecision invariants & refusals in `application/src/test/kotlin/dev/wrkflw/application/TaskActionsTest.kt`
- [ ] T037 [P] [US2] Concurrency test: two simultaneous claims on one task → exactly one effective (SC-004), in `adapters/persistence-postgres/src/test/kotlin/dev/wrkflw/persistence/TaskConcurrencyTest.kt`

### Implementation for User Story 2

- [ ] T038 [US2] `ClaimTask` use case (candidate-group membership check, optimistic transition) in `application/src/main/kotlin/dev/wrkflw/application/command/ClaimTask.kt`
- [ ] T039 [US2] `ReleaseTask` use case (owner-only, CLAIMED→PENDING) in `application/src/main/kotlin/dev/wrkflw/application/command/ReleaseTask.kt`
- [ ] T040 [US2] `SubmitDecision` use case (owner-only, record Decision, advance via FlowInterpreter, write DECISION_RECORDED + STATE_TRANSITIONED audit, signal Temporal) in `application/src/main/kotlin/dev/wrkflw/application/command/SubmitDecision.kt`
- [ ] T041 [US2] `TaskRepository` conditional-update methods (claim/release/decide guarded by `(status, version)`) in `adapters/persistence-postgres/src/main/kotlin/dev/wrkflw/persistence/TaskRepositoryPostgres.kt` (depends on T030)
- [ ] T042 [US2] `Decision` persistence in `adapters/persistence-postgres/src/main/kotlin/dev/wrkflw/persistence/DecisionRepositoryPostgres.kt`
- [ ] T043 [US2] Temporal workflow signal handler to advance state / trigger next CreateHumanTask in `adapters/temporal/src/main/kotlin/dev/wrkflw/temporal/DocumentApprovalWorkflow.kt`
- [ ] T044 [US2] `claim`/`release`/`decision` routes + DTOs in `adapters/rest-api/src/main/kotlin/dev/wrkflw/rest/TaskRoutes.kt`
- [ ] T045 [US2] Wire decision → DB transition (commit) → Temporal signal; make T034–T037 pass

**Checkpoint**: US1 + US2 = complete single-stage approve/reject loop.

---

## Phase 5: User Story 3 - Complete a multi-stage flow end to end (Priority: P2)

**Goal**: A document progresses through multiple approval stages to a terminal outcome; rejection returns it to the submitter for rework and re-submission.

**Independent Test**: Two-stage definition → approve twice reaches `Approved` terminal; a reject → `ReworkRequested` then resubmit re-enters the first stage.

### Tests for User Story 3 (write first, must fail) ⚠️

- [ ] T046 [P] [US3] Integration test: two-stage approval to `Approved` terminal; reject→ReworkRequested→resubmit cycle, in `apps/api-service/src/test/kotlin/dev/wrkflw/MultiStageFlowE2ETest.kt`
- [ ] T047 [P] [US3] Unit test: FlowInterpreter multi-stage transitions + rejection-to-submitter + terminal detection in `domain/src/test/kotlin/dev/wrkflw/domain/flow/FlowInterpreterMultiStageTest.kt`

### Implementation for User Story 3

- [ ] T048 [US3] Workflow interpreter loop: on advance, enter next HUMAN_TASK (create task) or reach terminal; handle `SUBMIT` (resubmit) trigger in `adapters/temporal/src/main/kotlin/dev/wrkflw/temporal/DocumentApprovalWorkflow.kt` (depends on T043)
- [ ] T049 [US3] FlowInstance completion (status COMPLETED + terminalOutcome) + FLOW_COMPLETED audit in `application/src/main/kotlin/dev/wrkflw/application/command/SubmitDecision.kt` and `FlowInstanceRepository`
- [ ] T050 [US3] Verify seeded `document-approval` definition exercises both stages + rework path; make T046–T047 pass

**Checkpoint**: Full multi-stage approval, including rejection rework loop.

---

## Phase 6: User Story 4 - Observe work lists and flow status (Priority: P2)

**Goal**: Group members see their group queue and personal queue; anyone can look up a flow's state, pending tasks, responsible groups, and history.

**Independent Test**: With several flows, group worklist returns only the caller's group's unclaimed tasks; `/mine` returns owned tasks; `/flows/{id}` returns state + pending + ordered history.

### Tests for User Story 4 (write first, must fail) ⚠️

- [ ] T051 [P] [US4] Contract tests for `GET /worklists/group`, `/worklists/mine`, `/flows/{id}` in `adapters/rest-api/src/test/kotlin/dev/wrkflw/rest/QueryContractTest.kt`
- [ ] T052 [P] [US4] Integration test: group/mine filtering + flow status with history accuracy in `apps/api-service/src/test/kotlin/dev/wrkflw/WorkListAndStatusE2ETest.kt`
- [ ] T053 [P] [US4] Unit tests for query services in `application/src/test/kotlin/dev/wrkflw/application/QueriesTest.kt`

### Implementation for User Story 4

- [ ] T054 [US4] Query services `GroupWorkList`, `MyTasks`, `FlowStatus` (with history) in `application/src/main/kotlin/dev/wrkflw/application/query/`
- [ ] T055 [US4] jOOQ query impls (worklist by group membership, my tasks by owner, flow status + audit history) in `adapters/persistence-postgres/src/main/kotlin/dev/wrkflw/persistence/QueriesPostgres.kt`
- [ ] T056 [US4] `GET` routes + DTOs (`worklists/group`, `worklists/mine`, `flows/{id}`) in `adapters/rest-api/src/main/kotlin/dev/wrkflw/rest/QueryRoutes.kt`
- [ ] T057 [US4] Make T051–T053 pass

**Checkpoint**: Humans can find their work and stakeholders can track flows.

---

## Phase 7: User Story 5 - Notify external systems of approval outcomes (Priority: P3)

**Goal**: Each meaningful state change reliably emits a CloudEvent via the transactional outbox.

**Independent Test**: Drive a flow → each step writes one outbox row in the same transaction and the publisher emits the matching CloudEvent exactly once; a rolled-back change emits nothing.

### Tests for User Story 5 (write first, must fail) ⚠️

- [ ] T058 [P] [US5] Integration test: state change writes one outbox row in same tx; publisher emits matching CloudEvent; rollback → no event (SC-006), in `adapters/eventing-cloudevents/src/test/kotlin/dev/wrkflw/eventing/OutboxConsistencyTest.kt`
- [ ] T059 [P] [US5] Unit test: DomainEvent → CloudEvents envelope mapping (per contracts/events.md) in `adapters/eventing-cloudevents/src/test/kotlin/dev/wrkflw/eventing/CloudEventMappingTest.kt`

### Implementation for User Story 5

- [ ] T060 [US5] Write `OutboxEvent` row in the same transaction as each state change in SubmitDocument/ClaimTask/ReleaseTask/SubmitDecision (`application/src/main/kotlin/dev/wrkflw/application/command/`)
- [ ] T061 [US5] `DomainEventPublisher` impl → CloudEvents mapping in `adapters/eventing-cloudevents/src/main/kotlin/dev/wrkflw/eventing/CloudEventsPublisher.kt`
- [ ] T062 [US5] Outbox poller in api-service (publish PENDING → mark DISPATCHED, at-least-once) in `apps/api-service/src/main/kotlin/dev/wrkflw/OutboxPublisherRunner.kt`
- [ ] T063 [US5] Wire all six event types (flow.started, task.created, task.claimed, task.released, decision.recorded, flow.completed) into the commands; make T058–T059 pass

**Checkpoint**: External integration events flowing reliably.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T064 [P] Execute `quickstart.md` end to end manually and fix gaps
- [ ] T065 [P] Performance smoke test: ~1,000 active flows / ~100 concurrent reviewers, worklist & flow-status p95 < 1s (SC-008) in `apps/api-service/src/test/kotlin/dev/wrkflw/PerfSmokeTest.kt`
- [ ] T066 [P] Observability: structured logging + basic metrics across adapters (apps + adapters)
- [ ] T067 [P] `README.md` + module docs
- [ ] T068 Code cleanup / simplification pass across changed modules
- [ ] T069 [P] Wire Dokka into the Gradle build (`:dokkaHtmlMultiModule`) and publish output into the docs site under `docs/reference/api/`; link from `docs/reference/api.md`
- [ ] T070 [P] CI: build the MkDocs site (`pip install -r docs/requirements.txt && mkdocs build --strict`) and run Dokka; publish to GitHub Pages on merge to `main`

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (P1)** → no deps.
- **Foundational (P2)** → depends on Setup; **blocks all user stories**.
- **US1 (P3)** → depends on Foundational. MVP.
- **US2 (P4)** → depends on Foundational; builds on US1 task creation.
- **US3 (P5)** → depends on US2 (signal/advance machinery).
- **US4 (P6)** → depends on Foundational (+ data produced by US1–US3 for meaningful results); independently testable with seeded data.
- **US5 (P7)** → depends on Foundational; augments US1–US3 command transactions. Additive.
- **Polish (P8)** → after desired stories complete.

### Within each user story

- Tests written first and FAIL → models → repositories/services → routes → integration wiring.

### Parallel opportunities

- Setup: T002, T003, T004 in parallel.
- Foundational: T006, T007, T009, T010, T011, T012 (distinct domain files) in parallel; T015/T017 (migrations) in parallel.
- Each story's test tasks ([P]) in parallel before its implementation.
- After Foundational, US1 and (the query-only parts of) US4 and (mapping unit test of) US5 can be staffed in parallel; US2→US3 are sequential.

---

## Implementation Strategy

### MVP first

1. Phase 1 Setup → 2. Phase 2 Foundational → 3. Phase 3 US1 → **STOP & validate** (submit→task→history) → demo.

### Incremental delivery

US1 (submit) → US2 (claim/decide loop) → US3 (multi-stage + rework) → US4 (queries) → US5 (events). Each is an independently testable, demoable increment.

### Constitution checkpoints (per Principle II & I)

- No implementation task starts before its story's tests exist and fail.
- The boundary test (T014) must stay green for the life of the project.
- Every command task writes an audit entry (Principle III); event emission (US5) and state are atomically consistent via the outbox (Principle V).
