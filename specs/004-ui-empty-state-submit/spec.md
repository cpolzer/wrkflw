# Feature Specification: UI Empty State & Submit Entry Point

**Feature Branch**: `004-ui-empty-state-submit`

**Created**: 2026-06-11

**Status**: Draft

**Input**: User description: "looking at our ui - its empty. and i do not have a button to submit a new document for approval. lets change that"

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Submit a new document from My Submissions (Priority: P1)

A submitter lands on the "My Submissions" page — either because they are new and have no flows yet, or because they want to start a new one alongside existing submissions. They see a clearly labelled action to start a new approval request and can reach the submission form in a single click.

**Why this priority**: Without a visible entry point to the submission form, the core user action of the application is unreachable through normal navigation. This is a critical usability gap.

**Independent Test**: Open the application as an authenticated submitter, land on "My Submissions", click the submit CTA, confirm the submission form loads with the correct workflow definition.

**Acceptance Scenarios**:

1. **Given** the user is authenticated and has no prior submissions, **When** they open "My Submissions", **Then** they see a welcoming empty state message AND a prominently placed button to start a new submission — not just the text "No submissions yet."
2. **Given** the user is authenticated and has one or more existing submissions, **When** they view "My Submissions", **Then** a "Submit new document" button is always visible in the page header area — not hidden by or dependent on the table state.
3. **Given** the user clicks the submit CTA, **When** the navigation resolves, **Then** the submission form for the document-approval workflow opens immediately.
4. **Given** the user is authenticated but does NOT belong to the initiator group, **When** they view "My Submissions", **Then** the submit CTA is not shown (they cannot submit).

---

### User Story 2 — Welcoming empty state (Priority: P2)

When a submitter has no flows yet, the page communicates what the section is for and guides them toward their first action rather than presenting a bare dead end.

**Why this priority**: An informative empty state reduces confusion for new users and sets correct expectations about the section's purpose.

**Independent Test**: Log in as a first-time submitter with zero submitted flows; the "My Submissions" page must display a descriptive message explaining the section's purpose alongside the submit CTA.

**Acceptance Scenarios**:

1. **Given** the user has no submitted flows, **When** they land on "My Submissions", **Then** they see a short explanatory message (e.g., "You have not submitted any documents for approval yet.") and a CTA to start their first submission.
2. **Given** the user follows the CTA from the empty state, **When** they submit a document successfully, **Then** they are redirected to the flow detail page and their submission subsequently appears in the "My Submissions" list.

---

### Edge Cases

- What happens if the available workflow definitions list is empty? The submit CTA should not be shown and a message should indicate that no submission types are currently available.
- What happens when the user is in both the initiator and reviewer groups? The submit CTA is shown because they are permitted to initiate; the existing routing handles the correct landing page.
- What happens if the submissions list is loading? The submit CTA remains visible and enabled during the loading state; it is independent of the data fetch.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The "My Submissions" page MUST display a visible submit CTA whenever the authenticated user belongs to an initiator group and at least one workflow definition is available.
- **FR-002**: The submit CTA MUST be present regardless of whether the user has zero or more existing submissions (it is NOT only shown in the empty state).
- **FR-003**: Clicking the submit CTA MUST navigate the user to the submission form for the appropriate workflow definition without additional intermediate steps.
- **FR-004**: When the user has no existing submissions, the page MUST display an informative empty-state message describing the section's purpose — not a bare "No submissions yet." string.
- **FR-005**: The empty state MUST include the submit CTA as a secondary call to action alongside the explanatory message.
- **FR-006**: Users who do NOT belong to any initiator group MUST NOT see the submit CTA.
- **FR-007**: If no workflow definitions are available, the submit CTA MUST be hidden and the page MUST inform the user that no submission types are currently configured.

### Key Entities

- **Workflow Definition**: A named submission type available to the user; determines which form is shown and which initiator group is required.
- **Submission (Flow)**: A document sent through an approval process; displayed in the "My Submissions" list.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A first-time user can locate and click the submit CTA within 10 seconds of landing on "My Submissions" without any guidance.
- **SC-002**: The empty state conveys the section's purpose — validated by a user being able to describe what "My Submissions" is for after viewing it for 5 seconds.
- **SC-003**: The submit CTA is reachable in exactly one click from "My Submissions" regardless of whether the user has prior submissions.
- **SC-004**: Users who lack initiator-group membership never see the submit CTA (zero false positives in access control).

## Assumptions

- There is currently one hardcoded workflow definition ("Document Approval"). The submit CTA navigates directly to that definition's form without an intermediate picker screen. If multiple definitions are introduced in the future, a selection step will be added at that time.
- The initiator group check uses the user's group membership as provided by the authentication token; no additional backend call is required for this permission check.
- The submission form itself (`SubmitFlowView`) already exists and functions correctly — this feature only adds navigation to it.
- The visual style of the CTA and empty-state message follows the existing Onyx component library conventions already in use in the application.
