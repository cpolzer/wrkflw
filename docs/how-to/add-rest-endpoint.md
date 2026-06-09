# How to: add a REST endpoint

Endpoints are **driving adapters** over application use cases — they must not contain business
logic.

## Steps

1. **Start from a use case** — define (or reuse) an application command/query in
   `application/.../command` or `application/.../query`. It depends only on domain ports.
2. **Write the contract test first** (Principle II) — add it under
   `adapters/rest-api/src/test/...` against `contracts/openapi.yaml`; make it fail.
3. **Update the contract** — add the path/schema to
   `specs/001-document-approval-engine/contracts/openapi.yaml`.
4. **Add the route** in `adapters/rest-api/.../*Routes.kt`: parse the request DTO, read the
   `ActorContext`, call the use case, map the result/errors to HTTP, return the response DTO.
5. **Wire it** in the api-service composition root (Koin module or manual wiring).
6. **Map errors** — domain refusals → appropriate HTTP (403 unauthorized, 409 conflict/illegal
   state, 404 not found, 422 invalid input). Never swallow errors silently.

!!! note "Keep the framework out of the core"
    Ktor types live only in `rest-api`/`apps`. The use case knows nothing about HTTP. The boundary
    test enforces this.
