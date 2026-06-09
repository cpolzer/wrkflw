# How to: add a new flow definition

Flows are **data**, not code (constitution-backed: SC-007). Adding a new flow shape should not
require touching the engine.

## Steps

1. **Describe the flow as data** — states, transitions (with trigger + optional guard), terminal
   outcomes, the initiator group, and a candidate group per `HUMAN_TASK` state. See the
   `FlowDefinition` entity in `specs/001-document-approval-engine/data-model.md`.
2. **Add a seed/admin-loaded definition** — follow the pattern in
   `adapters/persistence-postgres/src/main/resources/db/migration/V2__seed_document_approval.sql`
   (new migration `V_n__seed_<key>.sql`, or load via an admin path when that lands).
3. **Validate** — the `FlowDefinition` validation rules enforce: exactly one initial state, every
   `HUMAN_TASK` state has a candidate group, all states reachable, terminal states have an outcome
   and no outgoing transitions.
4. **Test** — add an integration test that starts the new definition and runs it end to end
   *without engine code changes* (this is the SC-007 guarantee).

!!! warning "Do not add a Temporal workflow per flow type"
    A single generic workflow interprets the active definition. Per-flow workflow code would break
    the data-defined contract. See [Temporal integration](../explanation/temporal-integration.md).
