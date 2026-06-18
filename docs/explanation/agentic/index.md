# Agentic Coding

This project was built almost entirely by AI agents — with a human steering direction, reviewing outputs, and intervening when agents got stuck. This page explains the tooling landscape and how the pieces fit together, so you can replicate or extend the approach locally.

## The Core Idea

Rather than prompting an AI to write code directly, the workflow is **spec-first**:

1. Write a precise business specification before touching code
2. Let the agent derive an implementation plan from the spec
3. Break the plan into discrete, dependency-ordered tasks
4. Execute tasks one at a time — with the agent doing the implementation and tests

The spec acts as a contract between you and the agent. When the agent drifts, you correct the spec, not the code. When a task fails, the spec tells you what "correct" actually means.

## Tools Used

### Orchestrating the workflow — [Spec Kit](https://github.com/github/spec-kit)

Spec Kit is the backbone of the development lifecycle. It enforces a structured flow from constitution → specification → plan → tasks → implement, and keeps a project constitution that all agents must honour.

See the [Spec Kit workflow](spec-kit.md) page for the full breakdown.

### AI agents / harnesses

| Tool | Role |
|------|------|
| [Claude Code](https://claude.ai/code) (Sonnet) | Primary agent — specs, planning, architecture decisions, review |
| [opencode](https://opencode.ai) + Qwen / Kimi | Secondary harness — implementation tasks, cost reduction |
| [Voltagent specialists](https://github.com/VoltAgent/awesome-claude-code-subagents) | Language/domain specialists pulled in for targeted review or research |

Planning and specifications were done with Claude Sonnet. Once a `tasks.md` existed, implementation tasks were handed off to Qwen or Kimi via opencode for cost efficiency.

### Contract-first API design — [TypeSpec](https://typespec.io)

TypeSpec is the source of truth for REST contracts. The chain is:

```
contracts/*.tsp  →  tsp compile  →  openapi.yaml  →  openapi-typescript  →  ui/src/api/types.ts
```

The `.tsp` source lives in `contracts/` and emits directly to `specs/001-document-approval-engine/contracts/openapi.yaml`. The OpenAPI YAML and generated TypeScript types are both committed so the docs site and UI can consume them without running the TypeSpec compiler. Running `mise run contracts:build` regenerates the OpenAPI; `mise run ui:generate-types` regenerates the TypeScript types.

### Token / cost optimization

| Tool | Purpose |
|------|---------|
| [mem0](https://github.com/mem0ai/mem0) | Persistent cross-session memory — agents recall prior decisions without re-explaining context |
| [ogham-mcp](https://github.com/cpolzer/ogham-mcp) | Compressed context retrieval — reduces tokens spent on large codebases |
| [jcodemunch](https://github.com/jgravelle/jcodemunch-mcp) | Code chunking MCP — feeds only relevant file sections to the agent |

### Observability

| Tool | Purpose |
|------|---------|
| [codeburn](https://github.com/getagentseal/codeburn) | Local token / cost tracking per session |
| [Entire](https://entire.io) | Overall session observability (being explored) |

## Practical Hints for Local Use

- **Start with the constitution.** Before specifying anything, run `/speckit-constitution` and answer its questions. This gives every subsequent agent a shared set of non-negotiable rules.
- **Clarify before planning.** Use `/speckit-clarify` to surface ambiguities in the spec before the agent makes technology decisions. Fixing a spec is cheap; fixing a plan is not.
- **Hand off at `tasks.md`.** Once `tasks.md` exists, you can switch to a cheaper model/harness. The tasks are small enough that context is low and model quality matters less.
- **Don't skip local validation.** `./gradlew build` before every push. CI is a safety net, not a first reviewer (Principle VI of the constitution).
- **Use mem0.** Without cross-session memory, agents repeat mistakes. With it, they accumulate project knowledge across sessions.
