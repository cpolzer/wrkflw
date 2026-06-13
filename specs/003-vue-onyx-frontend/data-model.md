# Data Model: Vue/Onyx Web Frontend

**Date**: 2026-06-10 | **Source**: `specs/001-document-approval-engine/contracts/openapi.yaml`

All types below are **generated** from the backend OpenAPI spec via `openapi-typescript`.
The canonical source is `src/api/types.ts` (do not edit by hand).
This document records the shapes as they stand at planning time for human reference.

---

## Core DTO Shapes (consumed from backend)

### FlowStatus

Returned by `POST /flows` (201) and `GET /flows/{flowId}` (200 — without history).

```typescript
interface FlowStatus {
  flowId: string;           // UUID
  definitionId: string;     // UUID — which workflow definition
  state: FlowState;
  submitterId: string;
  submitterGroups: string[];
  startedAt: string;        // ISO-8601
  updatedAt: string;
}

type FlowState =
  | 'PENDING_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'RETURNED_FOR_REWORK'
  | 'COMPLETED';
```

### FlowStatusWithHistory

Returned by `GET /flows/{flowId}`.

```typescript
interface FlowStatusWithHistory extends FlowStatus {
  pendingTasks: TaskSummary[];
  history: AuditEvent[];
  formData: Record<string, unknown>;  // submitted field values
}
```

### TaskSummary

Returned by worklist endpoints and task mutations.

```typescript
interface TaskSummary {
  taskId: string;           // UUID
  flowId: string;           // UUID
  definitionStage: string;  // human-readable stage name
  candidateGroup: string;
  assigneeId: string | null;
  state: TaskState;
  createdAt: string;        // ISO-8601
  flowSummary: {
    definitionId: string;
    submitterId: string;
    startedAt: string;
  };
}

type TaskState = 'PENDING' | 'CLAIMED' | 'COMPLETED';
```

### AuditEvent

Entries in `FlowStatusWithHistory.history`.

```typescript
interface AuditEvent {
  eventId: string;
  flowId: string;
  actorId: string;
  actorGroups: string[];
  eventType: AuditEventType;
  occurredAt: string;       // ISO-8601
  detail: Record<string, unknown> | null;
}

type AuditEventType =
  | 'FLOW_STARTED'
  | 'TASK_CREATED'
  | 'TASK_CLAIMED'
  | 'TASK_RELEASED'
  | 'DECISION_RECORDED'
  | 'FLOW_COMPLETED'
  | 'FLOW_RETURNED_FOR_REWORK';
```

---

## Request Bodies (sent to backend)

### SubmitDocumentRequest

```typescript
interface SubmitDocumentRequest {
  definitionId: string;               // UUID — selected workflow definition
  formData: Record<string, unknown>;  // field values keyed by field name
}
```

### DecisionRequest

```typescript
interface DecisionRequest {
  outcome: 'APPROVED' | 'REJECTED';
  comment: string | null;  // required when outcome === 'REJECTED'
}
```

---

## Frontend-Only State Shapes (Pinia stores)

### AuthStore

```typescript
interface AuthState {
  user: {
    id: string;
    name: string;
    groups: string[];
  } | null;
  isAuthenticated: boolean;
}
```

### NotificationsStore

```typescript
interface NotificationsState {
  reworkPendingCount: number;  // flows returned for rework since last visit
}
```

---

## Workflow Definition Shape (consumed from backend — future endpoint)

The dynamic form (FR-003) renders fields declared by the workflow definition.
The exact schema for field declarations is to be confirmed when the backend exposes a
`GET /definitions/{id}` endpoint. Provisional shape assumed for planning:

```typescript
interface WorkflowDefinition {
  definitionId: string;
  name: string;
  initiatorGroup: string;
  fields: FieldDefinition[];
}

interface FieldDefinition {
  name: string;
  label: string;
  type: 'text' | 'textarea' | 'select' | 'date';
  required: boolean;
  options?: string[];  // for type === 'select'
}
```

> **Note**: If `GET /definitions` is not available in the first backend deliverable, the UI
> falls back to a hardcoded field schema for the document-approval definition and renders it
> as a static form. This fallback is removed once the endpoint exists.
