<template>
  <div class="form-field" :class="{ 'form-field--error': !!errorMessage }">
    <label :for="field.name" class="form-field__label">
      {{ field.label }}
      <span v-if="field.required" class="form-field__required" aria-label="required">*</span>
    </label>

    <input
      v-if="field.type === 'text'"
      :id="field.name"
      :name="field.name"
      type="text"
      class="form-field__input"
      :value="modelValue"
      :aria-required="field.required"
      :aria-describedby="errorMessage ? `${field.name}-error` : undefined"
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    />

    <textarea
      v-else-if="field.type === 'textarea'"
      :id="field.name"
      :name="field.name"
      class="form-field__input form-field__textarea"
      :value="modelValue"
      :aria-required="field.required"
      :aria-describedby="errorMessage ? `${field.name}-error` : undefined"
      @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
    />

    <select
      v-else-if="field.type === 'select'"
      :id="field.name"
      :name="field.name"
      class="form-field__input"
      :value="modelValue"
      :aria-required="field.required"
      :aria-describedby="errorMessage ? `${field.name}-error` : undefined"
      @change="emit('update:modelValue', ($event.target as HTMLSelectElement).value)"
    >
      <option value="" disabled>Select…</option>
      <option v-for="opt in field.options" :key="opt" :value="opt">{{ opt }}</option>
    </select>

    <input
      v-else-if="field.type === 'date'"
      :id="field.name"
      :name="field.name"
      type="date"
      class="form-field__input"
      :value="modelValue"
      :aria-required="field.required"
      :aria-describedby="errorMessage ? `${field.name}-error` : undefined"
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    />

    <span
      v-if="errorMessage"
      :id="`${field.name}-error`"
      class="form-field__error"
      role="alert"
    >{{ errorMessage }}</span>
  </div>
</template>

<script setup lang="ts">
import type { FieldDefinition } from '@/api/models'

defineProps<{
  field: FieldDefinition
  modelValue: string
  errorMessage?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<style scoped>
.form-field { display: flex; flex-direction: column; gap: 0.3rem; margin-bottom: 1rem; }
.form-field__label { font-weight: 500; font-size: 0.9rem; }
.form-field__required { color: #c62828; margin-left: 0.2rem; }
.form-field__input {
  padding: 0.5rem 0.75rem;
  border: 1px solid #bdbdbd;
  border-radius: 4px;
  font-size: 0.95rem;
  width: 100%;
}
.form-field__textarea { min-height: 80px; resize: vertical; }
.form-field--error .form-field__input { border-color: #c62828; }
.form-field__error { color: #c62828; font-size: 0.82rem; }
</style>
