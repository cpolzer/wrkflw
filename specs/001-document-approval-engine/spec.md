# Feature Specification: Document Approval Workflow Engine

**Feature Branch**: `001-document-approval-engine`

**Created**: 2026-06-09

**Status**: Draft

**Input**: User description: "Build wrkflw: a scalable, human-centric workflow engine, starting with the document-approval use case. Flows contain Tasks, Tasks contain Steps; Tasks and Steps belong to responsible persons (group/role based with claim-to-act). Every transition, assignment, and decision is auditable. Admin operations (reassign, restart flow, reset task) are a planned later phase."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Submit a document for approval (Priority: P1)

A document owner submits a document into the approval workflow. The system starts a new approval flow, records who submitted it and when, and routes the document to the first group responsible for reviewing it. The owner can see that the flow has started and where it currently sits.

**Why this priority**: Without the ability to start a flow and route work to the right people, nothing else has value. This is the entry point of the entire product and the smallest end-to-end slice that demonstrates the engine works.

**Independent Test**: Submit a document and confirm a new approval flow exists, is in the correct initial state, has an approval task waiting for the designated reviewer group, and the submission is recorded in the history.

**Acceptance Scenarios**:

1. **Given** a valid document and a defined approval flow, **When** the owner submits it for approval, **Then** a new flow is created in its initial state and a review task appears for the responsible reviewer group.
2. **Given** a flow has just started, **When** the owner views the flow, **Then** they can see its current state, the pending task, and which group is responsible.
3. **Given** a submission, **When** the flow is created, **Then** the submission event (who, what, when) is recorded in the immutable history.

---

### User Story 2 - Claim and decide on an approval task (Priority: P1)

A reviewer who belongs to the responsible group sees the pending approval task in their work list, claims it so others know they are handling it, reviews the document, and records an approve or reject decision with an optional comment. The flow advances based on the decision.

**Why this priority**: The act of a responsible person taking ownership and making an auditable decision is the core human-workflow behaviour the product exists to provide. Combined with Story 1 it forms a complete approve/reject loop — the MVP.

**Independent Test**: As a member of the responsible group, claim a pending task, submit an approval decision, and confirm the task closes, the flow moves to the next state, the decision and actor are recorded, and a second group member can no longer act on the now-claimed/closed task.

**Acceptance Scenarios**:

1. **Given** a pending task assigned to a group the reviewer belongs to, **When** the reviewer claims it, **Then** the task shows as owned by that reviewer and is removed from other members' actionable lists.
2. **Given** a claimed task, **When** the owning reviewer approves, **Then** the flow advances to the next state, the task is completed, and the decision is recorded with actor, outcome, comment, and timestamp.
3. **Given** a claimed task, **When** the owning reviewer rejects, **Then** the flow follows the rejection path defined for the flow and the rejection is recorded.
4. **Given** a task claimed by another person, **When** a different reviewer attempts to decide on it, **Then** the action is refused and the refusal does not alter flow state.
5. **Given** a person who is not in the responsible group, **When** they attempt to claim or decide, **Then** the action is refused.

---

### User Story 3 - Complete a multi-step approval flow end to end (Priority: P2)

A document moves through more than one approval stage (for example, a first-line review followed by a final sign-off), each owned by a different responsible group, until the flow reaches a terminal outcome (approved or rejected). Interested parties can observe the flow's progression and final result.

**Why this priority**: Demonstrates that the engine handles real multi-stage approval chains with handoffs between groups, not just a single gate. It builds directly on Stories 1 and 2 and proves the state-machine model end to end.

**Independent Test**: Run a document through a flow definition with at least two sequential approval stages owned by different groups, and confirm each stage routes to the correct group, decisions advance the flow correctly, and the flow reaches and records a terminal outcome.

**Acceptance Scenarios**:

1. **Given** a flow definition with two sequential approval stages, **When** the first stage is approved, **Then** a new task is created for the second stage's responsible group and the flow state reflects the second stage.
2. **Given** the flow is at the final stage, **When** the final approver approves, **Then** the flow reaches the terminal "approved" outcome and no further tasks are pending.
3. **Given** any stage, **When** an approver rejects, **Then** the flow reaches the rejection outcome defined for that flow and stops requesting further approvals.
4. **Given** a completed flow, **When** anyone views its history, **Then** the full ordered sequence of state transitions, assignments, and decisions is visible.

---

### User Story 4 - Observe work lists and flow status (Priority: P2)

A responsible person sees the list of tasks awaiting their group and the tasks they have personally claimed. A document owner or observer can look up any flow and see its current state, outstanding tasks, responsible groups, and history.

**Why this priority**: Querying is essential for humans to actually find and do their work and for stakeholders to track progress. It is request/response in nature and complements the command-side stories.

**Independent Test**: With several flows in varying states, query the work list for a given group and confirm only that group's actionable tasks appear; query a specific flow and confirm its state, pending tasks, and history are returned accurately.

**Acceptance Scenarios**:

1. **Given** multiple pending tasks across flows, **When** a reviewer requests their group's work list, **Then** only unclaimed tasks assigned to a group they belong to are returned.
2. **Given** a reviewer has claimed tasks, **When** they request their personal list, **Then** only tasks they own are returned.
3. **Given** a flow identifier, **When** anyone requests that flow's status, **Then** the current state, pending tasks, responsible groups, and ordered history are returned.

---

### User Story 5 - Notify external systems of approval outcomes (Priority: P3)

When meaningful things happen in a flow (flow started, task created, decision recorded, flow completed), the system reliably emits an event in a standard, vendor-neutral format so other systems can react, without coupling those systems to the engine's internals.

**Why this priority**: Integration value, but the core human-approval loop delivers value without it. It is additive and can follow once the command/query slices work.

**Independent Test**: Drive a flow through start, decision, and completion, and confirm a corresponding event is published for each meaningful state change exactly once, in the agreed standard envelope, even if the consumer is temporarily unavailable.

**Acceptance Scenarios**:

1. **Given** a flow reaches a meaningful state change, **When** the change is committed, **Then** a corresponding event is made available for publication in the standard envelope.
2. **Given** the event consumer/broker is temporarily unavailable, **When** it recovers, **Then** previously committed events are still delivered and none are lost.
3. **Given** a state change is rolled back, **When** the transaction fails, **Then** no event for that change is published.

---

### Edge Cases

- **Double decision / race**: Two members of the same group attempt to claim or decide the same task simultaneously — exactly one succeeds; the other is cleanly refused without corrupting flow state.
- **Decision on a non-current task**: A reviewer attempts to act on a task that has already been completed, superseded, or belongs to a flow that has reached a terminal state — the action is refused.
- **Unauthorized actor**: A person not in the responsible group attempts to claim/decide — refused and recorded as a refused attempt where appropriate.
- **Releasing a claim**: A reviewer claims a task but cannot complete it — they (or the system) can release the claim so another group member can pick it up.
- **Unknown / undefined flow definition**: A submission references a flow definition that does not exist or is malformed — submission is refused with a clear reason and no partial flow is created.
- **Rejection path**: A flow definition may route a rejection to a terminal "rejected" outcome or back to an earlier stage for rework — the engine follows whatever the definition specifies.
- **Duplicate submission**: The same document is submitted twice — each submission creates a distinct flow instance unless the flow definition specifies otherwise.
- **Orphaned tasks after terminal state**: Once a flow reaches a terminal outcome, no pending tasks for that flow remain actionable.

## Requirements *(mandatory)*

### Functional Requirements

**Flow definition & lifecycle**

- **FR-001**: System MUST allow approval flows to be described as data — a set of named states, the transitions between them (including the action/decision that triggers each transition), and the responsible group for each human task — so that new flow shapes can be introduced without bespoke code per flow.
- **FR-002**: System MUST start a new flow instance from a named flow definition when a document is submitted, placing it in the definition's initial state.
- **FR-003**: System MUST advance a flow strictly according to its definition's allowed transitions; it MUST refuse transitions that are not defined for the flow's current state.
- **FR-004**: System MUST support flows with multiple sequential human-approval stages, each routed to its own responsible group.
- **FR-005**: System MUST bring every flow to a defined terminal outcome (e.g., "approved" or "rejected") and stop requesting further human action once terminal.

**Tasks, steps & assignment**

- **FR-006**: System MUST represent work as Tasks that belong to a Flow, and Steps that belong to a Task, where Tasks and Steps each have a responsible party.
- **FR-007**: System MUST assign human tasks to a responsible group/role (candidate group), not solely to named individuals.
- **FR-008**: Users MUST be able to claim a task assigned to a group they belong to, marking it as owned by them and removing it from other members' actionable lists.
- **FR-009**: System MUST allow a claimed task to be released back to its group so another member can claim it.
- **FR-010**: System MUST permit only the current owner of a claimed task to record a decision on it, and only members of the responsible group to claim it.

**Decisions & progression**

- **FR-011**: Users MUST be able to record an approve or reject decision on a task they own, optionally with a comment.
- **FR-012**: System MUST advance the flow according to the recorded decision and the flow definition (approval path vs. rejection path).
- **FR-013**: System MUST guarantee that each pending task results in at most one effective decision, even under concurrent attempts.

**Auditability**

- **FR-014**: System MUST record an immutable, ordered history of every state transition, assignment, claim, release, and decision, including who acted, what changed, and when.
- **FR-015**: System MUST make a flow's full history retrievable for the lifetime of the flow's record.

**Queries**

- **FR-016**: Users MUST be able to retrieve the list of unclaimed tasks actionable by their group(s).
- **FR-017**: Users MUST be able to retrieve the list of tasks they personally own.
- **FR-018**: Users MUST be able to retrieve a specific flow's current state, pending tasks, responsible groups, and history.

**Integration events**

- **FR-019**: System MUST emit an event for each meaningful state change (flow started, task created, decision recorded, flow completed) in a standard, vendor-neutral envelope.
- **FR-020**: System MUST guarantee that emitted events are consistent with persisted state: not lost when a state change is committed, and not emitted when a state change is rolled back.

**Identity (initial scope)**

- **FR-021**: System MUST associate every action with an acting person and the group(s) they belong to. *(Initial scope stores minimal references to persons and groups; full external identity integration is out of scope for the first deliverable — see Assumptions.)*

**Administrative operations (planned later phase — specified, not in first deliverable)**

- **FR-022**: System MUST (in a later phase) allow an administrator to manually reassign a task to a different responsible group or person, recorded in the history.
- **FR-023**: System MUST (in a later phase) allow an administrator to restart a flow from its beginning, recorded in the history.
- **FR-024**: System MUST (in a later phase) allow an administrator to reset a task to a prior state, recorded in the history.
- **FR-025**: System MUST (in a later phase) restrict administrative operations to authorized administrators and record the administrator's identity for every such action.

### Key Entities *(include if feature involves data)*

- **Flow Definition**: A reusable, data-described template of an approval process — its states, the transitions between them and what triggers each, terminal outcomes, and the responsible group for each human stage. "Document approval" is the first definition.
- **Flow Instance**: A single running occurrence of a Flow Definition for a specific submitted document. Has a current state, an ordered history, and relates to its document and originator.
- **Task**: A unit of human work belonging to a Flow Instance, addressed to a responsible group, with a lifecycle (pending → claimed → completed/released). Carries the decision once made.
- **Step**: A sub-unit of work belonging to a Task, with its own responsible party. Supports tasks that decompose into smaller responsible actions.
- **Person (Actor)**: A reference to a human who can claim tasks and record decisions; belongs to one or more groups.
- **Group / Role**: A set of persons jointly responsible for tasks addressed to them (candidate group); the unit of assignment.
- **Decision**: The recorded outcome of acting on a task (approve/reject), with the acting person, optional comment, and timestamp.
- **History / Audit Entry**: An immutable, ordered record of a single significant occurrence in a flow (transition, assignment, claim, release, decision, admin action).
- **Document**: The subject of the approval flow, referenced by the flow instance. The engine governs the approval *process*; document content/storage is referenced rather than owned — see Assumptions.
- **Integration Event**: A vendor-neutral notification of a meaningful state change, made available to external systems.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A document owner can submit a document and see a running approval flow with the correct first task in under 1 minute, with no manual setup beyond selecting the flow.
- **SC-002**: A responsible reviewer can find, claim, and decide on a pending task in under 2 minutes from opening their work list.
- **SC-003**: 100% of state transitions, assignments, claims, releases, and decisions appear in the flow's history with actor and timestamp — verified by reconstructing any flow's full lifecycle from history alone.
- **SC-004**: Under concurrent attempts to act on the same task, exactly one decision takes effect 100% of the time, with no flow ever reaching an inconsistent state.
- **SC-005**: An unauthorized or out-of-group action is refused 100% of the time and never alters flow state.
- **SC-006**: For every meaningful state change that is committed, exactly one corresponding integration event is ultimately delivered (no loss, no event for rolled-back changes), even when the consumer is briefly unavailable.
- **SC-007**: A new approval flow shape (different stages/groups) can be introduced by providing a new flow definition, without changing the engine's core behaviour.
- **SC-008**: The system sustains the target concurrent flow and task volume for the first deliverable without degraded responsiveness on work-list and flow-status queries. *(Concrete numeric target to be set during planning; see Assumptions.)*

## Assumptions

- **Identity**: Full authentication and an external identity provider are out of scope for the first deliverable. The system stores minimal references to persons and the groups they belong to, sufficient for group-based assignment and claim-to-act; integration with an external OIDC/SSO provider is a later phase.
- **Claim/release**: Claim-to-act is the assignment model, and releasing a claim is supported so work can be re-picked-up by the group.
- **Document content**: The engine governs the approval *process* and references the document; storing/serving document content (file bytes, versions) is out of scope for the first deliverable and assumed handled by a referenced document store or identified by reference.
- **Admin operations**: Reassign, restart-flow, and reset-task are specified (FR-022–FR-025) but scheduled for a subsequent phase, not the first deliverable.
- **First flow definition**: "Document approval" is the only flow definition required for the first deliverable; the data-defined engine is built to accept others later.
- **Inbound interface style**: Human/UI interactions (submit, claim, decide, query) are request/response in nature; asynchronous inbound triggers from external systems are out of scope for the first deliverable.
- **Scale targets**: "Scalable" is a goal; concrete concurrency/volume/latency numbers (SC-008) will be fixed during planning based on expected document and reviewer population.
- **Notifications to humans**: Human-facing notifications (email/in-app alerts that a task is waiting) are out of scope for the first deliverable; integration events (FR-019) provide the hook for such notifications later.
- **Web frontend**: A user interface is a later phase; the first deliverable exposes its capabilities through a programmatic interface that a future frontend will consume.
