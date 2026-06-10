import { ref } from 'vue'
import { submitFlow as apiSubmitFlow, getFlow as apiGetFlow, getSubmitterFlows as apiGetSubmitterFlows } from '@/api/flows'
import { useNotificationsStore } from '@/stores/notifications'
import type { FlowStatus, FlowStatusWithHistory, FlowSubmitRequest } from '@/api/models'

export function useFlows() {
  const currentFlow = ref<FlowStatusWithHistory | null>(null)
  const submittedFlows = ref<FlowStatus[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  async function submitFlow(payload: FlowSubmitRequest): Promise<FlowStatus> {
    isLoading.value = true
    error.value = null
    try {
      const result = await apiSubmitFlow(payload)
      currentFlow.value = result as FlowStatusWithHistory
      return result
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Submission failed'
      throw e
    } finally {
      isLoading.value = false
    }
  }

  async function fetchFlow(flowId: string): Promise<void> {
    isLoading.value = true
    error.value = null
    try {
      currentFlow.value = await apiGetFlow(flowId)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load flow'
      throw e
    } finally {
      isLoading.value = false
    }
  }

  async function fetchSubmittedFlows(): Promise<void> {
    isLoading.value = true
    error.value = null
    try {
      submittedFlows.value = await apiGetSubmitterFlows()
      const notifications = useNotificationsStore()
      notifications.updateFromFlows(submittedFlows.value)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load flows'
      throw e
    } finally {
      isLoading.value = false
    }
  }

  return { currentFlow, submittedFlows, isLoading, error, submitFlow, fetchFlow, fetchSubmittedFlows }
}
