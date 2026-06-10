# Research: Vue/Onyx Web Frontend

**Date**: 2026-06-10 | **Feature**: 003-vue-onyx-frontend

## Decision Log

### 1. Build Tooling

**Decision**: Vite  
**Rationale**: Vite is the official Vue 3 build tool. It provides instant HMR, fast cold starts, and a zero-config dev server. Onyx's own documentation and scaffolding assume Vite.  
**Alternatives considered**: Webpack (slower, more config), Nuxt 3 (SSR overhead not needed — spec assumption: static build).

---

### 2. OIDC Client Library

**Decision**: `oidc-client-ts`  
**Rationale**: Standards-compliant OAuth2/OIDC library with no vendor lock-in. Works with any OIDC-compliant IdP (Keycloak, Azure AD, etc.). Handles PKCE flow, silent renewal, and token storage. Smaller footprint than `keycloak-js` and not tied to Keycloak internals.  
**Alternatives considered**: `keycloak-js` (Keycloak-specific, ties us to vendor SDK; harder to swap IdP later), `vue-keycloak-js` (thin wrapper, same concern).

---

### 3. State Management

**Decision**: Pinia  
**Rationale**: Official Vue 3 state management library. Fully TypeScript-native, DevTools integration, composable-friendly, no boilerplate. Two stores needed: `auth` (user identity + token) and `notifications` (in-app rework badges).  
**Alternatives considered**: Vuex 4 (verbose, legacy), plain `reactive()` + `provide/inject` (acceptable for small state but no DevTools support, harder to test).

---

### 4. API Type Generation

**Decision**: `openapi-typescript` (CLI) generating `src/api/types.ts` from `specs/001-document-approval-engine/contracts/openapi.yaml`  
**Rationale**: Single source of truth for API shapes. Drift between the backend contract and the frontend's type assumptions surfaces as a TypeScript compile error. Generated file is committed so PRs expose contract changes visibly.  
**Alternatives considered**: Hand-written types (drift risk, maintenance burden), `swagger-codegen` (heavier, generates full client boilerplate that constrains usage patterns).

---

### 5. HTTP Client

**Decision**: Native `fetch` wrapped in a thin `client.ts`  
**Rationale**: No additional dependency needed. The wrapper attaches the OIDC bearer token from the auth store to every request and handles 401 → silent token refresh → retry. Response types come from generated OpenAPI types.  
**Alternatives considered**: Axios (valid choice, slightly larger bundle, more interceptor flexibility — revisit if retry/cancellation complexity grows).

---

### 6. Routing

**Decision**: Vue Router 4 with a navigation guard that checks authentication before every protected route  
**Rationale**: Official Vue 3 router. The navigation guard redirects unauthenticated users to the OIDC login flow. Post-login redirect preserves the originally requested route.  
**Smart landing page**: Guard checks the worklist API on login; if pending tasks exist → redirect to `/worklist`; otherwise → `/submissions`. This fulfils FR-016 without a dedicated redirect endpoint.

---

### 7. Testing Stack

**Decision**: Vitest (unit + component) + Playwright (E2E)  
**Rationale**: Vitest shares Vite config and is fast. `@vue/test-utils` + `@testing-library/vue` for component testing. Playwright provides real-browser E2E coverage against the full stack (backend + Keycloak).  
**Alternatives considered**: Jest (requires separate Babel/TS transform config, slower), Cypress (heavier setup, less portable in CI).

---

### 8. Local Keycloak Setup

**Decision**: Keycloak 25.x added to `docker-compose.yml` with a committed `realm-export.json` in `ui/keycloak/`  
**Realm config includes**:
- Realm: `wrkflw`
- Client: `wrkflw-ui` (public OIDC client, PKCE enabled)
- Test users: `alice` (initiator group), `bob` (reviewer group), `carol` (both groups)
- Groups: `initiators`, `legal-reviewers`, `finance-reviewers`

**Rationale**: Zero-setup local dev; all developers run the same IdP with the same users. Realm config is exported JSON → committed → importable by Keycloak on first boot via `--import-realm`.  
**Alternatives considered**: Mock OIDC server (`node-oidc-provider`) — lighter but diverges from real Keycloak claim/session behaviour; `keycloak-js` quirks surface only with a real Keycloak.

---

### 9. Onyx Integration

**Decision**: Install Onyx via npm (`@sit-onyx/nuxt` is for Nuxt; use `@sit-onyx/components` for Vue 3 + Vite), register globally in `main.ts`, import CSS.  
**Key Onyx components used**:
- `OnyxNavBar` / `OnyxSideNav` — persistent navigation (FR-015)
- `OnyxForm`, `OnyxInput`, `OnyxTextarea`, `OnyxSelect` — dynamic submission form (FR-003)
- `OnyxDataGrid` / `OnyxTable` — worklist and submissions list
- `OnyxBadge` / `OnyxToast` — rework notification indicator (FR notification requirement)
- `OnyxButton`, `OnyxDialog` — decision confirmation dialogs

**Rationale**: Onyx provides the Schwarz IT design system out of the box; no custom component library required per spec assumption.  
**Theme**: Uses Onyx's default Schwarz IT theme; no overrides unless branding requires.

---

### 10. Backend Auth Header Migration Path

**Current state**: The backend currently uses `X-Actor-Id` / `X-Actor-Groups` headers (placeholder). The OpenAPI spec notes "OIDC/SSO later."  
**Frontend approach**: The `client.ts` wrapper sends the OIDC bearer token as `Authorization: Bearer <token>`. A backend adapter upgrade (out of scope for this feature) will extract actor identity from the JWT claims rather than the custom headers. During development, the Keycloak JWT claims (`sub`, `groups`) map to the backend's expected actor context.  
**Risk**: Until the backend adapter is updated, local E2E tests may need the custom headers injected alongside the bearer token. A feature flag or a thin dev-only request transformer handles the transition.
