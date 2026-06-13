import { config } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, vi } from 'vitest'

// Mock OIDC user manager to avoid browser APIs in unit tests
vi.mock('@/auth/oidc', () => ({
  userManager: { getUser: vi.fn().mockResolvedValue(null), signinSilent: vi.fn() },
  getUser: vi.fn().mockResolvedValue(null),
  login: vi.fn(),
  logout: vi.fn(),
  handleCallback: vi.fn(),
  silentRenew: vi.fn(),
}))

beforeEach(() => {
  setActivePinia(createPinia())
})
