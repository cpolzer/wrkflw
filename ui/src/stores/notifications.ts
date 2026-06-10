import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { FlowStatus } from '@/api/models'

export const useNotificationsStore = defineStore('notifications', () => {
  const seenReworkFlowIds = ref<Set<string>>(
    new Set(JSON.parse(sessionStorage.getItem('seenReworkIds') ?? '[]') as string[]),
  )

  const reworkFlows = ref<FlowStatus[]>([])

  const reworkPendingCount = computed(
    () =>
      reworkFlows.value.filter(
        (f) =>
          (f as FlowStatus & { currentState?: string }).currentState === 'RETURNED_FOR_REWORK' &&
          !seenReworkFlowIds.value.has(f.flowId ?? ''),
      ).length,
  )

  function updateFromFlows(flows: FlowStatus[]): void {
    reworkFlows.value = flows
  }

  function markSeen(flowId: string): void {
    seenReworkFlowIds.value.add(flowId)
    sessionStorage.setItem('seenReworkIds', JSON.stringify([...seenReworkFlowIds.value]))
  }

  return { reworkPendingCount, reworkFlows, updateFromFlows, markSeen }
})
