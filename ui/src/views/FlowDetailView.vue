<template>
  <div class="flow-detail">
    <RouterLink :to="{ name: 'my-submissions' }" class="flow-detail__back" aria-label="Back to submissions">
      ← My Submissions
    </RouterLink>

    <div v-if="isLoading" aria-live="polite">Loading flow…</div>
    <div v-else-if="error" class="flow-detail__error" role="alert">{{ error }}</div>

    <template v-else-if="richFlow">
      <div class="flow-detail__header">
        <h1 class="flow-detail__title">{{ richFlow.definitionKey ?? 'Flow' }}</h1>
        <FlowStatusBadge :state="richFlow.currentState ?? 'PENDING_REVIEW'" />
      </div>

      <!-- Rework notice (FR-011) -->
      <section
        v-if="richFlow.currentState === 'RETURNED_FOR_REWORK'"
        class="flow-detail__rework"
        aria-labelledby="rework-heading"
      >
        <h2 id="rework-heading">Returned for Rework</h2>
        <p v-if="rejectionComment" class="flow-detail__rejection-comment">
          <strong>Reviewer comment:</strong> {{ rejectionComment }}
        </p>
        <button
          class="flow-detail__btn flow-detail__btn--resubmit"
          :disabled="resubmitting"
          aria-label="Re-submit this document for review"
          @click="onResubmit"
        >
          {{ resubmitting ? 'Submitting…' : 'Re-submit for Review' }}
        </button>
      </section>

      <!-- Document data (decoded from documentRef) -->
      <section class="flow-detail__form-data" aria-labelledby="form-data-heading">
        <h2 id="form-data-heading">Submission Details</h2>
        <dl class="form-data-list">
          <template v-for="(value, key) in documentData" :key="key">
            <dt class="form-data-list__term">{{ String(key) }}</dt>
            <dd class="form-data-list__def">{{ value ?? '—' }}</dd>
          </template>
          <template v-if="!Object.keys(documentData).length">
            <dt class="form-data-list__term">Reference</dt>
            <dd class="form-data-list__def">{{ richFlow.terminalOutcome ?? '—' }}</dd>
          </template>
        </dl>
      </section>

      <!-- Audit timeline -->
      <section class="flow-detail__timeline" aria-labelledby="timeline-heading">
        <h2 id="timeline-heading">Activity Timeline</h2>
        <ol v-if="richFlow.history?.length" class="timeline-list" aria-label="Flow audit history">
          <li
            v-for="(entry, idx) in richFlow.history"
            :key="idx"
            class="timeline-list__entry"
          >
            <span class="timeline-list__type">{{ formatEventType(entry.type) }}</span>
            <span v-if="entry.actorId" class="timeline-list__actor">by {{ entry.actorId }}</span>
            <time class="timeline-list__time" :datetime="entry.occurredAt">
              {{ formatDate(entry.occurredAt) }}
            </time>
          </li>
        </ol>
        <p v-else class="flow-detail__empty">No activity yet.</p>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useFlows } from '@/composables/useFlows'
import { useErrorHandler } from '@/composables/useErrorHandler'
import FlowStatusBadge from '@/components/FlowStatusBadge.vue'
import type { RichFlowStatus } from '@/api/models'

const route = useRoute()
const router = useRouter()
const { fetchFlow, currentFlow, isLoading, error, submitFlow } = useFlows()
const { handleError } = useErrorHandler()

const resubmitting = ref(false)

const richFlow = computed(() => currentFlow.value as RichFlowStatus | null)

const documentData = computed((): Record<string, unknown> => {
  const flow = richFlow.value
  if (!flow) return {}
  const docRef = (flow as RichFlowStatus & { documentRef?: string }).documentRef
  if (!docRef) return {}
  try {
    return JSON.parse(docRef) as Record<string, unknown>
  } catch {
    return { reference: docRef }
  }
})

const rejectionComment = computed(() => {
  const history = richFlow.value?.history ?? []
  const lastDecision = [...history].reverse().find((e) => e.type === 'DECISION_RECORDED')
  return (lastDecision?.detail as Record<string, unknown> | undefined)?.comment as string | undefined
})

onMounted(async () => {
  const flowId = route.params.flowId as string
  try {
    await fetchFlow(flowId)
  } catch {
    // error already set in composable
  }
})

function formatEventType(type: string | undefined): string {
  if (!type) return 'Event'
  return type.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())
}

function formatDate(iso: string | undefined): string {
  if (!iso) return '—'
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))
}

async function onResubmit(): Promise<void> {
  const flow = richFlow.value
  if (!flow) return
  resubmitting.value = true
  try {
    const result = await submitFlow({
      definitionId: flow.definitionKey ?? '',
      formData: documentData.value as Record<string, string>,
    })
    router.push({ name: 'flow-detail', params: { flowId: result.flowId } })
  } catch (e) {
    handleError(e)
  } finally {
    resubmitting.value = false
  }
}
</script>

<style scoped>
.flow-detail { max-width: 720px; }
.flow-detail__back { display: inline-block; margin-bottom: 1rem; color: #1565c0; text-decoration: none; }
.flow-detail__back:hover { text-decoration: underline; }
.flow-detail__header { display: flex; align-items: center; gap: 1rem; margin-bottom: 1.5rem; }
.flow-detail__title { margin: 0; }
.flow-detail__error { color: #c62828; }
.flow-detail__empty { color: #757575; font-style: italic; }

.flow-detail__rework {
  background: #fff3e0;
  border-left: 4px solid #e65100;
  padding: 1rem 1.25rem;
  border-radius: 4px;
  margin-bottom: 1.5rem;
}
.flow-detail__rework h2 { margin: 0 0 0.5rem; color: #e65100; }
.flow-detail__rejection-comment { margin: 0 0 1rem; }

.flow-detail__btn {
  padding: 0.55rem 1.2rem;
  border: none;
  border-radius: 4px;
  font-size: 0.95rem;
  cursor: pointer;
}
.flow-detail__btn:disabled { opacity: 0.5; cursor: not-allowed; }
.flow-detail__btn--resubmit { background: #1565c0; color: #fff; }
.flow-detail__btn--resubmit:hover:not(:disabled) { background: #0d47a1; }

.flow-detail__form-data { margin-bottom: 2rem; }
.form-data-list { display: grid; grid-template-columns: 180px 1fr; gap: 0.4rem 1rem; }
.form-data-list__term { font-weight: 600; text-transform: capitalize; }

.flow-detail__timeline { margin-bottom: 2rem; }
.timeline-list { list-style: none; padding: 0; margin: 0; }
.timeline-list__entry {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  padding: 0.5rem 0;
  border-bottom: 1px solid #f0f0f0;
}
.timeline-list__entry:last-child { border-bottom: none; }
.timeline-list__type { font-weight: 600; }
.timeline-list__actor { color: #616161; }
.timeline-list__time { margin-left: auto; font-size: 0.82rem; color: #9e9e9e; }
</style>
