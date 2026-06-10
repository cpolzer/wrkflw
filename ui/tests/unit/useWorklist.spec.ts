import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useWorklist } from '@/composables/useWorklist'

vi.mock('@/api/worklist', () => ({
  getGroupWorklist: vi.fn(),
  getMyTasks: vi.fn(),
}))
vi.mock('@/api/tasks', () => ({
  claimTask: vi.fn(),
  releaseTask: vi.fn(),
  submitDecision: vi.fn(),
}))

import * as worklistApi from '@/api/worklist'
import * as tasksApi from '@/api/tasks'

const mockTask = {
  taskId: 't1',
  flowId: 'f1',
  stateName: 'Legal Review',
  candidateGroupId: 'legal-reviewers',
  ownerId: null,
  status: 'PENDING' as const,
}

describe('useWorklist', () => {
  beforeEach(() => vi.clearAllMocks())

  it('fetchGroupTasks populates groupTasks on mount', async () => {
    vi.mocked(worklistApi.getGroupWorklist).mockResolvedValue([mockTask])
    vi.mocked(worklistApi.getMyTasks).mockResolvedValue([])

    const { fetchAll, groupTasks } = useWorklist()
    await fetchAll()

    expect(worklistApi.getGroupWorklist).toHaveBeenCalledOnce()
    expect(groupTasks.value).toHaveLength(1)
    expect(groupTasks.value[0].taskId).toBe('t1')
  })

  it('claimTask calls api and updates task state reactively', async () => {
    const claimed = { ...mockTask, status: 'CLAIMED' as const, ownerId: 'bob' }
    vi.mocked(worklistApi.getGroupWorklist).mockResolvedValue([mockTask])
    vi.mocked(worklistApi.getMyTasks).mockResolvedValue([])
    vi.mocked(tasksApi.claimTask).mockResolvedValue(claimed)

    const { fetchAll, claimTask, groupTasks } = useWorklist()
    await fetchAll()
    await claimTask('t1')

    expect(tasksApi.claimTask).toHaveBeenCalledWith('t1')
    // Task should be removed from unclaimed group list
    expect(groupTasks.value.find((t) => t.taskId === 't1')).toBeUndefined()
  })
})
