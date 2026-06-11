# Research: UI Empty State & Submit Entry Point

**Branch**: `004-ui-empty-state-submit` | **Date**: 2026-06-11

## Summary

No technical unknowns exist for this feature. All decisions are grounded in existing codebase
patterns and confirmed Onyx component APIs. This document records the key findings for traceability.

---

## Decision 1 — Auth group check mechanism

**Decision**: Use `useAuthStore().isInGroup(group)` to test whether the user belongs to the
initiator group.

**Rationale**: `isInGroup` is already implemented in `ui/src/stores/auth.ts:38` and used by
`SubmitFlowView.vue` for the same guard. No new mechanism needed.

**Alternatives considered**: Direct `user.value?.groups.includes(group)` — identical semantics;
`isInGroup` is the preferred encapsulated form already in use.

---

## Decision 2 — Definitions source

**Decision**: Import `AVAILABLE_DEFINITIONS` from `ui/src/api/definitions.ts` to determine
whether any definitions are available and which `definitionId` to navigate to.

**Rationale**: `AVAILABLE_DEFINITIONS` is already exported and contains `DOCUMENT_APPROVAL_DEFINITION`
with `initiatorGroup: 'initiators'`. The single-definition assumption is documented in the spec
Assumptions section; no picker screen is needed.

**Alternatives considered**: Fetching from a backend `GET /definitions` endpoint — deferred by
the spec (hardcoded fallback is intentional until the endpoint ships).

---

## Decision 3 — Onyx component selection

**Decision**: Use `OnyxButton` for the header CTA and `OnyxEmpty` for the empty state.

**Rationale**: Both are present in `sit-onyx@^1.14.0` (confirmed via
`ui/node_modules/sit-onyx/dist/components/`). `OnyxButton` accepts a `link` prop that renders
it as a router-aware anchor — avoiding a manual `RouterLink` wrapper. `OnyxEmpty` provides
`default` (headline), `description`, and `buttons` slots that map directly to FR-004 and FR-005.

**OnyxButton key props**: `label` (required), `color` (`primary` | `neutral` | `danger`),
`mode` (`default` | `outline` | `plain`), `link` (renders as link).

**OnyxEmpty key slots**: `default` (headline text), `description` (explanatory paragraph),
`buttons` (CTA button area).

**Alternatives considered**: Plain `<button>` + `<RouterLink>` — already in use for other
elements; Onyx components give consistent visual language with the rest of the design system.

---

## Decision 4 — Onyx registration

**Decision**: Import `OnyxButton` and `OnyxEmpty` directly from `sit-onyx` inside the SFC
`<script setup>` block.

**Rationale**: Onyx is **not** globally registered in `ui/src/main.ts` (no `createOnyx()` call).
All existing components import Vue primitives directly. The same pattern applies here.

**Alternatives considered**: Global `app.use(createOnyx())` registration — would require changing
`main.ts` and is beyond the scope of this feature.

---

## Decision 5 — Navigation target

**Decision**: Navigate to `{ name: 'submit-flow', params: { definitionId: definition.definitionId } }`
using Vue Router's named route.

**Rationale**: The `submit-flow` route (`/submit/:definitionId`) is registered in
`ui/src/router/index.ts:28`. Using the named route avoids hard-coding the path string.

**Alternatives considered**: Hard-coding `/submit/document-approval` — fragile; named routes
survive path restructuring.
