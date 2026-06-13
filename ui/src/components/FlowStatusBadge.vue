<template>
  <span class="flow-status-badge" :class="`flow-status-badge--${state.toLowerCase().replace(/_/g, '-')}`">
    {{ label }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { FlowState } from '@/api/models'

const props = defineProps<{ state: FlowState | string }>()

const label = computed(() => {
  const map: Record<string, string> = {
    PENDING_REVIEW: 'Pending Review',
    APPROVED: 'Approved',
    REJECTED: 'Rejected',
    RETURNED_FOR_REWORK: 'Returned for Rework',
    COMPLETED: 'Completed',
  }
  return map[props.state] ?? props.state
})
</script>

<style scoped>
.flow-status-badge {
  display: inline-block;
  padding: 0.2rem 0.6rem;
  border-radius: 12px;
  font-size: 0.8rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}
.flow-status-badge--pending-review { background: #e3f2fd; color: #1565c0; }
.flow-status-badge--approved { background: #e8f5e9; color: #2e7d32; }
.flow-status-badge--rejected { background: #ffebee; color: #c62828; }
.flow-status-badge--returned-for-rework { background: #fff3e0; color: #e65100; }
.flow-status-badge--completed { background: #ede7f6; color: #4527a0; }
</style>
