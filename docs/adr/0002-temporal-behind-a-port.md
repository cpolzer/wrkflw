# 0002. Adopt Temporal for orchestration, behind a port

- **Status**: Accepted
- **Date**: 2026-06-09
- **Deciders**: wrkflw team

## Context and problem statement

The engine needs durable, long-running orchestration (timers, retries, "wait for a human")
without reinventing a workflow engine, while keeping business logic free of the orchestration
technology (constitution Principle IV).

## Considered options

- **Temporal**, accessed via a `WorkflowEngine` port — durable execution; human tasks modeled as
  signal waits.
- **Camunda** — strong human-task fit out of the box, but BPMN + licensing weight.
- **Kafka/Zeebe** — a log/transport, not an orchestrator; we'd still build the engine.
- **Hand-built state machine + DB** — full control, but reinvents durability.

## Decision

Adopt Temporal, wrapped behind the `WorkflowEngine` port. Integrate via **Approach A**: Temporal
owns orchestration position; PostgreSQL owns human-task state, business data, and audit. Internal
progression uses signals/activities; brokers/events are for external integration only.

## Consequences

- **Positive**: durability/retries/timers for free; domain stays Temporal-free and the engine is
  swappable; simple DB-based work-list queries and admin operations.
- **Negative / trade-offs**: operational dependency on a Temporal cluster; a second source of
  truth (orchestration vs. DB) requires the commit-then-signal discipline.

## Links

- [Temporal integration (Approach A)](../explanation/temporal-integration.md)
- `research.md` D1, D2; constitution Principle IV
