# Research: Pre-Commit Hooks & Commit Convention Enforcement

**Date**: 2026-06-10

## R-001 — Conventional Commits validation tool choice

**Decision**: POSIX shell regex (`grep -Eq`) inside `hooks/commit-msg`.

**Rationale**: Zero new dependencies. The pattern covers the full Conventional Commits v1.0 spec
(type, optional scope, optional breaking `!`, description length cap at 100 chars). All allowed
types are enumerated in the pattern so typos (`feat` vs `feature`) are caught immediately.

**Alternatives considered**:
- `commitlint` (npm/Node) — industry standard but adds a Node/npm dependency the project
  constitution prohibits.
- `gitlint` (Python via `uv run`) — viable since `uv` is already in the toolchain; rejected
  because a shell regex is simpler and has no startup cost.
- `lefthook` (Go binary via mise) — purpose-built hook runner with built-in CC support;
  rejected to keep the toolchain to JVM + shell, avoiding an extra mise-managed binary.

## R-002 — Pre-commit lint scope

**Decision**: Detect staged `.kt` files; if any exist, run `./gradlew ktlintCheck detekt`.

**Rationale**: Running the full Gradle check (not file-filtered) is the most accurate approach.
With the Gradle daemon running and incremental caching warm, this completes in 5–20 s for
typical staged changes — within SC-004's 30 s target. File-level filtering inside the ktlint
Gradle plugin is possible (`ktlint { filter { include("...") } }`) but saves only a few seconds
at the cost of extra configuration that could diverge from the CI check.

**Alternatives considered**:
- `ktlint` CLI binary on staged files only (sub-second) — rejected: requires a separate binary
  in the toolchain; also skips detekt analysis which requires the Gradle plugin.
- `git stash --keep-index` + full Gradle check + `git stash pop` — rejected: stash-based
  isolation causes problems with partially staged files and is fragile under interrupts.

## R-003 — Co-existence with Entire CLI hooks

**Decision**: Installer wraps existing hooks rather than replacing them.

**Rationale**: The repo already has `commit-msg` and other hooks managed by the Entire CLI. The
installer detects an existing hook (by checking for the `## wrkflw-managed` marker to make the
operation idempotent) and creates a wrapper that calls our validation first (fail-fast), then
delegates to the Entire CLI's original content. This preserves all Entire CLI functionality
while adding our gate.

## R-004 — Hook distribution mechanism

**Decision**: Shell scripts in a version-controlled `hooks/` directory; `mise run hooks:install`
copies them to `.git/hooks/`.

**Rationale**: `.git/hooks/` is not cloned with the repository. The only reliable options are:
(a) copy scripts in at setup time via a documented command, or (b) use a hook manager that auto-
installs (e.g., `husky`, `lefthook`). Option (a) with `mise run hooks:install` is consistent
with how the rest of the project's setup works (`mise install`, `mise run ci`). The scripts live
in `hooks/` at the repo root — committed, reviewed, and diff-able like any other source file.
