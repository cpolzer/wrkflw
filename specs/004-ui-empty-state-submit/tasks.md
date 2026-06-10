# Tasks: UI Empty State & Submit Entry Point

**Input**: Design documents from `specs/004-ui-empty-state-submit/`

**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅

**Files changed**:
- `ui/src/views/MySubmissionsView.vue` — primary change
- `ui/tests/unit/MySubmissionsView.spec.ts` — new (Test-First per Principle II)

**Note**: No Setup or Foundational phases needed — all infrastructure, routes, stores, and
definitions already exist. All tasks target a single existing view file plus one new test file.

---

## Phase 1: User Story 1 — Submit CTA (Priority: P1) 🎯 MVP

**Goal**: A visible "Submit new document" button appears in the page header whenever the
authenticated user is an initiator and at least one workflow definition is available. The
button navigates directly to the submission form in one click.

**Independent Test**: Open the app as an initiator → see the CTA button in the "My Submissions"
header → click it → `SubmitFlowView` loads with the document-approval form.

### Tests for User Story 1 (Principle II: write first, ensure RED before implementation)

- [X] T001 [US1] Create `ui/tests/unit/MySubmissionsView.spec.ts` with vitest + @vue/test-utils boilerplate, mocking `useAuthStore`, `useFlows`, and stubbing Onyx components
- [X] T002 [US1] Write test: initiator user with N > 0 existing submissions → `OnyxButton[label="Submit new document"]` rendered in header area (and no `OnyxEmpty` visible) in `ui/tests/unit/MySubmissionsView.spec.ts`
- [X] T003 [US1] Write test: initiator user with N existing submissions → header CTA `OnyxButton` still rendered (FR-002) in `ui/tests/unit/MySubmissionsView.spec.ts`
- [X] T004 [US1] Write test: non-initiator user → no `OnyxButton` CTA rendered at all (FR-006) in `ui/tests/unit/MySubmissionsView.spec.ts`
- [X] T004b [US1] Write test: user in both initiator and reviewer groups → header CTA `OnyxButton` still rendered (spec edge case: dual-group membership does not suppress CTA) in `ui/tests/unit/MySubmissionsView.spec.ts`
- [X] T005 [US1] Write test: loading state active → header CTA `OnyxButton` still visible (spec edge case) in `ui/tests/unit/MySubmissionsView.spec.ts`

### Implementation for User Story 1

- [X] T006 [US1] Add imports to `ui/src/views/MySubmissionsView.vue`: `useAuthStore` from `@/stores/auth`, `AVAILABLE_DEFINITIONS` from `@/api/definitions`, `OnyxButton` from `sit-onyx`
- [X] T007 [US1] Add computed values to `<script setup>` in `ui/src/views/MySubmissionsView.vue`: `auth = useAuthStore()`, `firstDefinition = computed(() => AVAILABLE_DEFINITIONS[0])`, `canSubmit = computed(() => !!firstDefinition.value && auth.isInGroup(firstDefinition.value.initiatorGroup))`
- [X] T008 [US1] Replace `<h1>My Submissions</h1>` with a `<header class="submissions-view__header">` flex container containing the `<h1>` and a conditional `<OnyxButton v-if="canSubmit" label="Submit new document" color="primary" :link="submitLink" />` in `ui/src/views/MySubmissionsView.vue`
- [X] T009 [US1] Add `.submissions-view__header` CSS rule (`display: flex; align-items: center; justify-content: space-between; margin-bottom: 1.5rem;`) and remove the existing `h1` margin rule in `ui/src/views/MySubmissionsView.vue`

**Checkpoint**: Run `mise run ui:test` — T001–T005 must now pass GREEN. The CTA is reachable in one click from "My Submissions".

---

## Phase 2: User Story 2 — Welcoming Empty State (Priority: P2)

**Goal**: When the user has no submissions, a descriptive message explains the section's
purpose and a secondary CTA guides them toward their first submission — not a bare "No
submissions yet." dead end.

**Independent Test**: Log in as a first-time initiator with zero flows → "My Submissions"
shows `OnyxEmpty` with an explanatory message and a "Submit new document" button inside it.

### Tests for User Story 2 (Principle II: write first, ensure RED before implementation)

- [X] T010 [US2] Write test: initiator with 0 submissions → `OnyxEmpty` rendered with headline text containing "not submitted" and a secondary `OnyxButton` in `ui/tests/unit/MySubmissionsView.spec.ts`
- [X] T011 [US2] Write test: no definitions available → `OnyxEmpty` rendered, no `OnyxButton`, description contains "no submission types" (FR-007) in `ui/tests/unit/MySubmissionsView.spec.ts`
- [X] T012 [US2] Write test: non-initiator with 0 submissions → `OnyxEmpty` rendered, no `OnyxButton`, description contains "permission" in `ui/tests/unit/MySubmissionsView.spec.ts`

### Implementation for User Story 2

- [X] T013 [US2] Add `OnyxEmpty` to the imports from `sit-onyx` in `ui/src/views/MySubmissionsView.vue`
- [X] T014 [US2] Replace `<p v-else class="submissions-view__empty">No submissions yet.</p>` with `<OnyxEmpty v-else>` block in `ui/src/views/MySubmissionsView.vue`: default slot text "You have not submitted any documents for approval yet.", `#description` slot with three conditional `<span>` branches (canSubmit / no definitions / no permission), `#buttons` slot with conditional `OnyxButton` (same props as header CTA, only when `canSubmit`)
- [X] T015 [US2] Remove the now-unused `.submissions-view__empty` CSS rule from `ui/src/views/MySubmissionsView.vue`

**Checkpoint**: Run `mise run ui:test` — T010–T012 must now pass GREEN. Empty state is informative and guides users.

---

## Phase 3: Polish & Validation

**Purpose**: Full quality gate before push.

- [X] T016 Run `mise run ui:check` from repo root (lint + typecheck + all unit tests + production build) — must exit 0
- [ ] T017 [P] Verify visually: start `mise run ui:dev`, open `/submissions` as a mock initiator, confirm header CTA is visible and navigates to `/submit/document-approval`
- [ ] T018 [P] Verify visually: with `submittedFlows` mocked as empty, confirm `OnyxEmpty` renders with message and secondary CTA button

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (US1)**: Start immediately — no prerequisites beyond the existing codebase
- **Phase 2 (US2)**: Start after Phase 1 implementation is complete (same file; sequential edits are safer)
- **Phase 3 (Polish)**: After both US phases complete

### Within Each User Story

1. Write ALL tests for the story first (RED)
2. Implement until tests pass (GREEN)
3. Run `mise run ui:test` to confirm before moving to next phase

### Parallel Opportunities

Within Phase 1 tests: T002–T005 can be written in parallel (all in the same file but independent test cases).
Within Phase 2 tests: T010–T012 can be written in parallel.
T017 and T018 (visual checks) can run in parallel.

---

## Implementation Strategy

### MVP (User Story 1 Only)

1. Complete Phase 1 (T001–T009)
2. Run `mise run ui:check`
3. Validate: CTA visible in header, one-click navigation works
4. US1 alone satisfies FR-001, FR-002, FR-003, FR-006

### Full Feature

1. Complete Phase 1 → Phase 2 → Phase 3
2. All 6 FRs satisfied
3. `mise run ui:check` exits 0 before push (Principle VI)

---

## Notes

- All tasks are in `ui/` — no backend, no Kotlin, no Gradle changes
- `OnyxButton` with `:link` prop renders as a router-aware anchor — no `@click` + `router.push` needed
- `OnyxEmpty` is not globally registered; must be imported from `sit-onyx`
- Constitution Principle II: tests written before implementation — they MUST fail (RED) before T006
- Constitution Principle VI: `mise run ui:check` must pass before `git push`
