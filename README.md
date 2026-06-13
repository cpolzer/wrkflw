# wrkflw

A data-driven, human-centric workflow engine for document approvals, built on Kotlin, Ktor, Temporal, PostgreSQL, and CloudEvents.

## Architecture

The codebase is a Gradle multi-module monorepo structured to [hexagonal architecture](docs/explanation/architecture.md):

```
domain/           Pure business logic — entities, ports, no framework dependencies
application/      Use-case services (orchestrate domain + ports)
adapters/
  persistence-postgres/   jOOQ + Flyway (PostgreSQL)
  rest-api/               Ktor route handlers + DTOs
  temporal/               Temporal workflows + activities
  eventing-cloudevents/   CloudEvents outbox publisher
apps/
  api-service/    REST server + outbox poller (composition root)
  worker-service/ Temporal worker (composition root)
```

`domain` and `application` have no dependency on Ktor, Temporal, jOOQ, or any framework. All I/O flows through ports.

## Prerequisites

- JDK 21
- Docker (PostgreSQL + Temporal dev server)
- [mise](https://mise.jdx.dev/) (task runner)

## Quick start

### Option A — full Docker stack (recommended)

Builds and runs every backend service in containers. Only the UI dev server runs on the host.

```bash
mise run local:up    # builds images + starts all services
mise run migrate     # apply DB schema (first time only)
mise run ui:install  # install frontend deps (first time only)
mise run ui:dev      # Vite dev server → http://localhost:5173
```

Rebuild after code changes: `mise run local:up` (only changed layers rebuild).

### Option B — run services on the host

```bash
docker compose up -d postgres temporal keycloak

./gradlew build
mise run migrate

mise run run:worker  # terminal 1
mise run run:api     # terminal 2

mise run ui:install  # first time only
mise run ui:dev      # terminal 3
```

See [`docs/how-to/run-full-stack-docker.md`](docs/how-to/run-full-stack-docker.md) for details on the Docker stack, and [`specs/001-document-approval-engine/quickstart.md`](specs/001-document-approval-engine/quickstart.md) for a full end-to-end curl walkthrough.

## Development

```bash
# Lint + static analysis
./gradlew ktlintCheck detekt

# Auto-fix lint
./gradlew ktlintFormat

# Full CI (lint → build → docs)
mise run ci

# Live docs preview
mise run docs:serve
```

## Modules

| Module | Purpose |
|--------|---------|
| `domain` | Entities (`FlowInstance`, `Task`, `Decision`), value objects, domain events, ports |
| `application` | Command services (submit, claim, release, decide) and query services (worklist, flow status) |
| `adapters:persistence-postgres` | jOOQ-based repository implementations + Flyway migrations |
| `adapters:rest-api` | Ktor route definitions + request/response DTOs |
| `adapters:temporal` | `DocumentApprovalWorkflow`, `CreateHumanTaskActivity`, `AdvanceFlowActivity` |
| `adapters:eventing-cloudevents` | `CloudEventsOutboxPublisher` — maps outbox rows to CloudEvents 1.0 envelopes |
| `apps:api-service` | Main entry point: Ktor server + Koin wiring + outbox poller |
| `apps:worker-service` | Temporal worker entry point |

## API overview

All endpoints are under `/api/v1`. Actor identity is supplied via trusted headers (`X-Actor-Id`, `X-Actor-Groups`).

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/flows` | Submit a document for approval |
| `POST` | `/tasks/{id}/claim` | Claim a task |
| `POST` | `/tasks/{id}/release` | Release a claimed task |
| `POST` | `/tasks/{id}/decision` | Submit approve/reject/submit decision |
| `GET` | `/worklists/group` | Tasks for the caller's candidate groups |
| `GET` | `/worklists/mine` | Tasks claimed by the caller |
| `GET` | `/flows/{id}` | Flow status + pending tasks + audit history |

See [`docs/reference/rest-api.md`](docs/reference/rest-api.md) for the full contract.

## Events

Each state change writes a `CloudEvent` via the transactional outbox. Event types:

- `dev.wrkflw.flow.started`
- `dev.wrkflw.task.created`
- `dev.wrkflw.task.claimed`
- `dev.wrkflw.task.released`
- `dev.wrkflw.decision.recorded`
- `dev.wrkflw.flow.completed`

See [`docs/reference/events.md`](docs/reference/events.md) for schemas.

## Documentation

Full developer docs are in [`docs/`](docs/index.md) and served with MkDocs:

```bash
mise run docs:serve   # live preview at http://localhost:8000
mise run docs:build   # static build
```
