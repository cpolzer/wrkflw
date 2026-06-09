# Reference: Integration events

Outbound domain events are **CloudEvents 1.0**, emitted via the transactional outbox. The
authoritative catalog is **`specs/001-document-approval-engine/contracts/events.md`**.

## Event types

| `type` | Emitted when |
|--------|--------------|
| `dev.wrkflw.flow.started` | A flow instance is created (US1) |
| `dev.wrkflw.task.created` | A human task is created |
| `dev.wrkflw.task.claimed` | A task is claimed |
| `dev.wrkflw.task.released` | A claimed task is released |
| `dev.wrkflw.decision.recorded` | A decision is recorded (US2) |
| `dev.wrkflw.flow.completed` | A flow reaches a terminal outcome (US3) |

## Guarantees

- Exactly one outbox row per committed state change, written in the same transaction (no loss, no
  ghost events on rollback) — SC-006.
- At-least-once delivery; consumers **dedupe on the CloudEvents `id`**.
- Events are for **external integration only** — they never drive internal flow progression.

See [Temporal integration](../explanation/temporal-integration.md) for why events don't advance
flows.
