import { UserManager, WebStorageStateStore, type User } from 'oidc-client-ts'

export const userManager = new UserManager({
  authority: import.meta.env.VITE_OIDC_AUTHORITY ?? 'http://localhost:8180/realms/wrkflw',
  client_id: import.meta.env.VITE_OIDC_CLIENT_ID ?? 'wrkflw-ui',
  redirect_uri: `${window.location.origin}/auth/callback`,
  post_logout_redirect_uri: window.location.origin,
  response_type: 'code',
  scope: 'openid profile email',
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  automaticSilentRenew: true,
  silent_redirect_uri: `${window.location.origin}/auth/silent-renew`,
})

export async function getUser(): Promise<User | null> {
  return userManager.getUser()
}

export async function login(returnPath?: string): Promise<void> {
  await userManager.signinRedirect({ state: returnPath })
}

export async function handleCallback(): Promise<User> {
  return userManager.signinRedirectCallback()
}

export async function silentRenew(): Promise<void> {
  await userManager.signinSilentCallback()
}

export async function logout(): Promise<void> {
  await userManager.signoutRedirect()
}
