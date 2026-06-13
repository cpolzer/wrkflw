// Clean re-exports of generated OpenAPI schema types.
// Import from this file; never import directly from types.ts.
import type { components } from './types'

export type FlowStatus = components['schemas']['FlowStatus']
export type FlowStatusWithHistory = components['schemas']['FlowStatusWithHistory']
export type FlowSummary = components['schemas']['FlowSummary']
export type TaskSummary = components['schemas']['TaskSummary']
export type SubmitDocumentRequest = components['schemas']['SubmitDocumentRequest']
export type DecisionRequest = components['schemas']['DecisionRequest']

// Frontend-facing submit payload — form data is encoded as documentRef JSON.
// api/flows.ts translates this to the backend SubmitDocumentRequest.
export interface FlowSubmitRequest {
  definitionId: string
  formData: Record<string, string>
}

// Frontend extended task — backend returns stateName/candidateGroupId/ownerId;
// aliased here to match how the views reference them.
export interface RichTaskSummary extends TaskSummary {
  definitionStage: string
  candidateGroupId: string
  ownerId?: string | null
}

// Frontend extended flow — backend returns currentState + documentRef;
// formData is decoded from documentRef if it was submitted as JSON.
export interface RichFlowStatus extends FlowStatus {
  currentState: string
  formData?: Record<string, unknown>
  history?: components['schemas']['AuditEntry'][]
}

export type FlowState =
  | 'PENDING_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'RETURNED_FOR_REWORK'
  | 'COMPLETED'

export type TaskState = 'PENDING' | 'CLAIMED' | 'COMPLETED'

export interface FieldDefinition {
  name: string
  label: string
  type: 'text' | 'textarea' | 'select' | 'date'
  required: boolean
  options?: string[]
}

export interface WorkflowDefinition {
  definitionId: string
  name: string
  initiatorGroup: string
  fields: FieldDefinition[]
}
