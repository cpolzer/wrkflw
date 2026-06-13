import { api } from './client'
import type { TaskSummary, DecisionRequest } from './models'

export async function claimTask(taskId: string): Promise<TaskSummary> {
  return api.post<TaskSummary>(`/tasks/${taskId}/claim`)
}

export async function releaseTask(taskId: string): Promise<TaskSummary> {
  return api.post<TaskSummary>(`/tasks/${taskId}/release`)
}

export async function submitDecision(taskId: string, decision: DecisionRequest): Promise<TaskSummary> {
  return api.post<TaskSummary>(`/tasks/${taskId}/decision`, decision)
}
