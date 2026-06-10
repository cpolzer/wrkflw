# Temporal Integration (Approach A)

Temporal provides durable orchestration; it is reached **only** through the `WorkflowEngine`
port, so the domain never sees a Temporal type.

## The division of responsibility

| Concern | Owner |
|---------|-------|
| Orchestration position (where a flow is, retries, timers, waiting) | **Temporal** |
| Human-task state, business data, audit, work-list queries | **PostgreSQL** |

A running flow instance ↔ one Temporal workflow execution (workflow id = flow instance id).

## How a human step works

1. The workflow reaches a `HUMAN_TASK` state.
2. It runs an **Activity** that calls the application path to create a `Task` row (PENDING) +
   `TASK_CREATED` audit.
3. The workflow **blocks on a Signal**.
4. A person claims and decides via REST. The application service validates domain rules
   (group membership, ownership, claimable status), commits the authoritative DB transition
   (decision + state advance + audit + outbox) in **one transaction**, then sends the Temporal
   **Signal**.
5. The workflow resumes, advances per the flow definition, and either creates the next task or
   reaches a terminal outcome.

## Why the DB transition commits *before* the signal

The database is the source of truth for task state. Committing first guarantees that:

- Work-list and status queries are always consistent with what users see.
- **Exactly one decision takes effect** — claim/decide are conditional updates guarded by
  `(status, version)`; a losing concurrent attempt updates zero rows and is refused *before* any
  signal is sent.
- Signals are idempotent at the workflow, so an at-least-once signal delivery is safe.

## What Temporal is *not* used for

- It does **not** store business/task state (that's PostgreSQL).
- Brokers/events do **not** drive progression — internal advancement is signals/activities only.
  CloudEvents are emitted for *external* integration via the outbox.

## Swappability

Because everything goes through `WorkflowEngine`, the engine could be replaced (e.g., with a
different runtime) without touching `domain`/`application`. Tests use Temporal's
`TestWorkflowEnvironment`/Testcontainers, independent of the production cluster choice
(self-hosted vs Temporal Cloud).
