<template>
  <div class="submit-view">
    <h1 class="submit-view__title">{{ definition?.name ?? 'Submit for Approval' }}</h1>

    <div v-if="!definition" class="submit-view__error" role="alert">
      Workflow definition not found.
    </div>

    <form v-else class="submit-view__form" novalidate @submit.prevent="onSubmit">
      <DynamicFormField
        v-for="field in definition.fields"
        :key="field.name"
        :field="field"
        :model-value="formData[field.name] ?? ''"
        :error-message="fieldErrors[field.name]"
        @update:model-value="formData[field.name] = $event"
      />

      <div v-if="submitError" class="submit-view__submit-error" role="alert">
        {{ submitError }}
      </div>

      <button
        type="submit"
        class="submit-view__btn"
        :disabled="isLoading"
        aria-label="Submit document for approval"
      >
        {{ isLoading ? 'Submitting…' : 'Submit for Approval' }}
      </button>
    </form>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useFlows } from '@/composables/useFlows'
import { getDefinition } from '@/api/definitions'
import DynamicFormField from '@/components/DynamicFormField.vue'
import type { WorkflowDefinition } from '@/api/models'
import { useAuthStore } from '@/stores/auth'
import { useErrorHandler } from '@/composables/useErrorHandler'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { handleError } = useErrorHandler()
const { submitFlow, isLoading } = useFlows()

const SESSION_KEY = 'submitFormDraft'

const definition = ref<WorkflowDefinition | undefined>(undefined)
const formData = reactive<Record<string, string>>({})
const fieldErrors = reactive<Record<string, string>>({})
const submitError = ref<string | null>(null)

onMounted(() => {
  const id = route.params.definitionId as string
  definition.value = getDefinition(id)

  // FR-002: guard — only initiators may submit
  if (definition.value && !auth.isInGroup(definition.value.initiatorGroup)) {
    router.replace({ name: 'my-submissions' })
    return
  }

  // T049: restore draft saved before session expiry redirect
  const saved = sessionStorage.getItem(SESSION_KEY)
  if (saved) {
    try {
      const draft = JSON.parse(saved) as Record<string, string>
      Object.assign(formData, draft)
    } catch {
      // ignore corrupt draft
    }
    sessionStorage.removeItem(SESSION_KEY)
  }
})

function validate(): boolean {
  let valid = true
  for (const key in fieldErrors) delete fieldErrors[key]

  for (const field of definition.value?.fields ?? []) {
    if (field.required && !formData[field.name]?.trim()) {
      fieldErrors[field.name] = `${field.label} is required.`
      valid = false
    }
  }
  return valid
}

// Persist draft on every change so auth-redirect doesn't lose progress (T049)
watch(formData, (data) => {
  if (Object.keys(data).length) {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(data))
  }
}, { deep: true })

async function onSubmit(): Promise<void> {
  if (!definition.value || !validate()) return
  submitError.value = null
  try {
    const flow = await submitFlow({
      definitionId: definition.value.definitionId,
      formData: { ...formData },
    })
    sessionStorage.removeItem(SESSION_KEY)
    await router.push({ name: 'flow-detail', params: { flowId: flow.flowId } })
  } catch (e) {
    submitError.value = handleError(e)
  }
}
</script>

<style scoped>
.submit-view { max-width: 600px; }
.submit-view__title { margin-bottom: 1.5rem; }
.submit-view__form { display: flex; flex-direction: column; }
.submit-view__btn {
  align-self: flex-start;
  padding: 0.6rem 1.5rem;
  background: #1565c0;
  color: #fff;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  cursor: pointer;
  margin-top: 0.5rem;
}
.submit-view__btn:disabled { opacity: 0.6; cursor: not-allowed; }
.submit-view__btn:hover:not(:disabled) { background: #0d47a1; }
.submit-view__submit-error { color: #c62828; margin-bottom: 0.5rem; }
.submit-view__error { color: #c62828; }
</style>
