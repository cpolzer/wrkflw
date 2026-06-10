# Hexagonal Layering

The inward-only dependency rule (constitution Principle I) is the backbone of the codebase.

## The rule

```
domain  ←  application  ←  adapters  ←  apps
```

- **`domain`** — pure Kotlin: aggregates (`FlowInstance`, `Task`), value objects, the flow state
  machine, domain events, and **ports** (interfaces). No framework, no SQL, no Temporal, no DI.
- **`application`** — use cases (commands/queries) that orchestrate domain objects through ports
  and own the transaction boundary. Depends only on `domain`.
- **`adapters`** — concrete implementations of ports, each owning one infrastructure concern
  (`persistence-postgres`, `temporal`, `rest-api`, `eventing-cloudevents`).
- **`apps`** — thin composition roots that wire adapters to ports and own configuration.

## Ports (defined in `domain`)

| Port | Implemented by |
|------|----------------|
| `FlowDefinitionRepository`, `FlowInstanceRepository`, `TaskRepository` | `persistence-postgres` (jOOQ) |
| `AuditLog` | `persistence-postgres` |
| `WorkflowEngine` | `temporal` |
| `DomainEventPublisher` | `eventing-cloudevents` (via outbox) |
| `ActorContext` | `rest-api` (header-based for now; OIDC later) |
| `Clock` | app (system clock; fixed clock in tests) |

## Why an application service looks framework-free

```kotlin
// application/ — depends only on domain ports
class SubmitDocument(
    private val definitions: FlowDefinitionRepository,
    private val flows: FlowInstanceRepository,
    private val engine: WorkflowEngine,
    private val audit: AuditLog,
    private val actor: ActorContext,
)
```

It never names a concrete adapter. Wiring happens in the app composition root (see
[how DI works](../how-to/add-rest-endpoint.md) and ADR-0004 on DI). This is what keeps the
business logic fast to unit-test and the infrastructure replaceable.

## Enforcement

This is not a guideline — it is a **test**. A Konsist/ArchUnit boundary test (task T014) asserts
that `domain` and `application` have no forbidden dependencies, and it runs on every build. If
someone imports a Ktor or jOOQ type into the domain, the build goes red.
