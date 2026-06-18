# Reference: REST API

The authoritative contract is defined in **TypeSpec** and compiled to OpenAPI:

| Artifact | Path | Role |
|----------|------|------|
| TypeSpec source | `contracts/lib/models.tsp`, `contracts/lib/routes.tsp` | Edit this |
| Generated OpenAPI | `specs/001-document-approval-engine/contracts/openapi.yaml` | Generated — do not hand-edit |
| TypeScript types | `ui/src/api/types.ts` | Generated from OpenAPI |

To regenerate after changing the TypeSpec:

```bash
mise run contracts:build     # .tsp → openapi.yaml
mise run ui:generate-types   # openapi.yaml → ui/src/api/types.ts
```

## Endpoints (first deliverable)

| Method & path | Purpose | Story |
|---------------|---------|-------|
| `POST /api/v1/flows` | Submit a document / start a flow (initiator group only) | US1 |
| `GET /api/v1/flows/{flowId}` | Flow status + pending tasks + history | US4 |
| `GET /api/v1/worklists/group` | Unclaimed tasks for the caller's group(s) | US4 |
| `GET /api/v1/worklists/mine` | Tasks the caller owns | US4 |
| `POST /api/v1/tasks/{taskId}/claim` | Claim a pending task | US2 |
| `POST /api/v1/tasks/{taskId}/release` | Release a claimed task | US2 |
| `POST /api/v1/tasks/{taskId}/decision` | Approve/reject an owned task | US2 |

## Conventions

- **Identity**: `X-Actor-Id` / `X-Actor-Groups` headers in the first deliverable (placeholder for
  OIDC/SSO later), surfaced to the core via the `ActorContext` port.
- **Error mapping**: 403 (not authorized / wrong group / not owner), 409 (not claimable/decidable
  — conflict or illegal state), 404 (not found), 422 (invalid input).

!!! tip "Rendering the spec"
    To browse the OpenAPI interactively, point Swagger UI / Redoc at `contracts/openapi.yaml`, or
    add the `neoteroi-mkdocs` OpenAPI plugin later to embed it in this site.
