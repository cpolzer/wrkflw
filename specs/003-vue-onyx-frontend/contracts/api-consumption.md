# Contract: Frontend API Consumption

**Date**: 2026-06-10  
**Source contract**: `specs/001-document-approval-engine/contracts/openapi.yaml`  
**Consumer**: `ui/` Vue SPA  
**Provider**: `apps/api-service` Ktor REST API

---

## Authentication

All requests carry `Authorization: Bearer <oidc-access-token>` obtained from Keycloak via OIDC PKCE flow.

**Current backend state**: The backend adapter reads `X-Actor-Id` / `X-Actor-Groups` headers.
A backend migration (tracked separately) will extract these from the JWT claims.
During transition, `client.ts` injects both the `Authorization` header and the custom headers
(derived from the decoded token) for local development compatibility.

---

## Endpoints Consumed

| Endpoint | Method | Frontend Usage | View/Composable |
|---|---|---|---|
| `/flows` | POST | Submit new flow | `SubmitFlowView` → `useFlows` |
| `/flows/{flowId}` | GET | Flow detail + history | `FlowDetailView` → `useFlows` |
| `/worklists/group` | GET | Reviewer worklist | `WorklistView` → `useWorklist` |
| `/worklists/mine` | GET | My claimed tasks | `WorklistView` → `useWorklist` |
| `/tasks/{taskId}/claim` | POST | Claim a task | `WorklistView` → `useTasks` |
| `/tasks/{taskId}/release` | POST | Release a task | `TaskDetailView` → `useTasks` |
| `/tasks/{taskId}/decision` | POST | Approve / reject | `TaskDetailView` → `useTasks` |

> **Missing endpoint** (needed): `GET /definitions` — list of workflow definitions the caller
> may initiate. Required for `MySubmissionsView` to show available definitions and for
> `SubmitFlowView` to render the dynamic form. Must be added to the backend and the OpenAPI
> spec before the submission flow can be fully implemented. Interim: hardcode document-approval
> definition.

---

## Error Handling Contract

| HTTP Status | Frontend Behaviour |
|---|---|
| 401 | Silent token refresh via OIDC; retry once. If refresh fails, redirect to login. |
| 403 | Show inline "Not authorised" message. Do not redirect. |
| 404 | Show "Not found" toast; navigate back. |
| 409 | Show "Action no longer available" message (task already claimed, etc.). Refresh worklist. |
| 422 | Map field errors to inline form validation messages if the backend returns field-level detail; otherwise show generic validation toast. |
| 5xx | Show "Something went wrong, please try again" toast. Log to console in dev. |
| Network error | Show "Cannot reach server" banner. No silent failure. |

---

## Request Conventions

- **Content-Type**: `application/json` for all POST request bodies.
- **Base URL**: Configured via `VITE_API_BASE_URL` environment variable (default: `http://localhost:8080/api/v1`).
- **Timeout**: 10 s per request; configurable via `VITE_API_TIMEOUT_MS`.

---

## Type Generation

```bash
# Run from ui/ directory after installing deps:
npm run generate:types
# Internally: openapi-typescript ../specs/001-document-approval-engine/contracts/openapi.yaml -o src/api/types.ts
```

The generated `src/api/types.ts` is committed. Any change to `openapi.yaml` requires
re-running the generator and committing the diff, making contract drift visible in PRs.
