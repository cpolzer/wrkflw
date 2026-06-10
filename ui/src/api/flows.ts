import { api } from './client'
import type { FlowStatus, FlowStatusWithHistory, FlowSummary, FlowSubmitRequest } from './models'

export async function submitFlow(payload: FlowSubmitRequest): Promise<FlowStatus> {
  return api.post<FlowStatus>('/flows', {
    definitionKey: payload.definitionId,
    documentRef: JSON.stringify(payload.formData),
  })
}

export async function getFlow(flowId: string): Promise<FlowStatusWithHistory> {
  return api.get<FlowStatusWithHistory>(`/flows/${flowId}`)
}

export async function getSubmitterFlows(): Promise<FlowSummary[]> {
  return api.get<FlowSummary[]>('/flows')
}
