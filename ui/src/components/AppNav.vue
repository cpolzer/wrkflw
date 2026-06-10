<template>
  <nav class="app-nav" aria-label="Main navigation">
    <div class="app-nav__brand">wrkflw</div>
    <ul class="app-nav__links" role="list">
      <li>
        <RouterLink :to="{ name: 'my-submissions' }" aria-label="My Submissions">
          My Submissions
        </RouterLink>
      </li>
      <li v-if="reworkCount > 0">
        <ReworkBanner />
      </li>
      <li>
        <RouterLink :to="{ name: 'worklist' }" aria-label="My Worklist">
          My Worklist
        </RouterLink>
      </li>
    </ul>
    <button class="app-nav__logout" aria-label="Sign out" @click="auth.signOut()">
      Sign out
    </button>
  </nav>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useNotificationsStore } from '@/stores/notifications'
import { useFlows } from '@/composables/useFlows'
import ReworkBanner from './ReworkBanner.vue'

const auth = useAuthStore()
const notifications = useNotificationsStore()
const { fetchSubmittedFlows } = useFlows()
const reworkCount = computed(() => notifications.reworkPendingCount)

onMounted(async () => {
  if (auth.isAuthenticated) {
    try {
      await fetchSubmittedFlows()
    } catch {
      // Non-critical — badge just won't show
    }
  }
})
</script>

<style scoped>
.app-nav {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  padding: 0.75rem 1.5rem;
  background: var(--onyx-color-base-neutral-900, #1a1a2e);
  color: #fff;
}
.app-nav__brand {
  font-weight: 700;
  font-size: 1.1rem;
  margin-right: auto;
}
.app-nav__links {
  display: flex;
  gap: 1rem;
  list-style: none;
  margin: 0;
  padding: 0;
}
.app-nav__links a {
  color: #fff;
  text-decoration: none;
  padding: 0.4rem 0.75rem;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}
.app-nav__links a:hover,
.app-nav__links a.router-link-active {
  background: rgba(255, 255, 255, 0.15);
}
.app-nav__badge {
  background: #e53935;
  border-radius: 999px;
  font-size: 0.7rem;
  padding: 0.1rem 0.45rem;
  font-weight: 700;
}
.app-nav__logout {
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.4);
  color: #fff;
  padding: 0.35rem 0.75rem;
  border-radius: 4px;
  cursor: pointer;
}
.app-nav__logout:hover {
  background: rgba(255, 255, 255, 0.1);
}
</style>
