# Quickstart: Document Approval Workflow Engine

How to run and exercise the first deliverable locally. (Build modules do not exist yet —
this is the target developer experience the implementation should deliver.)

## Prerequisites

- JDK 21
- Docker (for PostgreSQL, Temporal dev server, and Testcontainers)

## Local dependencies

```bash
# PostgreSQL + Temporal dev server (compose file added during implementation)
docker compose up -d postgres temporal
```

- PostgreSQL: `localhost:5432` (db `wrkflw`)
- Temporal: `localhost:7233` (UI on `localhost:8233`)

## Build & migrate

```bash
./gradlew build                 # compiles all modules, runs unit + integration tests
./gradlew :adapters:persistence-postgres:flywayMigrate   # apply schema (seeds document-approval definition)
```

The build fails if `domain` or `application` acquire a forbidden framework/SQL/Temporal
dependency (Konsist/ArchUnit boundary test — Principle I).

## Run the two services

```bash
./gradlew :apps:worker-service:run    # hosts Temporal workflows + activities
./gradlew :apps:api-service:run       # serves REST on :8080, runs the outbox publisher
```

## Exercise the document-approval flow

> Actor identity for the first deliverable is supplied via a trusted header (placeholder for
> real OIDC). Examples use `X-Actor-Id` / `X-Actor-Groups`.

```bash
# 1. Submit a document (caller must be in the initiator group)
curl -s -X POST localhost:8080/api/v1/flows \
  -H 'Content-Type: application/json' \
  -H 'X-Actor-Id: alice' -H 'X-Actor-Groups: authors' \
  -d '{"definitionKey":"document-approval","documentRef":"doc-123"}'
# → 201 { flowId, currentState: "Submitted", pendingTasks: [ { taskId, candidateGroupId: "reviewers" } ] }

# 2. A reviewer sees the group work list
curl -s localhost:8080/api/v1/worklists/group \
  -H 'X-Actor-Id: bob' -H 'X-Actor-Groups: reviewers'

# 3. Claim the task
curl -s -X POST localhost:8080/api/v1/tasks/$TASK_ID/claim \
  -H 'X-Actor-Id: bob' -H 'X-Actor-Groups: reviewers'

# 4. Approve it (advances the flow; signals Temporal)
curl -s -X POST localhost:8080/api/v1/tasks/$TASK_ID/decision \
  -H 'Content-Type: application/json' \
  -H 'X-Actor-Id: bob' -H 'X-Actor-Groups: reviewers' \
  -d '{"outcome":"APPROVE","comment":"looks good"}'

# 5. Inspect status + full history
curl -s localhost:8080/api/v1/flows/$FLOW_ID
```

## Acceptance smoke checks (map to spec)

- **US1**: submit → flow `RUNNING`, one `PENDING` task for `reviewers`, `FLOW_STARTED` in history.
- **US2**: claim then approve → task `COMPLETED`, decision recorded, flow advances; a second
  reviewer cannot decide the same task (409).
- **US3**: approve through to `FinalReview` → `Approved` terminal; reject → returns to submitter
  (`ReworkRequested`).
- **Concurrency (SC-004)**: two simultaneous claims → exactly one 200, one 409.
- **Events (SC-006)**: each step emits the matching CloudEvent via the outbox.
