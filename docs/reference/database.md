# Reference: Database schema

PostgreSQL is the source of truth for human-task state, business data, and the audit log. The
authoritative model is **`specs/001-document-approval-engine/data-model.md`**; the live schema is
defined by the Flyway migrations in
`adapters/persistence-postgres/src/main/resources/db/migration/`.

## Tables (first deliverable)

| Table | Holds |
|-------|-------|
| `flow_definition` | data-defined templates (states/transitions/initiator+candidate groups), immutable per version |
| `flow_instance` | a running flow (current state, status, terminal outcome, document ref, submitter) |
| `task` | human work item (status, owner, **version** for optimistic concurrency) |
| `decision` | recorded approve/reject on a task |
| `person`, `group`, `group_membership` | minimal identity for assignment & authorization |
| `audit_entry` | **append-only** ordered history of every change |
| `outbox_event` | transactional outbox for CloudEvents publication |

## Notes

- **Append-only**: `audit_entry` is never updated or deleted (Principle III). A flow's lifecycle
  is reconstructable from its ordered entries (SC-003).
- **Concurrency**: claim/release/decide are conditional updates on `task (status, version)` —
  exactly one effective outcome (FR-013, SC-004).
- **jOOQ**: typed SQL is generated from the migrated schema during the build (T016).
- `step` is **not** in the schema — Steps are out of scope for the first deliverable.
