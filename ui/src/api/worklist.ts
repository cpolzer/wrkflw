import { api } from './client'
import type { TaskSummary } from './models'

export async function getGroupWorklist(): Promise<TaskSummary[]> {
  return api.get<TaskSummary[]>('/worklists/group')
}

export async function getMyTasks(): Promise<TaskSummary[]> {
  return api.get<TaskSummary[]>('/worklists/mine')
}
