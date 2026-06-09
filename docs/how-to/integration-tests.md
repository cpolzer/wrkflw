# How to: write an integration test

Per constitution Principle II, adapters and cross-boundary contracts are covered by integration
tests against **real backing services** (not mocks of those services).

## Tooling

- **JUnit 5** + `kotlin.test`
- **Testcontainers** for PostgreSQL
- **Temporal** `TestWorkflowEnvironment` / Testcontainers for orchestration

## Patterns

- **Persistence adapter** → spin up a Postgres container, run Flyway migrations, exercise the jOOQ
  repository against it. Example target: `TaskConcurrencyTest` (two simultaneous claims → exactly
  one wins, SC-004).
- **End-to-end (api-service)** → Postgres + Temporal test env; drive the REST endpoints and assert
  DB state + audit history (e.g., `SubmitDocumentE2ETest`, `ClaimDecideE2ETest`).
- **Outbox/events** → assert one outbox row per committed change, a matching CloudEvent published,
  and no event on rollback (SC-006).

## Running tests

```bash
mise run test          # all tests
mise run build         # compile + test + boundary check (what CI runs)
```

Testcontainers manages its own Docker images — just ensure Docker is running (`mise run services:up`
starts the named services, but Testcontainers spins its own ephemeral containers independently).

## Rules

- **Tests first, must fail** before implementation (red→green→refactor).
- Use a **fixed `Clock`** in tests for deterministic timestamps.
- Prefer asserting **observable outcomes** (DB rows, audit entries, emitted events) over internal
  calls.

See the per-story test tasks in `specs/001-document-approval-engine/tasks.md`.
