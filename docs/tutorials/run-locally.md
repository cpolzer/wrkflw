# Run the document-approval flow locally

This tutorial gets you from a clean checkout to a running flow you can drive by hand.

!!! note "Authoritative steps"
    The canonical, always-current run instructions live in
    `specs/001-document-approval-engine/quickstart.md`. This page is the gentle, narrative
    version; if they disagree, the quickstart wins (and please fix this page).

## 1. Prerequisites

- [mise](https://mise.jdx.dev/) — installs and pins all other tools
- Docker

## 2. Install pinned tools

```bash
mise install         # installs temurin-21 + python 3.12, creates .venv
```

This reads `mise.toml` and installs exactly the JDK and Python versions the project requires.
No manual `JAVA_HOME` wrangling needed.

## 3. Start backing services

```bash
mise run services:up
```

- PostgreSQL → `localhost:5432` (db `wrkflw`)
- Temporal → `localhost:7233`, UI on `localhost:8233`

## 4. Build and migrate

```bash
mise run build       # compile + test all modules (includes the boundary-test gate)
mise run migrate     # apply schema migrations + seed document-approval definition
```

The build runs the architecture boundary test — if `domain`/`application` ever import a
framework type, it fails here by design.

## 5. Run the two services

Open two terminals:

```bash
# terminal 1
mise run run:worker  # Temporal workflows + activities

# terminal 2
mise run run:api     # REST on :8080 + outbox publisher
```

## 6. Drive a flow

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
