# Integration Event Contract (CloudEvents)

Outbound domain events are published as **CloudEvents 1.0** (FR-019), emitted via the
transactional outbox (FR-020). Delivery is at-least-once; consumers MUST dedupe on `id`.

## Envelope attributes

| CloudEvents attribute | Source |
|-----------------------|--------|
| `specversion` | `1.0` |
| `id` | `OutboxEvent.id` (UUID) — dedupe key |
| `source` | `/wrkflw/document-approval` (service/definition source URI) |
| `type` | event type (below) |
| `subject` | flow instance id |
| `time` | `OutboxEvent.occurredAt` (RFC 3339 UTC) |
| `datacontenttype` | `application/json` |
| `data` | type-specific payload (below) |

## Event types

| `type` | Emitted when | `data` fields |
|--------|--------------|---------------|
| `dev.wrkflw.flow.started` | A flow instance is created (US1) | `flowId`, `definitionKey`, `documentRef`, `submitterId`, `initialState` |
| `dev.wrkflw.task.created` | A human task is created for a stage | `flowId`, `taskId`, `stateName`, `candidateGroupId` |
| `dev.wrkflw.task.claimed` | A task is claimed | `flowId`, `taskId`, `ownerId` |
| `dev.wrkflw.task.released` | A claimed task is released | `flowId`, `taskId` |
| `dev.wrkflw.decision.recorded` | A decision is recorded (US2) | `flowId`, `taskId`, `outcome`, `actorId`, `comment?` |
| `dev.wrkflw.flow.completed` | A flow reaches a terminal outcome (US3) | `flowId`, `terminalOutcome` |

## Guarantees (SC-006)

- For every committed state change, exactly one corresponding outbox row is written in the same
  transaction; it is published exactly once *effectively* (at-least-once delivery + consumer
  dedupe on `id`).
- No event is published for a rolled-back state change (the outbox row rolls back with it).
- Events are for **external integration only**; they never drive internal flow progression (that
  is Temporal's role — Principle IV).
