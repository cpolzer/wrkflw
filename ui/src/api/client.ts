import { userManager } from '@/auth/oidc'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'
const TIMEOUT_MS = Number(import.meta.env.VITE_API_TIMEOUT_MS ?? 10000)

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function getAuthHeaders(): Promise<Record<string, string>> {
  const user = await userManager.getUser()
  if (!user) return {}
  const headers: Record<string, string> = {
    Authorization: `Bearer ${user.access_token}`,
    // Compatibility shim until backend reads JWT claims directly
    'X-Actor-Id': user.profile.sub ?? '',
    'X-Actor-Groups': ((user.profile['groups'] as string[] | undefined) ?? []).join(','),
  }
  return headers
}

async function request<T>(
  path: string,
  options: RequestInit = {},
  retry = true,
): Promise<T> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS)

  const authHeaders = await getAuthHeaders()
  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    signal: controller.signal,
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders,
      ...options.headers,
    },
  }).finally(() => clearTimeout(timer))

  if (response.status === 401 && retry) {
    try {
      await userManager.signinSilent()
      return request<T>(path, options, false)
    } catch {
      await userManager.signinRedirect({ state: window.location.pathname })
      return new Promise(() => {}) // navigation pending
    }
  }

  if (!response.ok) {
    const text = await response.text().catch(() => '')
    throw new ApiError(response.status, text || response.statusText)
  }

  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body !== undefined ? JSON.stringify(body) : undefined }),
}
