import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getUser, login, logout } from '@/auth/oidc'

export interface AuthUser {
  id: string
  name: string
  groups: string[]
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(null)
  const isAuthenticated = computed(() => user.value !== null)

  async function loadUser(): Promise<void> {
    const oidcUser = await getUser()
    if (!oidcUser || oidcUser.expired) {
      user.value = null
      return
    }
    const claims = oidcUser.profile
    user.value = {
      id: claims.sub ?? '',
      name: claims.name ?? claims.preferred_username ?? claims.sub ?? '',
      groups: (claims['groups'] as string[] | undefined) ?? [],
    }
  }

  async function signIn(returnPath?: string): Promise<void> {
    await login(returnPath)
  }

  async function signOut(): Promise<void> {
    user.value = null
    await logout()
  }

  function isInGroup(group: string): boolean {
    return user.value?.groups.includes(group) ?? false
  }

  return { user, isAuthenticated, loadUser, signIn, signOut, isInGroup }
})
