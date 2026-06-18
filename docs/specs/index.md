# Feature Specs

This section surfaces the raw Spec Kit artifacts for every feature built in this project — specification, implementation plan, tasks, data model, contracts, and research notes.

All specs follow the [Spec Kit lifecycle](../explanation/agentic/spec-kit.md): `constitution → specify → clarify → plan → tasks → implement`.

## Features

| # | Feature | Status | Priority |
|---|---------|--------|---------|
| [001](001-document-approval-engine/spec.md) | Document Approval Engine | Complete | Core |
| [002](002-pre-commit-hooks/spec.md) | Pre-commit Hooks | Complete | DX |
| [003](003-vue-onyx-frontend/spec.md) | Vue / Onyx Frontend | Complete | Core |
| [004](004-ui-empty-state-submit/spec.md) | UI Empty State & Submit | Complete | UX |
| [005](005-os-agnostic-test-infra/spec.md) | OS-Agnostic Test Infrastructure | Complete | Infra |

## What Each Artifact Is

| File | Stage | Contains |
|------|-------|---------|
| `spec.md` | specify | Business-facing requirements, user stories, acceptance scenarios |
| `plan.md` | plan | Technology decisions, module design, implementation approach |
| `tasks.md` | tasks | Ordered implementation checklist with done conditions |
| `data-model.md` | plan | Entity relationships, DB schema |
| `contracts/` | plan | TypeSpec source (`.tsp`) + generated OpenAPI + integration event definitions |
| `research.md` | plan | Notes and spikes captured during planning |
| `checklists/requirements.md` | tasks | FR/SC traceability matrix |
| `quickstart.md` | implement | How to run this feature locally |
