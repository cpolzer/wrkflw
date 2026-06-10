# Implementation Plan: Pre-Commit Hooks & Commit Convention Enforcement

**Branch**: `001-document-approval-engine` | **Date**: 2026-06-10 | **Spec**: [spec.md](spec.md)

## Summary

Add version-controlled Git hook scripts (`hooks/pre-commit` and `hooks/commit-msg`) that block
commits on ktlint/detekt violations or non-Conventional-Commit messages. An idempotent
`mise run hooks:install` task copies scripts into `.git/hooks/` on each dev machine. No new
binary dependencies: linting uses the existing Gradle/ktlint/detekt setup; commit-message
validation uses a POSIX shell regex. Existing Entire-CLI hooks are preserved via wrapper scripts.

## Technical Context

**Language/Version**: POSIX sh (hooks), Kotlin 1.9 / JVM 21 (lint tooling via Gradle)

**Primary Dependencies**: ktlint 1.4.1 + detekt (already wired in `build-logic/kotlin-jvm.gradle.kts`); no new tool dependencies

**Storage**: N/A

**Testing**: Manual acceptance smoke tests (see tasks.md); shell scripts are too small for unit tests; Gradle build validates linter config at compile time

**Target Platform**: Linux / macOS developer workstations; same shell environment as CI

**Project Type**: Developer tooling / Git hooks (no new modules; touches build-logic and repo root only)

**Performance Goals**: Pre-commit lint completes within 30 s on a warm Gradle daemon (SC-004)

**Constraints**: No Node/npm; no new `mise`-managed binaries; must co-exist with existing Entire-CLI hooks in `.git/hooks/`

**Scale/Scope**: Two hook scripts + one installer shell script + one Gradle task entry in `mise.toml`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I — Hexagonal Architecture | ✅ PASS | Pure developer tooling; no changes to domain/application/adapter layers |
| II — Test-First Discipline | ✅ PASS | Acceptance scenarios are smoke tests (manual + CI integration); shell scripts have no meaningful unit-test surface |
| III — Auditability & Traceability | ✅ PASS | Not applicable — no business-state changes |
| IV — Orchestration Behind a Port | ✅ PASS | Not applicable |
| V — Explicit Contracts & Consistency | ✅ PASS | Conventional Commit spec is the contract; enforcement is the consistency guarantee |
| VI — Local Validation Before Push | ✅ DIRECTLY IMPLEMENTS | This feature is the mechanical enforcement of Principle VI |

No violations. No complexity tracking needed.

## Project Structure

### Documentation (this feature)

```text
specs/002-pre-commit-hooks/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md        ← N/A (no entities)
├── quickstart.md        ← Phase 1 output
└── tasks.md             ← /speckit-tasks output
```

### Source Code (repository root)

```text
hooks/                             # NEW — version-controlled hook scripts
├── pre-commit                     # runs ktlintCheck + detekt on staged .kt files
└── commit-msg                     # validates Conventional Commits format

scripts/
└── install-hooks.sh               # NEW — idempotent hook installer

mise.toml                          # MODIFY — add hooks:install task
```

No new Gradle modules. No changes to `build-logic/` (linting already configured).

## Phase 0: Research

### R-001 — Conventional Commits format (resolved)

**Decision**: Enforce the [Conventional Commits v1.0](https://www.conventionalcommits.org/en/v1.0.0/) spec via POSIX shell regex in `hooks/commit-msg`.

**Allowed types**: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`

**Format regex** (POSIX ERE):
```
^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\([a-z0-9/_-]+\))?(!)?: .{1,100}
```
Special exemptions (skip validation if message starts with): `Merge`, `Revert`, `WIP`

**Alternatives considered**:
- `commitlint` (npm) — rejected: adds Node dependency
- `gitlint` (Python via uv) — viable but adds setup steps; regex is simpler and zero-dep
- `lefthook` (Go binary) — viable; adds a mise-managed binary; rejected in favour of plain shell to keep toolchain minimal

### R-002 — Pre-commit lint scope (staged files only)

**Decision**: Detect whether any `.kt` files are staged. If yes, run `./gradlew ktlintCheck detekt` (full check). If no `.kt` files staged, skip lint entirely.

**Rationale**: Running `./gradlew ktlintCheck detekt` on a warm daemon with incremental caching takes 5–20 s for typical changes — within the 30 s SC-004 threshold. File-level filtering inside ktlint's Gradle plugin requires additional configuration and only saves a few seconds in practice.

**Alternative considered**: Run `ktlint` binary directly on staged files (sub-second). Rejected because (a) we'd need a separate `ktlint` binary in the toolchain, and (b) detekt can't be run file-by-file without the Gradle plugin.

### R-003 — Co-existence with Entire-CLI hooks

**Decision**: The installer creates thin wrapper scripts for `commit-msg` and any other hook that Entire CLI already owns. The wrapper calls our validation first (fail-fast), then delegates to the Entire CLI hook.

The existing `commit-msg` content is preserved inside the wrapper, so both validators run on every commit.

`pre-commit` has no existing hook — the installer writes our script directly.

## Phase 1: Design & Contracts

### Hook scripts

#### `hooks/pre-commit`

```sh
#!/bin/sh
set -e

# Only run lint if Kotlin files are staged.
STAGED_KT=$(git diff --cached --name-only --diff-filter=ACMR | grep '\.kt$' || true)
if [ -z "$STAGED_KT" ]; then
  exit 0
fi

echo "[pre-commit] Kotlin files staged — running ktlint + detekt..."
./gradlew ktlintCheck detekt --daemon --quiet
```

#### `hooks/commit-msg`

```sh
#!/bin/sh
set -e
MSG=$(cat "$1")

# Exempt merge/revert/WIP commits
case "$MSG" in
  Merge*|Revert*|WIP*) exit 0 ;;
esac

TYPES="feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert"
PATTERN="^($TYPES)(\([a-z0-9/_-]+\))?(!)?: .{1,100}"

if ! echo "$MSG" | grep -Eq "$PATTERN"; then
  echo ""
  echo "[commit-msg] ✗ Commit message does not follow Conventional Commits format."
  echo ""
  echo "  Required: type(scope): description"
  echo "  Example:  feat(api): add document submission endpoint"
  echo "            fix(temporal): handle null workflow id on signal"
  echo "            chore: bump ktlint to 1.5.0"
  echo ""
  echo "  Allowed types: feat fix docs style refactor perf test build ci chore revert"
  echo "  Breaking change: append ! after type/scope, e.g. feat!: remove legacy endpoint"
  echo ""
  exit 1
fi
```

#### `scripts/install-hooks.sh`

```sh
#!/bin/sh
set -e
REPO_ROOT=$(git rev-parse --show-toplevel)
HOOKS_SRC="$REPO_ROOT/hooks"
HOOKS_DST="$REPO_ROOT/.git/hooks"

install_hook() {
  name="$1"
  src="$HOOKS_SRC/$name"

  if [ ! -f "$src" ]; then
    echo "[install-hooks] WARN: $src not found, skipping"
    return
  fi

  dst="$HOOKS_DST/$name"

  # If an existing hook already exists (e.g. from Entire CLI), wrap it.
  if [ -f "$dst" ] && ! grep -q "## wrkflw-managed" "$dst" 2>/dev/null; then
    echo "[install-hooks] Wrapping existing $name hook..."
    EXISTING=$(cat "$dst")
    cat > "$dst" << WRAPPER
#!/bin/sh
## wrkflw-managed — do not edit by hand; re-run scripts/install-hooks.sh to regenerate
# wrkflw validation (runs first)
sh "$src" "\$@" || exit 1
# Original hook
$EXISTING
WRAPPER
  else
    echo "[install-hooks] Installing $name hook..."
    cp "$src" "$dst"
  fi

  chmod +x "$dst"
}

install_hook "pre-commit"
install_hook "commit-msg"

echo "[install-hooks] Done. Git hooks installed."
```

### `mise.toml` addition

```toml
[tasks."hooks:install"]
description = "Install Git pre-commit and commit-msg hooks (idempotent)"
run = "sh scripts/install-hooks.sh"
```

### Quickstart entry

Documented in `quickstart.md`: new contributors run `mise run hooks:install` once after cloning.

## Complexity Tracking

No violations to justify.
