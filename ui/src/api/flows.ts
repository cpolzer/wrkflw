import { api } from './client'
import type { FlowStatus, FlowStatusWithHistory, FlowSubmitRequest } from './models'

export async function submitFlow(payload: FlowSubmitRequest): Promise<FlowStatus> {
  return api.post<FlowStatus>('/flows', {
    definitionKey: payload.definitionId,
    documentRef: JSON.stringify(payload.formData),
  })
}

export async function getFlow(flowId: string): Promise<FlowStatusWithHistory> {
  return api.get<FlowStatusWithHistory>(`/flows/${flowId}`)
}

// No GET /flows endpoint exists yet; returns empty until backend adds it.
export async function getSubmitterFlows(): Promise<FlowStatus[]> {
  return api.get<FlowStatus[]>('/flows')
}
