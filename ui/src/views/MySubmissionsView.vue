<template>
  <div class="submissions-view">
    <header class="submissions-view__header">
      <h1>My Submissions</h1>
      <OnyxButton
        v-if="canSubmit"
        label="Submit new document"
        color="primary"
        :link="submitLink"
      />
    </header>

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
          <tr v-for="flow in submittedFlows" :key="flow.flowId">
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

      <OnyxEmpty v-else>
        You have not submitted any documents for approval yet.
        <template #description>
          <span v-if="canSubmit">
            Click &ldquo;Submit new document&rdquo; above to start your first approval request.
          </span>
          <span v-else-if="!firstDefinition">
            No submission types are currently configured.
          </span>
          <span v-else>
            You do not have permission to submit documents.
          </span>
        </template>
        <template v-if="canSubmit" #buttons>
          <OnyxButton
            label="Submit new document"
            color="primary"
            :link="submitLink"
          />
        </template>
      </OnyxEmpty>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { OnyxButton, OnyxEmpty } from 'sit-onyx'
import { useFlows } from '@/composables/useFlows'
import { useAuthStore } from '@/stores/auth'
import { AVAILABLE_DEFINITIONS } from '@/api/definitions'
import FlowStatusBadge from '@/components/FlowStatusBadge.vue'
const { submittedFlows, isLoading, error, fetchSubmittedFlows } = useFlows()
const auth = useAuthStore()

const firstDefinition = computed(() => AVAILABLE_DEFINITIONS[0])
const canSubmit = computed(
  () => !!firstDefinition.value && auth.isInGroup(firstDefinition.value.initiatorGroup),
)
const submitLink = computed(() =>
  firstDefinition.value ? { href: `/submit/${firstDefinition.value.definitionId}` } : undefined,
)

onMounted(fetchSubmittedFlows)
</script>

<style scoped>
.submissions-view__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1.5rem;
}
.submissions-view__header h1 { margin-bottom: 0; }
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
