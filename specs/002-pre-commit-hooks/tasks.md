# Tasks: Pre-Commit Hooks & Commit Convention Enforcement

**Input**: Design documents from `specs/002-pre-commit-hooks/`

**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ quickstart.md ✅

**Tests**: Acceptance tests are manual smoke tests (no automated test surface for shell hook scripts).

**Organization**: Two parallel P1 stories (lint blocking + commit-msg validation) then one P2 story (onboarding installer).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to
- File paths are repo-root-relative

---

## Phase 1: Setup

**Purpose**: Create the versioned hook script files and wire the installer task into the dev workflow.

- [X] T001 Create `hooks/pre-commit` and `hooks/commit-msg` as POSIX sh stubs (`#!/bin/sh` + `set -e` + `exit 0`) in `hooks/`
- [X] T002 Add `[tasks."hooks:install"]` entry to `mise.toml` with `run = "sh scripts/install-hooks.sh"`

**Checkpoint**: `hooks/` directory exists with two executable stubs; `mise run hooks:install` is a recognised task (even if installer script doesn't exist yet).

---

## Phase 2: Foundational

**Purpose**: Create the idempotent installer script that all three user stories depend on to be testable.

- [X] T003 Implement `scripts/install-hooks.sh` — copies `hooks/pre-commit` and `hooks/commit-msg` to `.git/hooks/`, `chmod +x`, wraps existing Entire-CLI `commit-msg` hook via `## wrkflw-managed` marker check (see plan.md design)

**Checkpoint**: `mise run hooks:install` succeeds; `.git/hooks/pre-commit` and `.git/hooks/commit-msg` are installed and executable.

---

## Phase 3: User Story 1 — Blocked commit when linting fails (Priority: P1) 🎯 MVP

**Goal**: `git commit` is rejected immediately when staged `.kt` files violate ktlint or detekt rules.

**Independent Test**: Stage a file containing `val x:Int=1` (missing spaces around `:` — ktlint violation), run `git commit -m "test: trigger lint"`, verify rejection and that the Gradle error output names the file.

### Implementation for User Story 1

- [X] T004 [US1] Implement full `hooks/pre-commit`: detect staged `.kt` files via `git diff --cached --name-only --diff-filter=ACMR | grep '\.kt$'`; skip silently if none; otherwise run `./gradlew ktlintCheck detekt --daemon --quiet` and exit with Gradle's exit code
- [X] T005 [US1] Smoke test US1: install hooks (`mise run hooks:install`), stage a `.kt` file with a known ktlint violation, run `git commit`, confirm exit code non-zero and violation reported; then fix the file and confirm commit succeeds

**Checkpoint**: US1 fully functional — lint-violating commits are blocked; clean commits proceed.

---

## Phase 4: User Story 2 — Blocked commit on invalid message format (Priority: P1)

**Goal**: `git commit` is rejected when the commit message does not match Conventional Commits v1.0 format; the error message shows allowed types and a correct example.

**Independent Test**: Run `git commit --allow-empty -m "fixed stuff"` → must be rejected with example; run `git commit --allow-empty -m "fix(api): handle null workflow id"` → must succeed.

### Implementation for User Story 2

- [X] T006 [P] [US2] Implement full `hooks/commit-msg` in `hooks/commit-msg`: read message from `$1`; exempt `Merge*`, `Revert*`, `WIP*` via `case`; validate against POSIX ERE `^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\([a-z0-9/_-]+\))?(!)?: .{1,100}` using `grep -Eq`; on failure print allowed types + two examples and exit 1
- [X] T007 [US2] Smoke test US2: run `git commit --allow-empty -m "fixed stuff"` → rejected with example output; run `git commit --allow-empty -m "feat(hooks): add conventional commit enforcement"` → accepted; run `git commit --allow-empty -m "Merge branch foo"` → accepted (exemption)

**Checkpoint**: US2 fully functional — bad messages blocked, good messages and exemptions pass.

---

## Phase 5: User Story 3 — Zero-configuration onboarding (Priority: P2)

**Goal**: A new contributor runs `mise run hooks:install` once after cloning; both hooks activate with no manual steps.

**Independent Test**: In a fresh working tree (simulate by temporarily removing `.git/hooks/pre-commit` and `.git/hooks/commit-msg`), run `mise run hooks:install`, verify both hooks are present and executable, then confirm a lint violation is blocked.

### Implementation for User Story 3

- [X] T008 [US3] Verify `scripts/install-hooks.sh` idempotency: run `mise run hooks:install` twice in succession; confirm no duplicate wrapper nesting and hooks remain correct on the second run
- [X] T009 [US3] Verify Entire-CLI co-existence: confirm `.git/hooks/commit-msg` after install contains both the `## wrkflw-managed` marker and the original Entire-CLI delegation; run a bad commit message and confirm it is rejected (our check runs first); run a valid commit and confirm Entire-CLI hook also runs
- [X] T010 [US3] Validate quickstart.md against actual installed behaviour: follow every step in `specs/002-pre-commit-hooks/quickstart.md` and confirm each command/outcome matches; update quickstart if any discrepancy found

**Checkpoint**: US3 fully functional — one command installs hooks; idempotent; Entire CLI preserved.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T011 [P] Verify `--no-verify` bypass: run `git commit --no-verify --allow-empty -m "bad msg"` and confirm commit succeeds (bypass works); confirm quickstart documents this behaviour accurately
- [X] T012 Run `mise run ci` from repo root and confirm all modules still build, lint, and test cleanly (no regressions from the new `hooks/` directory or `mise.toml` change)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 (stubs must exist to be copied)
- **Phase 3 US1**: Depends on Phase 2 (installer must work to smoke test)
- **Phase 4 US2**: Depends on Phase 2; independent of US1 — can run in parallel with Phase 3
- **Phase 5 US3**: Depends on Phases 3 + 4 being complete (verifies full combined behaviour)
- **Phase 6 Polish**: Depends on Phase 5 completion

### User Story Dependencies

- **US1 and US2**: Both depend only on the foundational installer; they are fully independent of each other
- **US3**: Depends on US1 + US2 being implemented (tests the full combined install)

### Within Each User Story

- Implementation task must complete before its smoke test
- T006 (US2) is marked [P] because it touches `hooks/commit-msg` only — it can run in parallel with T004 (US1) which touches `hooks/pre-commit` only

---

## Parallel Opportunities

```
Phase 3 (US1) and Phase 4 (US2) can run in parallel:

  T004 [US1] hooks/pre-commit implementation
  T006 [US2] hooks/commit-msg implementation   ← concurrent

  T005 [US1] smoke test                        ← after T004
  T007 [US2] smoke test                        ← after T006
```

---

## Implementation Strategy

### MVP (US1 only — lint blocking)

1. Phase 1: Create stubs + mise task
2. Phase 2: Implement installer
3. Phase 3: Implement + smoke test pre-commit hook
4. **Stop and validate**: ktlint violations now block commits locally

### Full delivery

1. MVP above
2. Phase 4: Add commit-msg validation (US2)
3. Phase 5: Verify onboarding + idempotency (US3)
4. Phase 6: Polish + CI validation

---

## Notes

- Hook scripts live in `hooks/` (committed, reviewed, diff-able) — never edit `.git/hooks/` directly
- `mise run hooks:install` is the single documented setup command (see `quickstart.md`)
- Entire CLI hooks are preserved; our checks always run first (fail-fast)
- `--no-verify` bypasses all hooks — documented in quickstart.md; no technical enforcement possible
