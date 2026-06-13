# Implementation Plan: UI Empty State & Submit Entry Point

**Branch**: `004-ui-empty-state-submit` | **Date**: 2026-06-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/004-ui-empty-state-submit/spec.md`

## Summary

`MySubmissionsView.vue` currently shows a bare "No submissions yet." empty state with no path
to the submission form. This feature adds (a) a persistent "Submit new document" button in the
page header, always visible when the user is an initiator and definitions exist, and (b) a
richer empty state with an explanatory message and a secondary CTA. The submission form
(`SubmitFlowView`) already exists and works — only navigation is missing.

Single file change: `ui/src/views/MySubmissionsView.vue`. No new routes, no new API calls, no
new stores, no new components.

## Technical Context

**Language/Version**: TypeScript 5.x, Vue 3.5 (Composition API / `<script setup>`)

**Primary Dependencies**:
- `sit-onyx ^1.14.0` — `OnyxButton`, `OnyxEmpty`
- `pinia` — `useAuthStore` (already used throughout the app)
- `vue-router` — named route `submit-flow` (already registered)
- `ui/src/api/definitions.ts` — `AVAILABLE_DEFINITIONS` (already exported)

**Storage**: N/A (no persistence changes)

**Testing**: Vitest + `@vue/test-utils` for unit tests; existing e2e specs in
`ui/tests/e2e/` (no changes required to e2e suite)

**Target Platform**: Browser (Vite / Vue SPA)

**Project Type**: Frontend Vue SPA feature — modification to an existing view component

**Performance Goals**: No additional async work introduced; CTA render is synchronous and
computed from in-memory auth state and a constant definitions array.

**Constraints**: Onyx is NOT globally registered; components must be individually imported.
The hardcoded single-definition assumption is intentional per spec Assumptions — no picker.

**Scale/Scope**: One SFC modification (~50 lines changed); one new unit test file.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Hexagonal Architecture | ✅ N/A | Frontend SPA lives entirely outside the Kotlin hexagonal boundary. The `ui/` directory is a separate application that communicates with the backend only through the HTTP port. No domain/application layer changes. |
| II. Test-First Discipline | ✅ APPLIED | Unit tests for the new computed logic (canSubmit, cannotSubmitReason) are written before or alongside the implementation. UI tests validate all FR-specified states. |
| III. Auditability & Traceability | ✅ N/A | No new state transitions or backend writes. The submission itself already produces audit records in the backend — unchanged. |
| IV. Orchestration Behind a Port | ✅ N/A | No orchestration changes. |
| V. Explicit Contracts & Consistency | ✅ N/A | No new API surface. Navigation uses the existing named route and hardcoded definition ID. |
| VI. Local Validation Before Push | ✅ REQUIRED | `mise run ui:check` (lint + typecheck + unit tests + build) must pass before push. |

**Gate result: PASS.** No violations. No Complexity Tracking entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/004-ui-empty-state-submit/
├── spec.md              ✅ complete
├── research.md          ✅ Phase 0 output (this plan phase)
├── plan.md              ✅ This file
└── tasks.md             📋 Phase 2 output (/speckit-tasks — not yet created)
```

No `data-model.md` needed — no new entities. No `contracts/` needed — no new API surface.

### Source Code (files to change)

```text
ui/src/views/
└── MySubmissionsView.vue          ← primary change

ui/tests/unit/
└── MySubmissionsView.spec.ts      ← new test file (FR coverage)
```

## Implementation Design

### MySubmissionsView.vue — changes

#### 1. Imports (additions)

```ts
import { computed } from 'vue'               // already imported
import { useAuthStore } from '@/stores/auth'
import { AVAILABLE_DEFINITIONS } from '@/api/definitions'
import { OnyxButton } from 'sit-onyx'
import { OnyxEmpty } from 'sit-onyx'
```

#### 2. Computed values (additions in `<script setup>`)

```ts
const auth = useAuthStore()

// FR-001, FR-006, FR-007
const firstDefinition = computed(() => AVAILABLE_DEFINITIONS[0])
const canSubmit = computed(
  () =>
    !!firstDefinition.value &&
    auth.isInGroup(firstDefinition.value.initiatorGroup)
)
```

`AVAILABLE_DEFINITIONS` is a constant array — no reactivity needed beyond the computed.
`canSubmit` is falsy for non-initiators (FR-006) and when the array is empty (FR-007).

#### 3. Template — header area

Add a page header section above the table/empty state that is always visible when `canSubmit`:

```html
<header class="submissions-view__header">
  <h1>My Submissions</h1>
  <OnyxButton
    v-if="canSubmit"
    label="Submit new document"
    color="primary"
    :link="{ name: 'submit-flow', params: { definitionId: firstDefinition!.definitionId } }"
  />
</header>
```

FR-002: button in the header renders regardless of whether there are 0 or N submissions.

#### 4. Template — empty state

Replace the bare `<p v-else>` with `OnyxEmpty`:

```html
<OnyxEmpty v-else>
  You have not submitted any documents for approval yet.
  <template #description>
    <span v-if="canSubmit">Click "Submit new document" above to start your first approval request.</span>
    <span v-else-if="!firstDefinition">No submission types are currently configured.</span>
    <span v-else>You do not have permission to submit documents.</span>
  </template>
  <template v-if="canSubmit" #buttons>
    <OnyxButton
      label="Submit new document"
      color="primary"
      :link="{ name: 'submit-flow', params: { definitionId: firstDefinition!.definitionId } }"
    />
  </template>
</OnyxEmpty>
```

FR-004: replaces bare "No submissions yet."
FR-005: secondary CTA inside `OnyxEmpty #buttons` slot.
FR-007: description message adapts when no definitions exist.

#### 5. Style additions

```css
.submissions-view__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1.5rem;
}
.submissions-view__header h1 { margin-bottom: 0; }
```

The existing `h1` margin rule (`margin-bottom: 1.5rem`) moves to the header flex container.

### MySubmissionsView.spec.ts — test coverage

Unit tests using `@vue/test-utils` + `vitest`. Mock dependencies:
- `useAuthStore` → `vi.mock('@/stores/auth', ...)`
- `useFlows` → `vi.mock('@/composables/useFlows', ...)`
- `AVAILABLE_DEFINITIONS` → imported directly (no mock needed; constant)

**Test scenarios** (maps to FR requirements):

| Test | FR | Assertion |
|------|----|-----------|
| Initiator + definitions exist → CTA visible in header | FR-001, FR-002 | `OnyxButton[label="Submit new document"]` present |
| Initiator + 0 submissions → empty state has message + CTA | FR-004, FR-005 | `OnyxEmpty` rendered; secondary `OnyxButton` in `#buttons` slot |
| Initiator + N submissions → header CTA still present | FR-002 | `OnyxButton` in header present alongside table |
| Non-initiator → no CTA | FR-006 | No `OnyxButton` rendered |
| No definitions → no CTA + informative message | FR-007 | No `OnyxButton`; description contains "no submission types" |
| Loading state → CTA still visible | spec edge case | CTA not hidden during `isLoading` |

## Phases

### Phase 1: Tests + Implementation

Task execution order (all sequential — single file, single developer):

1. Write `MySubmissionsView.spec.ts` with all test scenarios (RED)
2. Update `MySubmissionsView.vue` — imports, computed values, header, empty state, styles (GREEN)
3. Run `mise run ui:check` to confirm lint, typecheck, tests, build all pass (REFACTOR / validate)

No setup phase needed. No foundational phase. Single user story, single increment.
