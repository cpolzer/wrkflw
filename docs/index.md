# wrkflw Developer Documentation

**wrkflw** is a scalable, human-centric workflow engine. The first delivered use case is
**document approval**: flows contain tasks, tasks belong to responsible groups, people claim and
decide, and every transition is auditable.

This site is **developer-facing** — how the system is built and how to work on it. Product-level
specifications live alongside the code under `specs/` (Spec Kit), and the project rules live in
the constitution (`.specify/memory/constitution.md`).

## Find your way (Diátaxis)

| If you want to… | Go to |
|------------------|-------|
| **Get it running** and see a flow end to end | [Tutorials](tutorials/run-locally.md) |
| **Do a specific task** (add a flow, add an endpoint, write a test) | [How-to guides](how-to/add-flow-definition.md) |
| **Look something up** (REST API, events, schema, KDoc) | [Reference](reference/rest-api.md) |
| **Understand the why** (architecture, hexagonal, Temporal) | [Explanation](explanation/architecture.md) |
| **See decisions and their rationale** | [ADRs](adr/index.md) |

## Quick start

```bash
mise install           # pin tools (temurin-21, python 3.12)
mise run services:up   # start Postgres + Temporal
mise run build         # compile + test all modules
mise run migrate       # apply schema + seed document-approval
mise run run:worker &  # Temporal worker
mise run run:api       # REST API on :8080
```

CI runs: `mise run ci` (lint + build + docs).  Local docs preview: `mise run docs:serve`.

## The five non-negotiables (constitution)

1. **Hexagonal architecture** — `domain`/`application` are framework-free; all I/O via ports.
2. **Test-first** — tests precede implementation; real backing services in integration tests.
3. **Auditability** — every state change is an immutable, ordered record.
4. **Orchestration behind a port** — Temporal only via a port; brokers never drive steps.
5. **Explicit contracts & consistency** — transactional outbox; one effective decision under concurrency.

See [Architecture overview](explanation/architecture.md) for how these shape the code.

## Source-of-truth map

| Topic | Lives in |
|-------|----------|
| Product requirements (what/why) | `specs/001-document-approval-engine/spec.md` |
| Implementation plan & tech context | `specs/001-document-approval-engine/plan.md` |
| Data model | `specs/001-document-approval-engine/data-model.md` |
| REST + event contracts | `specs/001-document-approval-engine/contracts/` |
| Project rules | `.specify/memory/constitution.md` |
| Architecture narrative & decisions | this site (`docs/`) |
