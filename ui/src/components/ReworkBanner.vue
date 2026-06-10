<template>
  <RouterLink
    v-if="count > 0"
    :to="{ name: 'my-submissions' }"
    class="rework-banner"
    :aria-label="`${count} document${count === 1 ? '' : 's'} returned for rework`"
  >
    <span class="rework-banner__badge" aria-hidden="true">{{ count }}</span>
    <span class="rework-banner__label">Rework needed</span>
  </RouterLink>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useNotificationsStore } from '@/stores/notifications'

const notifications = useNotificationsStore()
const count = computed(() => notifications.reworkPendingCount)
</script>

<style scoped>
.rework-banner {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.25rem 0.6rem;
  background: #fff3e0;
  border: 1px solid #e65100;
  border-radius: 12px;
  text-decoration: none;
  color: #e65100;
  font-size: 0.82rem;
  font-weight: 600;
}
.rework-banner:hover { background: #ffe0b2; }
.rework-banner__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.2rem;
  height: 1.2rem;
  background: #e65100;
  color: #fff;
  border-radius: 50%;
  font-size: 0.75rem;
  padding: 0 0.2rem;
}
</style>
