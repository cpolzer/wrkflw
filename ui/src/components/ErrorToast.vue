<template>
  <div class="toast-container" aria-live="assertive" aria-atomic="true">
    <div
      v-for="toast in toasts"
      :key="toast.id"
      class="toast toast--error"
      role="alert"
    >
      {{ toast.message }}
      <button
        class="toast__close"
        @click="dismiss(toast.id)"
        aria-label="Dismiss notification"
      >×</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useErrorHandler } from '@/composables/useErrorHandler'

const { toasts, dismiss } = useErrorHandler()
</script>

<style scoped>
.toast-container {
  position: fixed;
  bottom: 1.5rem;
  right: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  z-index: 9999;
}
.toast {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  border-radius: 6px;
  min-width: 280px;
  max-width: 420px;
  font-size: 0.9rem;
  box-shadow: 0 2px 8px rgba(0,0,0,0.2);
}
.toast--error { background: #c62828; color: #fff; }
.toast__close {
  background: transparent;
  border: none;
  color: inherit;
  font-size: 1.2rem;
  cursor: pointer;
  margin-left: auto;
  padding: 0;
  line-height: 1;
}
</style>
