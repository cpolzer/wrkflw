# Tasks: Vue/Onyx Web Frontend

**Input**: Design documents from `specs/003-vue-onyx-frontend/`

**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅ contracts/ ✅ quickstart.md ✅

**Tests**: Included per Constitution Principle II (NON-NEGOTIABLE — overrides template default). Unit tests cover composables; Playwright E2E tests cover API adapters and user journeys. Tests are written FIRST and MUST FAIL before implementation begins (Red → Green → Refactor).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1–US4)
- Exact file paths are included in all task descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Scaffold the `ui/` project and add Keycloak to the dev stack. No user story work begins here.

- [X] T001 Scaffold Vite + Vue 3 + TypeScript project in `ui/` (run `npm create vite@latest ui -- --template vue-ts`, commit initial scaffold)
- [X] T002 Install Onyx component library (`@sit-onyx/components`), register globally in `ui/src/main.ts`, import Onyx CSS
- [X] T003 [P] Configure ESLint (`@vue/eslint-config-typescript`) and Prettier in `ui/eslint.config.ts` and `ui/.prettierrc`
- [X] T004 [P] Configure Vitest in `ui/vitest.config.ts` with `@vue/test-utils` and `@testing-library/vue`
- [X] T005 [P] Configure Playwright in `ui/playwright.config.ts` targeting `http://localhost:5173`
- [X] T006 Add Keycloak 25.x service and its Postgres DB to `docker-compose.yml`; mount `ui/keycloak/realm-export.json` via `--import-realm` on first boot
- [X] T007 Create `ui/keycloak/realm-export.json` with realm `wrkflw`, public OIDC client `wrkflw-ui` (PKCE), groups `initiators`/`legal-reviewers`, and test users `alice` (initiators), `bob` (legal-reviewers), `carol` (both) — all with password `password`
- [X] T008 Add `generate:types` npm script to `ui/package.json` using `openapi-typescript`; run it against `specs/001-document-approval-engine/contracts/openapi.yaml` and commit initial `ui/src/api/types.ts`
- [X] T009 Create `ui/.env.example` documenting `VITE_API_BASE_URL`, `VITE_OIDC_AUTHORITY`, `VITE_OIDC_CLIENT_ID`, `VITE_API_TIMEOUT_MS`
- [X] T010 Add `npm run check` script to `ui/package.json` (lint + typecheck + unit tests + build) to serve as the local validation gate per Constitution Principle VI

**Checkpoint**: `docker compose up -d` starts Keycloak at `:8180`; `cd ui && npm run check` passes on the empty scaffold.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Auth, API client, router skeleton, nav shell — everything US1–US4 build on. No user story work begins until this phase is complete.

**⚠️ CRITICAL**: No user story implementation can begin until this phase is complete.

- [X] T011 Implement OIDC setup in `ui/src/auth/oidc.ts` using `oidc-client-ts` (PKCE flow, silent token renewal, `VITE_OIDC_*` env vars)
- [X] T012 Create `auth` Pinia store in `ui/src/stores/auth.ts` (fields: `user: { id, name, groups } | null`, `isAuthenticated: boolean`; actions: `login()`, `logout()`, `loadUser()`)
- [X] T013 Implement API HTTP client in `ui/src/api/client.ts` (attaches `Authorization: Bearer` + compat headers `X-Actor-Id`/`X-Actor-Groups`; handles 401 → silent refresh → retry; 10 s timeout; maps all error statuses per contracts/api-consumption.md)
- [X] T014 [P] Implement flows API module `ui/src/api/flows.ts` (functions: `submitFlow`, `getFlow`; typed against generated `src/api/types.ts`)
- [X] T015 [P] Implement worklist API module `ui/src/api/worklist.ts` (functions: `getGroupWorklist`, `getMyTasks`)
- [X] T016 [P] Implement tasks API module `ui/src/api/tasks.ts` (functions: `claimTask`, `releaseTask`, `submitDecision`)
- [X] T017 Configure Vue Router 4 in `ui/src/router/index.ts`: define placeholder routes `/submissions`, `/submit/:definitionId`, `/flows/:flowId`, `/worklist`, `/tasks/:taskId`; add navigation guard that redirects unauthenticated users to OIDC login, preserving the intended route
- [X] T018 Implement smart landing logic in `ui/src/router/index.ts`: after auth, call `getGroupWorklist()`; if result is non-empty redirect to `/worklist`, else to `/submissions` (FR-016)
- [X] T019 Create `AppNav` component `ui/src/components/AppNav.vue` using Onyx nav primitives; shows "My Submissions" and "My Worklist" sections always, plus rework badge placeholder (FR-015)
- [X] T020 Create global error toast handler `ui/src/composables/useErrorHandler.ts`; wire it to the API client so all error status codes surface an Onyx toast without duplicating error logic in views

**Checkpoint**: App loads, redirects to Keycloak login, logs in as `alice`, lands on `/submissions` (empty state). Navigation between sections works without page reload.

---

## Phase 3: User Story 1 — Submit a document for approval (Priority: P1) 🎯 MVP

**Goal**: An authenticated initiator selects a workflow definition, fills in a dynamic form, submits, and is taken to a flow-status confirmation view.

**Independent Test**: Log in as `alice`, navigate to `/submit/document-approval`, fill form fields, submit — confirm redirect to `/flows/{id}` showing flow state `PENDING_REVIEW` and the submitted form data.

### Tests for User Story 1 (Constitution Principle II — write first, must fail before T025)

- [X] T021 [P] [US1] Write Vitest unit test for `useFlows` composable (test `submitFlow` calls the correct API module function with correct payload and updates reactive state) in `ui/tests/unit/useFlows.spec.ts`
- [X] T022 [P] [US1] Write Playwright E2E test for submit-flow happy path: login as `alice`, fill form, submit, assert `/flows/{id}` route and `PENDING_REVIEW` badge visible in `ui/tests/e2e/submit-flow.spec.ts`

### Implementation for User Story 1

- [X] T023 [US1] Create `DynamicFormField` component `ui/src/components/DynamicFormField.vue` — renders a single field (`text`, `textarea`, `select`, `date`) from a `FieldDefinition` prop using Onyx input components; emits value updates
- [X] T024 [US1] Add hardcoded `documentApprovalDefinition` constant to `ui/src/api/flows.ts` (fields: `title: text`, `description: textarea`, `priority: select[low/medium/high]`) to unblock the form until `GET /definitions` backend endpoint exists
- [X] T025 [US1] Create `SubmitFlowView` `ui/src/views/SubmitFlowView.vue`: renders definition name, maps `FieldDefinition[]` to `DynamicFormField` components, collects `formData`, calls `useFlows.submitFlow()`, navigates to `/flows/{id}` on success
- [X] T026 [US1] Add inline required-field validation to `SubmitFlowView` (FR-013): prevent submission when required fields are blank; highlight each empty required field with an Onyx error state
- [X] T027 [US1] Implement `useFlows` composable `ui/src/composables/useFlows.ts` (`submitFlow`, `getFlow`, `flows` reactive ref, loading/error state)
- [X] T028 [US1] Wire `SubmitFlowView` to route `/submit/:definitionId` in `ui/src/router/index.ts`

**Checkpoint**: User Story 1 fully functional. `alice` can submit a document; flow appears in backend; `/flows/{id}` shows status. `npm run test:e2e -- submit-flow` passes.

---

## Phase 4: User Story 2 — Review and decide on a task (Priority: P1)

**Goal**: A reviewer sees all unclaimed group tasks, claims one, reviews the full submitted form data, and approves or rejects (rejection requires a comment).

**Independent Test**: Log in as `bob`, open `/worklist` — pending task from alice's submission is visible with title, stage, submitter, time waiting. Claim it; task moves to "My Tasks". Open task; approve — task disappears from worklist, flow advances.

### Tests for User Story 2 (write first, must fail before T033)

- [X] T029 [P] [US2] Write Vitest unit test for `useWorklist` composable (asserts `getGroupWorklist` is called on mount and result is stored reactively) in `ui/tests/unit/useWorklist.spec.ts`
- [X] T030 [P] [US2] Write Playwright E2E test for claim-and-approve cycle: login as `bob`, claim alice's task, approve, assert flow status advances in `ui/tests/e2e/claim-approve.spec.ts`

### Implementation for User Story 2

- [X] T031 [US2] Implement `useWorklist` composable `ui/src/composables/useWorklist.ts` (`groupTasks`, `myTasks` reactive refs; `claimTask`, `releaseTask` actions; auto-refresh on mount)
- [X] T032 [US2] Create `WorklistView` `ui/src/views/WorklistView.vue`: Onyx table of unclaimed group tasks (columns: document title, stage, submitter name, time waiting); claim button per row; section for "My claimed tasks"
- [X] T033 [US2] Create `TaskDetailView` `ui/src/views/TaskDetailView.vue`: renders full `formData` as read-only field/value pairs (FR-008a); Approve button + Reject button; reject opens Onyx dialog requiring non-empty comment; calls `submitDecision()`
- [X] T034 [US2] Wire `WorklistView` to `/worklist` and `TaskDetailView` to `/tasks/:taskId` in `ui/src/router/index.ts`
- [X] T035 [US2] Handle 409 conflict in `WorklistView` (task already claimed by someone else): show "Task is no longer available" Onyx toast and refresh the worklist (edge case from spec)

**Checkpoint**: User Stories 1 + 2 fully functional end-to-end. Full approve cycle works. `npm run test:e2e -- claim-approve` passes.

---

## Phase 5: User Story 3 — Track submitted flows (Priority: P2)

**Goal**: A submitter sees all their flows with current states and can drill into any flow to see the full immutable audit history.

**Independent Test**: Log in as `alice`, open `/submissions` — see her submitted flow with state and last-updated. Click flow → `/flows/{id}` shows every audit event (FLOW_STARTED, TASK_CLAIMED, DECISION_RECORDED) with actor name and timestamp.

### Tests for User Story 3 (write first, must fail before T040)

- [X] T036 [P] [US3] Write Vitest unit test for `useFlows` list behaviour (asserts `flows` ref populates from a mocked API response, state labels map correctly) in `ui/tests/unit/useFlows.spec.ts` (extend existing file)
- [X] T037 [P] [US3] Write Playwright E2E test for flow tracking: login as `alice`, open `/submissions`, assert flow row visible, click through to `/flows/{id}`, assert timeline shows FLOW_STARTED and TASK_CREATED events in `ui/tests/e2e/flow-tracking.spec.ts`

### Implementation for User Story 3

- [X] T038 [US3] Extend `useFlows` composable to support fetching the submitter's own flows (call `GET /flows` with submitter filter or adapt to available query parameter; store result in `submittedFlows` ref)
- [X] T039 [US3] Create `MySubmissionsView` `ui/src/views/MySubmissionsView.vue`: Onyx table of the user's flows (columns: definition name, current state with `FlowStatusBadge`, last-updated); wire to `/submissions` route
- [X] T040 [US3] Create `FlowDetailView` `ui/src/views/FlowDetailView.vue`: shows flow state, submitted `formData` (read-only), and a chronological audit timeline (actor name, event type, timestamp) for every `AuditEvent` in `history`; wire to `/flows/:flowId` route (also used by US1 post-submit redirect)

**Checkpoint**: User Stories 1–3 all functional. `alice` can see her flow history after `bob` approves. `npm run test:e2e -- flow-tracking` passes.

---

## Phase 6: User Story 4 — Re-submit a reworked document (Priority: P2)

**Goal**: After rejection, the submitter sees an in-app notification, reviews the rejection comment, and re-submits from the flow detail view.

**Independent Test**: Reject alice's flow as `bob` with comment "Missing signature". Log in as `alice` — rework badge visible in nav. Open the flow → rejection comment prominent → click re-submit → new review task appears in bob's worklist.

### Tests for User Story 4 (write first, must fail before T044)

- [X] T041 [P] [US4] Write Vitest unit test for `notifications` store (`reworkPendingCount` increments when a flow with state `RETURNED_FOR_REWORK` is present in `submittedFlows`) in `ui/tests/unit/notifications.spec.ts`
- [X] T042 [P] [US4] Write Playwright E2E test for full rework cycle: reject as `bob`, login as `alice`, assert rework badge, re-submit, assert new task in bob's worklist in `ui/tests/e2e/rework-cycle.spec.ts`

### Implementation for User Story 4

- [X] T043 [US4] Implement `notifications` Pinia store `ui/src/stores/notifications.ts` (`reworkPendingCount` computed from `submittedFlows`; `markSeen()` action; persisted to `sessionStorage` so badge clears after user visits the flow)
- [X] T044 [US4] Create `ReworkBanner` component `ui/src/components/ReworkBanner.vue` (Onyx badge on "My Submissions" nav item showing `reworkPendingCount`; clicking navigates to `/submissions`)
- [X] T045 [US4] Wire `ReworkBanner` into `AppNav` and wire `notifications` store to load on every login
- [X] T046 [US4] Add re-submit action to `FlowDetailView` — renders a "Re-submit for review" button and the rejection comment prominently when `flow.state === 'RETURNED_FOR_REWORK'`; calls `submitFlow()` with same `definitionId` and pre-populated `formData`; navigates to new flow on success (FR-011)

**Checkpoint**: Full lifecycle works end-to-end: submit → review → reject → rework notification → re-submit → review again. `npm run test:e2e -- rework-cycle` passes.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Accessibility, edge-case hardening, local dev experience.

- [X] T047 [P] Audit all interactive Onyx components in views for keyboard reachability (Tab order, Enter/Space activation) and descriptive `aria-label` / `aria-describedby` attributes (SC-006, WCAG 2.1 AA)
- [X] T048 [P] Add `FlowStatusBadge` component `ui/src/components/FlowStatusBadge.vue` (Onyx chip/badge mapping each `FlowState` to a colour and human label; used in `MySubmissionsView` and `FlowDetailView`)
- [X] T049 Add session-expiry recovery: if OIDC silent refresh fails during form fill, preserve form data in `sessionStorage`, redirect to login, restore data on return (edge case from spec)
- [X] T050 Verify `npm run check` (lint + typecheck + vitest + build) passes clean with zero type errors after all phases
- [X] T051 Validate `quickstart.md` against actual local setup: run `docker compose up -d`, `npm install`, `npm run dev`, log in with each test user — confirm all flows work as documented
- [X] T052 Update `AGENTS.md` with `ui/` project entry: tech stack, local commands (`npm run dev`, `npm run check`, `npm run test:e2e`), test user credentials

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundation)**: Depends on Phase 1 complete — BLOCKS all user stories
- **Phase 3 (US1)**: Depends on Phase 2 complete
- **Phase 4 (US2)**: Depends on Phase 2 complete; US2 E2E tests depend on a submitted flow (US1 runtime dependency, not code dependency)
- **Phase 5 (US3)**: Depends on Phase 2 complete; shares `useFlows` with US1
- **Phase 6 (US4)**: Depends on US1 (submit) and US2 (reject path) being complete
- **Phase 7 (Polish)**: Depends on all user story phases complete

### Within Each User Story

1. Write tests FIRST — verify they FAIL (Red)
2. Implement composables / stores
3. Implement components / views
4. Wire routes
5. Verify tests now PASS (Green)
6. Refactor if needed (Refactor)

### Parallel Opportunities (within Phase 2)

All of T014, T015, T016 can run in parallel (different files, no inter-dependencies).

### Parallel Opportunities (within each US phase)

- US1: T021 and T022 can run in parallel; T023 and T024 can run in parallel
- US2: T029 and T030 can run in parallel
- US3: T036 and T037 can run in parallel
- US4: T041 and T042 can run in parallel

---

## Parallel Example: User Story 2

```bash
# Write tests in parallel:
Task T029: "Write Vitest unit test for useWorklist in ui/tests/unit/useWorklist.spec.ts"
Task T030: "Write Playwright E2E test for claim-approve cycle in ui/tests/e2e/claim-approve.spec.ts"

# Then implement in parallel:
Task T031: "Implement useWorklist composable in ui/src/composables/useWorklist.ts"
# (T032, T033 depend on T031 completing first)
```

---

## Implementation Strategy

### MVP (User Story 1 + 2 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundation (blocks all stories)
3. Complete Phase 3: US1 — submit flow
4. Complete Phase 4: US2 — claim and decide
5. **STOP and VALIDATE**: Full approval cycle works end-to-end with real Keycloak + backend
6. Demo / deploy

### Incremental Delivery

1. Setup + Foundation → navigable skeleton with auth
2. Add US1 → submitters can initiate flows (MVP for submitters)
3. Add US2 → reviewers can act (MVP for full approval cycle)
4. Add US3 → submitters can track history
5. Add US4 → full rework loop closes
6. Polish → production-ready quality

---

## Notes

- **[P]** tasks operate on different files with no incomplete-task dependencies — safe to parallelise
- Constitution Principle II is non-negotiable: tests are written before implementation in every phase; they must fail before the implementation tasks begin
- The `GET /definitions` backend endpoint is missing (noted in contracts/api-consumption.md); T024 provides a hardcoded fallback that is removed once the endpoint ships
- The backend auth header migration (Bearer token → JWT claims extraction) is a backend concern tracked separately; `client.ts` sends both headers during the transition period
- Commit after each phase checkpoint; push before marking phase complete (Constitution Principle VI)
