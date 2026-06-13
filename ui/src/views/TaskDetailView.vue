<template>
  <div class="task-detail">
    <RouterLink :to="{ name: 'worklist' }" class="task-detail__back" aria-label="Back to worklist">
      ← Worklist
    </RouterLink>

    <div v-if="isLoading" aria-live="polite">Loading task…</div>
    <div v-else-if="error" class="task-detail__error" role="alert">{{ error }}</div>

    <template v-else-if="task">
      <h1 class="task-detail__title">{{ task.stateName }}</h1>
      <p class="task-detail__meta">
        Flow: <strong>{{ task.flowId }}</strong>
      </p>

      <section class="task-detail__form-data" aria-labelledby="form-data-heading">
        <h2 id="form-data-heading">Document Reference</h2>
        <dl class="form-data-list">
          <template v-for="(value, key) in documentData" :key="key">
            <dt class="form-data-list__term">{{ String(key) }}</dt>
            <dd class="form-data-list__def">{{ value ?? '—' }}</dd>
          </template>
          <template v-if="!Object.keys(documentData).length">
            <dt class="form-data-list__term">Reference</dt>
            <dd class="form-data-list__def">{{ flow?.terminalOutcome ?? 'N/A' }}</dd>
          </template>
        </dl>
      </section>

      <section class="task-detail__actions" aria-labelledby="actions-heading">
        <h2 id="actions-heading">Decision</h2>

        <div class="task-detail__buttons">
          <button
            class="task-detail__btn task-detail__btn--approve"
            data-action="approve"
            :disabled="deciding"
            aria-label="Approve this task"
            @click="onDecision('APPROVE')"
          >
            Approve
          </button>
          <button
            class="task-detail__btn task-detail__btn--reject"
            :disabled="deciding"
            aria-label="Reject this task"
            @click="showRejectDialog = true"
          >
            Reject
          </button>
        </div>
      </section>

      <div
        v-if="outcome"
        class="task-detail__outcome"
        role="status"
        :class="outcome === 'APPROVE' ? 'task-detail__outcome--approved' : 'task-detail__outcome--rejected'"
      >
        {{ outcome === 'APPROVE' ? 'Approved' : 'Rejected' }} — flow updated.
      </div>
    </template>

    <!-- Reject dialog -->
    <dialog
      ref="rejectDialog"
      class="task-detail__dialog"
      aria-labelledby="reject-dialog-title"
      aria-modal="true"
    >
      <h3 id="reject-dialog-title">Reject Document</h3>
      <label for="reject-comment">Reason for rejection <span aria-label="required">*</span></label>
      <textarea
        id="reject-comment"
        v-model="rejectComment"
        class="task-detail__dialog-textarea"
        rows="4"
        aria-required="true"
        :aria-invalid="commentError ? 'true' : undefined"
        :aria-describedby="commentError ? 'comment-error' : undefined"
        placeholder="Provide a reason for the reviewer and submitter…"
      />
      <span v-if="commentError" id="comment-error" class="task-detail__dialog-error" role="alert">{{ commentError }}</span>
      <div class="task-detail__dialog-actions">
        <button
          class="task-detail__btn task-detail__btn--reject"
          data-action="reject"
          :disabled="deciding"
          aria-label="Confirm rejection"
          @click="onDecision('REJECT')"
        >Confirm Reject</button>
        <button
          class="task-detail__btn"
          :disabled="deciding"
          aria-label="Cancel rejection"
          @click="showRejectDialog = false; commentError = ''"
        >Cancel</button>
      </div>
    </dialog>
    <div v-if="showRejectDialog" class="task-detail__overlay" @click="showRejectDialog = false" />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getFlow } from '@/api/flows'
import { useWorklist } from '@/composables/useWorklist'
import { useErrorHandler } from '@/composables/useErrorHandler'
import type { FlowStatusWithHistory, TaskSummary } from '@/api/models'
import { ApiError } from '@/api/client'

const route = useRoute()
const router = useRouter()
const { submitDecision } = useWorklist()
const { handleError } = useErrorHandler()

const task = ref<TaskSummary | null>(null)
const flow = ref<FlowStatusWithHistory | null>(null)
const isLoading = ref(true)
const error = ref<string | null>(null)
const deciding = ref(false)
const outcome = ref<'APPROVE' | 'REJECT' | null>(null)

const showRejectDialog = ref(false)
const rejectComment = ref('')
const commentError = ref('')

const documentData = computed((): Record<string, unknown> => {
  const doc = (flow.value as (FlowStatusWithHistory & { documentRef?: string }) | null)?.documentRef
  if (!doc) return {}
  try {
    return JSON.parse(doc) as Record<string, unknown>
  } catch {
    return { reference: doc }
  }
})

onMounted(async () => {
  const taskId = route.params.taskId as string
  try {
    const flowId = history.state?.flowId as string | undefined
    if (!flowId) {
      error.value = 'Task not found. Please return to the worklist.'
      return
    }
    flow.value = await getFlow(flowId)
    task.value = flow.value.pendingTasks?.find((t) => t.taskId === taskId) ?? null
    if (!task.value) error.value = 'Task is no longer available.'
  } catch (e) {
    if (e instanceof ApiError && e.status === 403) {
      error.value = 'You do not have access to this task.'
    } else {
      error.value = handleError(e)
    }
  } finally {
    isLoading.value = false
  }
})

watch(showRejectDialog, (open) => {
  if (open) rejectComment.value = ''
})

async function onDecision(decision: 'APPROVE' | 'REJECT'): Promise<void> {
  if (decision === 'REJECT') {
    if (!rejectComment.value.trim()) {
      commentError.value = 'A rejection reason is required.'
      return
    }
    commentError.value = ''
  }

  deciding.value = true
  try {
    const taskId = route.params.taskId as string
    await submitDecision(taskId, {
      outcome: decision,
      comment: decision === 'REJECT' ? rejectComment.value : undefined,
    })
    outcome.value = decision
    showRejectDialog.value = false
    setTimeout(() => router.push({ name: 'worklist' }), 1500)
  } catch (e) {
    handleError(e)
  } finally {
    deciding.value = false
  }
}
</script>

<style scoped>
.task-detail { max-width: 720px; }
.task-detail__back { display: inline-block; margin-bottom: 1rem; color: #1565c0; text-decoration: none; }
.task-detail__back:hover { text-decoration: underline; }
.task-detail__title { margin-bottom: 0.25rem; }
.task-detail__meta { color: #616161; margin-bottom: 1.5rem; }
.task-detail__form-data { margin-bottom: 2rem; }
.form-data-list { display: grid; grid-template-columns: 180px 1fr; gap: 0.4rem 1rem; }
.form-data-list__term { font-weight: 600; text-transform: capitalize; }
.task-detail__buttons { display: flex; gap: 0.75rem; }
.task-detail__btn {
  padding: 0.55rem 1.2rem;
  border: none;
  border-radius: 4px;
  font-size: 0.95rem;
  cursor: pointer;
}
.task-detail__btn:disabled { opacity: 0.5; cursor: not-allowed; }
.task-detail__btn--approve { background: #2e7d32; color: #fff; }
.task-detail__btn--approve:hover:not(:disabled) { background: #1b5e20; }
.task-detail__btn--reject { background: #c62828; color: #fff; }
.task-detail__btn--reject:hover:not(:disabled) { background: #b71c1c; }
.task-detail__outcome { margin-top: 1.5rem; padding: 0.75rem 1rem; border-radius: 4px; font-weight: 500; }
.task-detail__outcome--approved { background: #e8f5e9; color: #2e7d32; }
.task-detail__outcome--rejected { background: #ffebee; color: #c62828; }
.task-detail__error { color: #c62828; }
.task-detail__dialog {
  position: fixed;
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  border: none;
  border-radius: 8px;
  padding: 1.5rem;
  width: 480px;
  max-width: 95vw;
  z-index: 1000;
  box-shadow: 0 4px 24px rgba(0,0,0,0.25);
}
.task-detail__dialog-textarea {
  width: 100%; padding: 0.5rem; border: 1px solid #bdbdbd; border-radius: 4px; resize: vertical; margin-top: 0.5rem;
}
.task-detail__dialog-actions { display: flex; gap: 0.75rem; margin-top: 1rem; }
.task-detail__dialog-error { color: #c62828; font-size: 0.82rem; }
.task-detail__overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.4); z-index: 999;
}
</style>
