<template>
  <div aria-live="polite">Completing sign-in…</div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { handleCallback } from '@/auth/oidc'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

onMounted(async () => {
  try {
    const user = await handleCallback()
    await auth.loadUser()
    const returnPath = (user.state as string | undefined) ?? '/submissions'
    await router.replace(returnPath)
  } catch {
    await router.replace('/submissions')
  }
})
</script>
