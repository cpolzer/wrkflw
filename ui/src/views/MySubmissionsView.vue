<template>
  <div class="submissions-view">
    <h1>My Submissions</h1>

    <div v-if="isLoading" aria-live="polite">Loading submissions…</div>
    <div v-else-if="error" class="submissions-view__error" role="alert">{{ error }}</div>

    <template v-else>
      <table v-if="submittedFlows.length" class="submissions-table" aria-label="My submitted flows">
        <thead>
          <tr>
            <th scope="col">Definition</th>
            <th scope="col">Status</th>
            <th scope="col">Details</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="flow in richFlows" :key="flow.flowId">
            <td>{{ flow.definitionKey ?? '—' }}</td>
            <td>
              <FlowStatusBadge :state="flow.currentState ?? 'PENDING_REVIEW'" />
            </td>
            <td>
              <RouterLink
                :to="{ name: 'flow-detail', params: { flowId: flow.flowId } }"
                class="submissions-table__link"
                :aria-label="`View details for flow ${flow.flowId}`"
              >
                View
              </RouterLink>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-else class="submissions-view__empty">No submissions yet.</p>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useFlows } from '@/composables/useFlows'
import FlowStatusBadge from '@/components/FlowStatusBadge.vue'
import type { RichFlowStatus } from '@/api/models'

const { submittedFlows, isLoading, error, fetchSubmittedFlows } = useFlows()

const richFlows = computed(() => submittedFlows.value as RichFlowStatus[])

onMounted(fetchSubmittedFlows)
</script>

<style scoped>
.submissions-view h1 { margin-bottom: 1.5rem; }
.submissions-view__empty { color: #757575; font-style: italic; }
.submissions-view__error { color: #c62828; }
.submissions-table { width: 100%; border-collapse: collapse; }
.submissions-table th,
.submissions-table td {
  padding: 0.6rem 0.75rem;
  text-align: left;
  border-bottom: 1px solid #e0e0e0;
}
.submissions-table th { font-weight: 600; background: #f5f5f5; }
.submissions-table__link { color: #1565c0; text-decoration: none; }
.submissions-table__link:hover { text-decoration: underline; }
</style>
