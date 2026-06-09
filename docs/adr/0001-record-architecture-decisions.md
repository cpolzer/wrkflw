# 0001. Record architecture decisions (use ADRs)

- **Status**: Accepted
- **Date**: 2026-06-09
- **Deciders**: wrkflw team

## Context and problem statement

The project makes significant architectural choices that outlive any single feature. We need a
lightweight, durable, in-repo record of *why* — discoverable by future contributors.

## Considered options

- ADRs (MADR) in `docs/adr/` — versioned with code, one file per decision.
- Keep rationale only in Spec Kit `research.md` — feature-scoped, not cross-cutting.
- Wiki / external doc — drifts from the code, not PR-reviewable.

## Decision

Adopt MADR-format ADRs under `docs/adr/`. Feature-scoped decisions stay in `research.md`;
project-wide, long-lived ones are promoted to ADRs.

## Consequences

- **Positive**: durable, reviewable, close to the code; clear decision history.
- **Negative / trade-offs**: small discipline cost to write one per significant decision.

## Links

- Constitution §Governance (amendment & versioning)
- `specs/001-document-approval-engine/research.md`
