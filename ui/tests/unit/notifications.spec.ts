import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useNotificationsStore } from '@/stores/notifications'

describe('useNotificationsStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
  })

  it('reworkPendingCount increments when a RETURNED_FOR_REWORK flow is present', () => {
    const store = useNotificationsStore()
    store.updateFromFlows([
      { flowId: 'f1', currentState: 'RETURNED_FOR_REWORK' } as never,
      { flowId: 'f2', currentState: 'PENDING_REVIEW' } as never,
    ])
    expect(store.reworkPendingCount).toBe(1)
  })

  it('reworkPendingCount excludes flows already marked seen', () => {
    const store = useNotificationsStore()
    store.updateFromFlows([
      { flowId: 'f1', currentState: 'RETURNED_FOR_REWORK' } as never,
      { flowId: 'f2', currentState: 'RETURNED_FOR_REWORK' } as never,
    ])
    expect(store.reworkPendingCount).toBe(2)

    store.markSeen('f1')
    expect(store.reworkPendingCount).toBe(1)
  })

  it('reworkPendingCount is 0 when no rework flows present', () => {
    const store = useNotificationsStore()
    store.updateFromFlows([
      { flowId: 'f1', currentState: 'APPROVED' } as never,
    ])
    expect(store.reworkPendingCount).toBe(0)
  })
})
