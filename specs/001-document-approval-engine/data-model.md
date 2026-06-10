# Phase 1 Data Model: Document Approval Workflow Engine

Source of truth is PostgreSQL. Types below are conceptual (domain) with their persisted shape
noted. `Step` is **out of scope** for the first deliverable (documented in spec) and is not
modeled here. All timestamps are UTC. IDs are UUIDs unless noted.

## Aggregates & entities

### FlowDefinition (data-defined template)
| Field | Type | Notes |
|-------|------|-------|
| id | UUID | PK |
| key | String | stable name, e.g. `document-approval`; unique per version |
| version | Int | definitions are immutable per version; new version = new row |
| initialState | String | name of the start state |
| initiatorGroupId | GroupId | only members may start a flow (FR-002) |
| states | JSONB | list of states: `{name, type: HUMAN_TASK\|TERMINAL, candidateGroupId?, terminalOutcome?}` |
| transitions | JSONB | list: `{from, trigger: APPROVE\|REJECT\|SUBMIT, to, guard?}` |
- **Validation**: exactly one initial state; every non-terminal HUMAN_TASK state has a candidateGroupId; every state reachable; terminal states have an outcome and no outgoing transitions; triggers valid for the state.
- **Document-approval definition** (seeded): `Submitted → (APPROVE) → FinalReview → (APPROVE) → Approved[terminal]`; any `REJECT` → `ReworkRequested` (returns to submitter) → resubmit `SUBMIT` → back to first review; abandon → `Rejected[terminal]`.

### FlowInstance
| Field | Type | Notes |
|-------|------|-------|
| id | UUID | PK; also the Temporal workflow id |
| definitionId | UUID | FK → FlowDefinition |
| definitionKey / version | String / Int | denormalized for query convenience |
| documentRef | String | reference to the document (content not owned) |
| submitterId | ActorId | who started it |
| currentState | String | current state name |
| status | Enum | `RUNNING`, `COMPLETED` |
| terminalOutcome | String? | set when status=COMPLETED (e.g. `APPROVED`, `REJECTED`) |
| createdAt / updatedAt | Instant | |
- **State transitions**: `RUNNING → COMPLETED` (on reaching a terminal state). `currentState` advances per the definition's transitions; advancement is committed in DB then signaled to Temporal (D6).

### Task (human work item)
| Field | Type | Notes |
|-------|------|-------|
| id | UUID | PK |
| flowInstanceId | UUID | FK → FlowInstance |
| stateName | String | the HUMAN_TASK state this task realizes |
| candidateGroupId | GroupId | responsible group (FR-007) |
| status | Enum | `PENDING` → `CLAIMED` → `COMPLETED`; `CLAIMED → PENDING` on release |
| ownerId | ActorId? | set when CLAIMED, cleared on release |
| version | Int | optimistic concurrency (D6) |
| createdAt / claimedAt / completedAt | Instant? | |
- **Invariants**: only one non-completed task per flow at a time (single-approver-per-stage, first deliverable); a task in `COMPLETED` is immutable; claim requires `status=PENDING` and actor ∈ candidateGroup; decide requires `status=CLAIMED` and `ownerId=actor`.
- **Concurrency**: claim/release/decide are conditional updates guarded by `(status, version)`; 0 rows ⇒ refuse (FR-013, SC-004).

### Decision (value, recorded on a task)
| Field | Type | Notes |
|-------|------|-------|
| taskId | UUID | FK → Task (one effective decision per task) |
| outcome | Enum | `APPROVE`, `REJECT` |
| actorId | ActorId | the deciding owner |
| comment | String? | optional |
| decidedAt | Instant | |

### Person (Actor reference) & Group
| Field | Type | Notes |
|-------|------|-------|
| Person.id | ActorId | minimal reference (FR-021); display name |
| Group.id | GroupId | candidate/initiator group; name |
| GroupMembership | (personId, groupId) | many-to-many; drives assignment & authorization |
- Minimal for first deliverable; replaced/augmented by external IdP later behind `ActorContext`.

### AuditEntry (append-only — Principle III, FR-014/015)
| Field | Type | Notes |
|-------|------|-------|
| id | BIGSERIAL | monotonic order |
| flowInstanceId | UUID | FK |
| taskId | UUID? | when applicable |
| type | Enum | `FLOW_STARTED`, `TASK_CREATED`, `TASK_CLAIMED`, `TASK_RELEASED`, `DECISION_RECORDED`, `STATE_TRANSITIONED`, `FLOW_COMPLETED` (+ later: `TASK_REASSIGNED`, `FLOW_RESTARTED`, `TASK_RESET`) |
| actorId | ActorId? | who acted (null for system) |
| payload | JSONB | type-specific detail (from/to state, outcome, comment, group, …) |
| occurredAt | Instant | |
- **Invariant**: insert-only; never updated/deleted. A flow's full lifecycle is reconstructable from its ordered entries (SC-003).

### OutboxEvent (transactional outbox — Principle V, FR-019/020)
| Field | Type | Notes |
|-------|------|-------|
| id | UUID | becomes the CloudEvents `id` (dedupe key) |
| flowInstanceId | UUID | becomes CloudEvents `subject` |
| type | String | CloudEvents `type`, e.g. `dev.wrkflw.flow.started` |
| data | JSONB | event payload |
| occurredAt | Instant | CloudEvents `time` |
| status | Enum | `PENDING` → `DISPATCHED` |
| dispatchedAt | Instant? | |
- Written in the **same transaction** as the state change it describes; published by the api-service poller; never published for rolled-back changes.

## Relationships

```
FlowDefinition 1───* FlowInstance 1───* Task 1───0..1 Decision
FlowInstance 1───* AuditEntry
(state change) 1───1 OutboxEvent
Person *───* Group (GroupMembership);  Task.candidateGroupId → Group;  FlowDefinition.initiatorGroupId → Group
```

## Mapping to functional requirements

| Requirement | Where enforced |
|-------------|----------------|
| FR-001/SC-007 data-defined flows | FlowDefinition.states/transitions (JSONB), generic interpreter |
| FR-002 initiator gating | FlowDefinition.initiatorGroupId + membership check in SubmitDocument |
| FR-003/005/012 transitions & terminal | FlowInstance.currentState/status + definition transitions |
| FR-007/008/009/010 candidate group, claim/release/owner-only | Task.candidateGroupId/status/ownerId/version |
| FR-011 decision | Decision + DECISION_RECORDED audit |
| FR-013/SC-004 single effective decision | Task conditional updates on (status, version) |
| FR-014/015/SC-003 audit | AuditEntry append-only |
| FR-019/020/SC-006 events | OutboxEvent + CloudEvents publisher |
