# Run the document-approval flow locally

This tutorial gets you from a clean checkout to a running flow you can drive by hand.

!!! note "Authoritative steps"
    The canonical, always-current run instructions live in
    `specs/001-document-approval-engine/quickstart.md`. This page is the gentle, narrative
    version; if they disagree, the quickstart wins (and please fix this page).

## 1. Prerequisites

- JDK 21
- Docker

## 2. Start backing services

```bash
docker compose up -d postgres temporal
```

- PostgreSQL → `localhost:5432` (db `wrkflw`)
- Temporal → `localhost:7233`, UI on `localhost:8233`

## 3. Build and migrate

```bash
./gradlew build
./gradlew :adapters:persistence-postgres:flywayMigrate   # applies schema + seeds document-approval
```

The build runs the architecture boundary test — if `domain`/`application` ever import a framework
type, it fails here by design.

## 4. Run the two services

```bash
./gradlew :apps:worker-service:run    # Temporal workflows + activities
./gradlew :apps:api-service:run       # REST on :8080 + outbox publisher
```

## 5. Drive a flow

Identity is supplied via headers in the first deliverable (`X-Actor-Id` / `X-Actor-Groups`).

```bash
# Submit (caller must be in the initiator group)
curl -s -X POST localhost:8080/api/v1/flows \
  -H 'Content-Type: application/json' \
  -H 'X-Actor-Id: alice' -H 'X-Actor-Groups: authors' \
  -d '{"definitionKey":"document-approval","documentRef":"doc-123"}'

# Reviewer claims and approves
curl -s -X POST localhost:8080/api/v1/tasks/$TASK_ID/claim \
  -H 'X-Actor-Id: bob' -H 'X-Actor-Groups: reviewers'
curl -s -X POST localhost:8080/api/v1/tasks/$TASK_ID/decision \
  -H 'Content-Type: application/json' \
  -H 'X-Actor-Id: bob' -H 'X-Actor-Groups: reviewers' \
  -d '{"outcome":"APPROVE","comment":"looks good"}'

# Inspect status + history
curl -s localhost:8080/api/v1/flows/$FLOW_ID
```

## What you just exercised

- **US1** submit → flow `RUNNING`, first task for `reviewers`, `FLOW_STARTED` in history.
- **US2** claim → approve → task `COMPLETED`, flow advances, decision recorded.
- Audit history and (US5) CloudEvents emitted via the outbox.

Next: [Add a new flow definition](../how-to/add-flow-definition.md).
