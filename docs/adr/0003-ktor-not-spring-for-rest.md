# 0003. Use Ktor (not Spring Boot) for the REST API

- **Status**: Accepted
- **Date**: 2026-06-09
- **Deciders**: wrkflw team

## Context and problem statement

We need an HTTP framework for the REST API. The choice must respect hexagonal layering (framework
confined to adapters/apps) and the team standard.

## Considered options

- **Ktor** — lightweight Kotlin-native server; no annotation/reflection magic.
- **Spring Boot** — feature-rich, but heavy and reflection-driven; conflicts with the team
  standard.

## Decision

Use **Ktor** for all HTTP/REST APIs built in Kotlin. Spring Boot is prohibited for the API layer.
This is encoded in the constitution (Technology & Architecture Constraints, v1.0.1).

## Consequences

- **Positive**: light footprint, explicit wiring, stays cleanly in the adapter/app layer.
- **Negative / trade-offs**: fewer batteries-included conveniences than Spring; DI handled
  separately (Koin or manual wiring).

## Links

- Constitution §Technology & Architecture Constraints (v1.0.1)
- Memory: `kotlin-rest-use-ktor`
