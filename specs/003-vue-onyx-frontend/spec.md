# Feature Specification: Vue/Onyx Web Frontend

**Feature Branch**: `003-vue-onyx-frontend`

**Created**: 2026-06-10

**Status**: Draft

**Input**: User description: "let's add our web frontend layer. Vue is the eco system and we want to use the schwarz it's onyx framework."

## Clarifications

### Session 2026-06-10

- Q: What constitutes a "document" in the approval workflow? → A: Structured form data whose fields are declared by the workflow definition; no physical file upload required.
- Q: How does authentication work, and how is it provided locally for development? → A: OIDC/SSO redirect (e.g., Keycloak); the frontend redirects to the IdP and receives tokens on callback. Local development uses a pre-configured Keycloak instance via Docker Compose with a committed realm config and test users — zero additional setup required.
- Q: How is the submitter notified when a document is returned for rework? → A: In-app only — a banner or badge is displayed when the submitter next opens the application; no email or push notification required.
- Q: How do dual-role users (submitter and reviewer) navigate between capabilities? → A: Unified navigation with a persistent sidebar/top nav; sections labelled "My Submissions" and "My Worklist" are always visible. Default landing page is the worklist when the user has pending tasks, otherwise "My Submissions".
- Q: What form data do reviewers see when evaluating a claimed task? → A: All form fields submitted by the initiator, displayed read-only in full.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Submit a document for approval via the web UI (Priority: P1)

An authenticated user opens the web application, sees a list of available workflow definitions, selects one, and fills in the structured form whose fields are declared by that workflow definition. On submission the UI immediately reflects that the flow has started and shows its current state.

**Why this priority**: Submitting a document is the primary entry point and the most critical user-facing action the engine supports. Without this, nothing else in the UI has value.

**Independent Test**: A user logs in, navigates to the submission form, submits a document, and is taken to a flow-status view confirming the flow is active and a review task is waiting.

**Acceptance Scenarios**:

1. **Given** the user is authenticated, **When** they open the application, **Then** they see a list of workflow definitions they are permitted to initiate.
2. **Given** a selected workflow definition, **When** the user fills in required fields and confirms submission, **Then** a new flow is started and the user is presented with a confirmation view showing flow ID and current state.
3. **Given** a successful submission, **When** the user views the flow, **Then** they see a status timeline showing who submitted, when, and which group holds the current task.

---

### User Story 2 — Review and decide on a task assigned to my group (Priority: P1)

A reviewer opens the application and sees a worklist of tasks assigned to groups they belong to. They claim a task, review the full set of form fields submitted by the initiator (displayed read-only), and either approve or reject it — providing a comment on rejection. The task is immediately removed from the worklist and the flow advances.

**Why this priority**: Approvals are the core human interaction the engine is built around. Reviewers need a clear, focused worklist and a frictionless claim-and-decide flow.

**Independent Test**: Log in as a reviewer, find a pending task in the worklist, claim it, and submit an approval decision. The task disappears from the worklist and the flow status advances.

**Acceptance Scenarios**:

1. **Given** the user is a member of a reviewer group with pending tasks, **When** they open the worklist, **Then** they see all unclaimed tasks for their groups, each showing document title, flow stage, and time waiting.
2. **Given** an unclaimed task in the worklist, **When** the user claims it, **Then** the task is locked to that user and the claim is reflected in the flow history.
3. **Given** a claimed task, **When** the user submits an approval, **Then** the task is completed, the flow advances to the next stage, and the worklist no longer shows the task.
4. **Given** a claimed task, **When** the user rejects it and provides a comment, **Then** the flow returns the document to the submitter for rework and the rejection reason is visible in the flow history.

---

### User Story 3 — Track the status of my submitted flows (Priority: P2)

A submitter returns to the application after some time and wants to know what has happened to a document they submitted. They open a view listing all flows they initiated and can drill into any flow to see its full history — every decision, who made it, and when.

**Why this priority**: Submitters need visibility to avoid re-submission of in-flight documents and to react quickly if a document is rejected and returned for rework.

**Independent Test**: Log in as a submitter, open the "my flows" list, click a flow, and confirm the full event timeline is visible with actor names, decisions, and timestamps.

**Acceptance Scenarios**:

1. **Given** the user has submitted one or more flows, **When** they open the "my flows" section, **Then** they see a list with each flow's current state and last-updated time.
2. **Given** a flow that has been rejected and returned, **When** the submitter opens the flow detail, **Then** they see the rejection comment and a clear call-to-action to re-submit.
3. **Given** a completed flow, **When** the user views its history, **Then** every transition (submitted, claimed, approved/rejected, returned, completed) appears in chronological order with actor and timestamp.

---

### User Story 4 — Re-submit a reworked document (Priority: P2)

After a document is rejected and returned, the submitter sees an in-app notification banner or badge on next login. They can open the flow, review the rejection comment, and re-submit for another review cycle.

**Why this priority**: The rejection-and-rework loop is a core part of the approval lifecycle. Without re-submission, a rejected document is a dead end.

**Independent Test**: Start from a flow that has been rejected, re-submit it, and confirm a new review task appears in the worklist for the reviewer group.

**Acceptance Scenarios**:

1. **Given** a flow in "returned for rework" state, **When** the submitter opens it, **Then** they see the rejection reason and an option to re-submit.
2. **Given** the submitter re-submits, **When** the action is confirmed, **Then** the flow re-enters the first review stage and a new task appears in the reviewer group's worklist.

---

### Edge Cases

- What happens when a user's session expires while they are mid-form? The form state is preserved in the browser and the user is prompted to log in again before completing submission.
- What happens when two reviewers try to claim the same task simultaneously? Only one claim succeeds; the other reviewer sees a message indicating the task was already claimed.
- What happens when the backend is temporarily unreachable? The UI shows a clear error message and does not submit the form silently. No data loss occurs.
- What happens when a user navigates directly to a flow they do not own or are not a reviewer for? They receive an access-denied message.
- What happens when a required field is left blank on submission? Inline validation prevents submission and highlights the missing fields.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Users MUST authenticate via OIDC/SSO before accessing any workflow features; unauthenticated requests MUST redirect to the identity provider login page.
- **FR-002**: Authenticated users MUST see only workflow definitions they are permitted to initiate.
- **FR-003**: The system MUST render a submission form whose fields are driven by the selected workflow definition; required fields MUST be visually identified.
- **FR-004**: On submission, the system MUST create a new flow via the backend API and navigate the user to a flow-status view.
- **FR-005**: Reviewers MUST see a worklist showing all unclaimed tasks for groups they belong to.
- **FR-006**: The worklist MUST display document title, flow stage, submitter name, and time waiting for each task.
- **FR-007**: Users MUST be able to claim an unclaimed task; claiming locks the task to that user.
- **FR-008**: Users MUST be able to approve or reject a claimed task; rejection MUST require a comment.
- **FR-008a**: When viewing a claimed task, reviewers MUST see all form fields submitted by the initiator, rendered read-only in full.
- **FR-009**: Submitters MUST be able to view a list of all flows they have initiated and their current states.
- **FR-010**: Submitters MUST be able to view the full event history of any flow they own.
- **FR-011**: Users MUST be able to re-submit a flow that has been returned for rework.
- **FR-012**: The UI MUST display clear, user-friendly error messages for failed API calls and network errors.
- **FR-013**: The UI MUST prevent form submission when required fields are blank, providing inline field-level validation messages.
- **FR-014**: The application MUST be navigable without page reloads for all primary user interactions (single-page application behavior).
- **FR-015**: The application MUST provide a persistent navigation structure (sidebar or top nav) with clearly labelled sections for "My Submissions" and "My Worklist", visible to all authenticated users regardless of role.
- **FR-016**: On login, the application MUST direct users with pending worklist tasks to the worklist view; users with no pending tasks MUST land on "My Submissions".

### Key Entities

- **Flow**: Represents an in-progress or completed approval process; has an ID, state, submitter, start time, submitted form data, and event history.
- **Task**: A human action item within a flow; belongs to a group, may be claimed by an individual reviewer, and records the decision made.
- **Workflow Definition**: A named template describing the stages, responsible groups, and the structured form fields required at submission.
- **Form Data**: The structured key-value pairs submitted by the initiator, as defined by the workflow definition's field schema; this is the "document" that reviewers evaluate.
- **Event**: An immutable record of a transition within a flow — who acted, what they did, and when.
- **User**: An authenticated person; belongs to one or more groups/roles that determine what they can submit or review.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A first-time user can complete the full submit → review → approve cycle without assistance within 5 minutes.
- **SC-002**: The worklist view loads and displays all pending tasks within 2 seconds under normal load conditions.
- **SC-003**: 90% of users successfully claim and decide on a task on their first attempt without encountering a confusing UI state.
- **SC-004**: All primary user actions (submit, claim, approve, reject, re-submit) complete and reflect their outcome in the UI within 3 seconds of confirmation.
- **SC-005**: The application is fully usable on current-generation desktop browsers (Chrome, Firefox, Edge, Safari) without visual or functional defects.
- **SC-006**: Accessibility: all interactive elements are reachable by keyboard and carry descriptive labels (WCAG 2.1 AA as baseline).

## Assumptions

- Authentication uses OIDC/SSO: the frontend redirects to an identity provider (Keycloak in production) and receives tokens on callback. The frontend does not handle raw credentials. For local development, a pre-configured Keycloak Docker Compose setup with a committed realm config and test users is provided in the repository.
- The backend REST API from the document-approval engine (feature 001) is the data source; no new backend endpoints are introduced by this feature beyond what already exists or is planned.
- Mobile/responsive support is desirable but not a hard requirement for the first deliverable; desktop-first layout is acceptable.
- Onyx is a Vue component library from the Schwarz IT ecosystem; it supplies the design system (colors, typography, spacing, interactive components) and integrates natively with Vue 3. No custom design system will be created alongside it.
- Real-time push notifications are out of scope for the first deliverable; the worklist is refreshed on page load or manual refresh. Submitter rework notifications are in-app only (banner/badge on next login) — no email or push channel required.
- The application will be served as a static build deployed behind the existing infrastructure; no server-side rendering is required.
