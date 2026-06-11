import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

// vi.hoisted variables are available inside vi.mock factories (they're moved to the top)
const mockState = vi.hoisted(() => ({
  flows: [] as { flowId: string; currentState?: string; definitionKey?: string }[],
  isLoading: false,
  defs: [{ definitionId: 'document-approval', name: 'Document Approval', initiatorGroup: 'initiators', fields: [] }],
}))

vi.mock('sit-onyx', async () => {
  const { h } = await import('vue')
  return {
    OnyxButton: {
      name: 'OnyxButton',
      props: ['label', 'color', 'mode', 'link'],
      setup(props: { label?: string }) {
        return () => h('a', { class: 'onyx-button-stub', 'data-label': props.label }, props.label)
      },
    },
    OnyxEmpty: {
      name: 'OnyxEmpty',
      setup(_: unknown, { slots }: { slots: Record<string, (() => unknown) | undefined> }) {
        return () =>
          h('div', { class: 'onyx-empty-stub' }, [
            slots.default?.(),
            slots.description?.(),
            slots.buttons?.(),
          ])
      },
    },
  }
})

vi.mock('@/api/definitions', () => ({
  get AVAILABLE_DEFINITIONS() {
    return mockState.defs
  },
  getDefinition: (id: string) => mockState.defs.find((d) => d.definitionId === id),
}))

vi.mock('@/composables/useFlows', async () => {
  const { ref } = await import('vue')
  return {
    useFlows: () => ({
      submittedFlows: ref(mockState.flows),
      isLoading: ref(mockState.isLoading),
      error: ref(null),
      fetchSubmittedFlows: vi.fn().mockResolvedValue(undefined),
    }),
  }
})

import MySubmissionsView from '@/views/MySubmissionsView.vue'
import { useAuthStore } from '@/stores/auth'

// ---------------------------------------------------------------------------

function setupAuth(groups: string[]) {
  const auth = useAuthStore()
  auth.user = groups.length ? { id: 'u1', name: 'Test User', groups } : null
  return auth
}

function mountView() {
  return mount(MySubmissionsView, {
    global: {
      stubs: { RouterLink: true, FlowStatusBadge: true },
    },
  })
}

function findSubmitButtons(wrapper: ReturnType<typeof mount>) {
  return wrapper
    .findAll('.onyx-button-stub')
    .filter((b) => b.attributes('data-label') === 'Submit new document')
}

// ---------------------------------------------------------------------------

describe('MySubmissionsView', () => {
  beforeEach(() => {
    mockState.flows = []
    mockState.isLoading = false
    mockState.defs = [
      { definitionId: 'document-approval', name: 'Document Approval', initiatorGroup: 'initiators', fields: [] },
    ]
  })

  // T002 — FR-001, FR-002: initiator with N > 0 submissions → header CTA visible, no OnyxEmpty
  it('T002: header CTA visible for initiator with existing submissions', async () => {
    setupAuth(['initiators'])
    mockState.flows = [{ flowId: 'f1', currentState: 'PENDING_REVIEW', definitionKey: 'document-approval' }]

    const wrapper = mountView()
    await wrapper.vm.$nextTick()

    expect(findSubmitButtons(wrapper).length).toBeGreaterThan(0)
    expect(wrapper.find('.onyx-empty-stub').exists()).toBe(false)
  })

  // T003 — FR-002: CTA present regardless of submission count
  it('T003: header CTA still present with multiple existing submissions', async () => {
    setupAuth(['initiators'])
    mockState.flows = Array.from({ length: 5 }, (_, i) => ({
      flowId: `f${i}`,
      currentState: 'APPROVED',
      definitionKey: 'document-approval',
    }))

    const wrapper = mountView()
    await wrapper.vm.$nextTick()

    expect(findSubmitButtons(wrapper).length).toBeGreaterThan(0)
  })

  // T004 — FR-006: non-initiator → no CTA at all
  it('T004: non-initiator sees no CTA', async () => {
    setupAuth(['reviewers'])

    const wrapper = mountView()
    await wrapper.vm.$nextTick()

    expect(findSubmitButtons(wrapper).length).toBe(0)
  })

  // T004b — edge case: user in both initiator and reviewer groups → CTA still shown
  it('T004b: user in both groups still sees CTA', async () => {
    setupAuth(['initiators', 'reviewers'])

    const wrapper = mountView()
    await wrapper.vm.$nextTick()

    expect(findSubmitButtons(wrapper).length).toBeGreaterThan(0)
  })

  // T005 — edge case: loading state → CTA independent of load status
  it('T005: header CTA visible while submissions are loading', async () => {
    setupAuth(['initiators'])
    mockState.isLoading = true

    const wrapper = mountView()
    await wrapper.vm.$nextTick()

    expect(findSubmitButtons(wrapper).length).toBeGreaterThan(0)
  })

  // T010 — FR-004, FR-005: initiator with 0 submissions → OnyxEmpty + secondary CTA
  it('T010: initiator with no submissions sees OnyxEmpty with message and secondary CTA', async () => {
    setupAuth(['initiators'])
    // flows stays empty (default)

    const wrapper = mountView()
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.onyx-empty-stub').exists()).toBe(true)
    expect(wrapper.find('.onyx-empty-stub').text().toLowerCase()).toContain('not submitted')
    expect(findSubmitButtons(wrapper).length).toBeGreaterThan(0)
  })

  // T011 — FR-007: no definitions → no CTA, informative message
  it('T011: no definitions → no CTA and informative message shown', async () => {
    setupAuth(['initiators'])
    mockState.defs = []

    const wrapper = mountView()
    await wrapper.vm.$nextTick()

    expect(findSubmitButtons(wrapper).length).toBe(0)
    expect(wrapper.find('.onyx-empty-stub').text().toLowerCase()).toContain('no submission types')
  })

  // T012 — FR-006 + FR-004: non-initiator with 0 submissions → OnyxEmpty, no CTA, permission message
  it('T012: non-initiator with no submissions sees OnyxEmpty without CTA', async () => {
    setupAuth(['reviewers'])

    const wrapper = mountView()
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.onyx-empty-stub').exists()).toBe(true)
    expect(findSubmitButtons(wrapper).length).toBe(0)
    expect(wrapper.find('.onyx-empty-stub').text().toLowerCase()).toContain('permission')
  })
})
