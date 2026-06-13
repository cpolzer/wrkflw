<template>
  <div class="worklist-view">
    <h1>My Worklist</h1>

    <div v-if="isLoading" aria-live="polite">Loading tasks…</div>
    <div v-else-if="error" class="worklist-view__error" role="alert">{{ error }}</div>

    <template v-else>
      <section aria-labelledby="group-tasks-heading">
        <h2 id="group-tasks-heading">Unclaimed Tasks</h2>
        <table v-if="groupTasks.length" class="worklist-table" aria-label="Unclaimed group tasks">
          <thead>
            <tr>
              <th scope="col">Flow ID</th>
              <th scope="col">Stage</th>
              <th scope="col">Group</th>
              <th scope="col">Action</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="task in groupTasks" :key="task.taskId">
              <td>{{ task.flowId?.slice(0, 8) ?? '—' }}</td>
              <td>{{ task.stateName ?? '—' }}</td>
              <td>{{ task.candidateGroupId ?? '—' }}</td>
              <td>
                <button
                  class="worklist-table__btn"
                  :disabled="claimingId === task.taskId"
                  :aria-label="`Claim task ${task.taskId}`"
                  @click="onClaim(task.taskId ?? '')"
                >
                  {{ claimingId === task.taskId ? 'Claiming…' : 'Claim' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="worklist-view__empty">No unclaimed tasks for your groups.</p>
      </section>

      <section aria-labelledby="my-tasks-heading" class="worklist-view__mine">
        <h2 id="my-tasks-heading">My Claimed Tasks</h2>
        <table v-if="myTasks.length" class="worklist-table" aria-label="My claimed tasks">
          <thead>
            <tr>
              <th scope="col">Flow ID</th>
              <th scope="col">Stage</th>
              <th scope="col">Action</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="task in myTasks" :key="task.taskId">
              <td>{{ task.flowId?.slice(0, 8) ?? '—' }}</td>
              <td>{{ task.stateName ?? '—' }}</td>
              <td>
                <RouterLink
                  :to="{ name: 'task-detail', params: { taskId: task.taskId }, state: { flowId: task.flowId } }"
                  class="worklist-table__link"
                  :aria-label="`View task ${task.taskId}`"
                >
                  View task
                </RouterLink>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="worklist-view__empty">No tasks claimed by you.</p>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useWorklist } from '@/composables/useWorklist'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { ApiError } from '@/api/client'

const { groupTasks, myTasks, isLoading, error, fetchAll, claimTask } = useWorklist()
const { handleError } = useErrorHandler()
const claimingId = ref<string | null>(null)

onMounted(fetchAll)

async function onClaim(taskId: string): Promise<void> {
  claimingId.value = taskId
  try {
    await claimTask(taskId)
  } catch (e) {
    if (e instanceof ApiError && e.status === 409) {
      handleError(e)
      await fetchAll()
    } else {
      handleError(e)
    }
  } finally {
    claimingId.value = null
  }
}
</script>

<style scoped>
.worklist-view h1 { margin-bottom: 1.5rem; }
.worklist-view__mine { margin-top: 2rem; }
.worklist-view__empty { color: #757575; font-style: italic; }
.worklist-view__error { color: #c62828; }
.worklist-table { width: 100%; border-collapse: collapse; }
.worklist-table th, .worklist-table td {
  padding: 0.6rem 0.75rem;
  text-align: left;
  border-bottom: 1px solid #e0e0e0;
}
.worklist-table th { font-weight: 600; background: #f5f5f5; }
.worklist-table__btn {
  padding: 0.3rem 0.8rem;
  background: #1565c0;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.85rem;
}
.worklist-table__btn:disabled { opacity: 0.5; cursor: not-allowed; }
.worklist-table__link { color: #1565c0; text-decoration: none; }
.worklist-table__link:hover { text-decoration: underline; }
</style>
