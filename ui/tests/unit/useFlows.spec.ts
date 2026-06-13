import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useFlows } from '@/composables/useFlows'

vi.mock('@/api/flows', () => ({
  submitFlow: vi.fn(),
  getFlow: vi.fn(),
  getSubmitterFlows: vi.fn(),
}))

import * as flowsApi from '@/api/flows'

describe('useFlows', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('submitFlow calls api.submitFlow with correct payload and updates state', async () => {
    const mockResponse = { flowId: 'abc-123', state: 'PENDING_REVIEW', definitionId: 'doc-approval', submitterId: 'alice', submitterGroups: [], startedAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' }
    vi.mocked(flowsApi.submitFlow).mockResolvedValue(mockResponse)

    const { submitFlow, currentFlow, isLoading, error } = useFlows()

    expect(isLoading.value).toBe(false)
    const result = await submitFlow({ definitionId: 'doc-approval', formData: { title: 'Test' } })

    expect(flowsApi.submitFlow).toHaveBeenCalledWith({ definitionId: 'doc-approval', formData: { title: 'Test' } })
    expect(result).toEqual(mockResponse)
    expect(currentFlow.value).toEqual(mockResponse)
    expect(error.value).toBeNull()
  })

  it('submitFlow sets error state on failure', async () => {
    vi.mocked(flowsApi.submitFlow).mockRejectedValue(new Error('Network error'))

    const { submitFlow, error } = useFlows()
    await expect(submitFlow({ definitionId: 'doc-approval', formData: {} })).rejects.toThrow()
    expect(error.value).toBeTruthy()
  })

  it('getSubmitterFlows populates submittedFlows', async () => {
    const mockFlows = [
      { flowId: 'f1', state: 'PENDING_REVIEW', definitionId: 'doc-approval', submitterId: 'alice', submitterGroups: [], startedAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' },
    ]
    vi.mocked(flowsApi.getSubmitterFlows).mockResolvedValue(mockFlows)

    const { fetchSubmittedFlows, submittedFlows } = useFlows()
    await fetchSubmittedFlows()

    expect(submittedFlows.value).toEqual(mockFlows)
  })

  it('submittedFlows holds all returned states without transformation', async () => {
    const mockFlows = [
      { flowId: 'f1', currentState: 'PENDING_REVIEW', definitionKey: 'doc-approval' },
      { flowId: 'f2', currentState: 'APPROVED', definitionKey: 'doc-approval' },
      { flowId: 'f3', currentState: 'RETURNED_FOR_REWORK', definitionKey: 'doc-approval' },
    ]
    vi.mocked(flowsApi.getSubmitterFlows).mockResolvedValue(mockFlows as never)

    const { fetchSubmittedFlows, submittedFlows, isLoading } = useFlows()

    expect(isLoading.value).toBe(false)
    await fetchSubmittedFlows()

    expect(submittedFlows.value).toHaveLength(3)
    expect(submittedFlows.value[0].currentState).toBe('PENDING_REVIEW')
    expect(submittedFlows.value[1].currentState).toBe('APPROVED')
    expect(submittedFlows.value[2].currentState).toBe('RETURNED_FOR_REWORK')
    expect(isLoading.value).toBe(false)
  })
})
