import { ref } from 'vue'
import { getGroupWorklist, getMyTasks } from '@/api/worklist'
import { claimTask as apiClaimTask, releaseTask as apiReleaseTask, submitDecision as apiSubmitDecision } from '@/api/tasks'
import type { TaskSummary, DecisionRequest } from '@/api/models'

export function useWorklist() {
  const groupTasks = ref<TaskSummary[]>([])
  const myTasks = ref<TaskSummary[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  async function fetchAll(): Promise<void> {
    isLoading.value = true
    error.value = null
    try {
      const [group, mine] = await Promise.all([getGroupWorklist(), getMyTasks()])
      groupTasks.value = group.filter((t) => t.status === 'PENDING')
      myTasks.value = mine
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load worklist'
      throw e
    } finally {
      isLoading.value = false
    }
  }

  async function claimTask(taskId: string): Promise<TaskSummary> {
    const result = await apiClaimTask(taskId)
    groupTasks.value = groupTasks.value.filter((t) => t.taskId !== taskId)
    myTasks.value = [...myTasks.value, result]
    return result
  }

  async function releaseTask(taskId: string): Promise<TaskSummary> {
    const result = await apiReleaseTask(taskId)
    myTasks.value = myTasks.value.filter((t) => t.taskId !== taskId)
    return result
  }

  async function submitDecision(taskId: string, decision: DecisionRequest): Promise<TaskSummary> {
    const result = await apiSubmitDecision(taskId, decision)
    myTasks.value = myTasks.value.filter((t) => t.taskId !== taskId)
    return result
  }

  return { groupTasks, myTasks, isLoading, error, fetchAll, claimTask, releaseTask, submitDecision }
}
