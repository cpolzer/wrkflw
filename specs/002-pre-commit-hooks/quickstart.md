# Quickstart: Pre-Commit Hooks

## First-time setup (every developer, once per clone)

```sh
mise run hooks:install
```

This copies `hooks/pre-commit` and `hooks/commit-msg` into `.git/hooks/` and makes them
executable. The command is safe to run multiple times.

**Prerequisite**: `mise install` must have been run first (installs JDK 21 for the Gradle daemon).

## What the hooks do

| Hook | Trigger | What it checks |
|------|---------|----------------|
| `pre-commit` | `git commit` | If `.kt` files are staged, runs `./gradlew ktlintCheck detekt` |
| `commit-msg` | `git commit` | Validates the commit message against Conventional Commits format |

## Commit message format

```
type(optional-scope): short description
```

**Allowed types**: `feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`

**Examples**:
```
feat(api): add document submission endpoint
fix(temporal): handle null workflow id on signal
docs: extend architecture explanation for Koin wiring
chore: bump ktlint to 1.5.0
feat!: remove legacy endpoint (breaking change)
```

**Exempt patterns**: Messages starting with `Merge`, `Revert`, or `WIP` skip format validation.

## Emergency bypass

If you need to commit without running hooks (e.g., a hotfix stash):

```sh
git commit --no-verify -m "..."
```

A warning is printed to remind you to validate locally before pushing.

## Verifying hooks are installed

```sh
ls -la .git/hooks/pre-commit .git/hooks/commit-msg
```

Both should be executable (shown with `x` permission bit).
