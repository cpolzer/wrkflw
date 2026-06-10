# Architecture Decision Records

ADRs capture **significant, hard-to-reverse decisions** and the reasoning behind them, in
[MADR](https://adr.github.io/madr/) format. They complement the per-feature Spec Kit
`research.md` (which records feature-scoped decisions) by holding cross-cutting, long-lived ones.

Write a new ADR by copying [`template.md`](template.md) to `NNNN-short-title.md` (next number),
and link it here.

| # | Title | Status |
|---|-------|--------|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions (use ADRs) | Accepted |
| [0002](0002-temporal-behind-a-port.md) | Adopt Temporal for orchestration, behind a port | Accepted |
| [0003](0003-ktor-not-spring-for-rest.md) | Use Ktor (not Spring Boot) for the REST API | Accepted |

!!! note "Relationship to Spec Kit"
    Decisions D1–D9 in `specs/001-document-approval-engine/research.md` are the feature-level
    decision log. Promote any that are project-wide and long-lived into an ADR here.
