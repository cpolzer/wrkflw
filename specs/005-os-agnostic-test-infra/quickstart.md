# Quickstart / Validation Runbook: OS-Agnostic Test Infrastructure

**Feature**: `005-os-agnostic-test-infra`

This runbook proves the success criteria: container-backed backend tests pass with **zero manual
setup** — no proxy, no `DOCKER_HOST` override — on any machine with a current container engine.

## Prerequisites

- A running container engine (Docker 29.x on the dev workstation; GitHub-hosted runner Docker in CI).
- JDK 21 toolchain (provided via `mise`).

## 1. Start from a clean shell (no workaround active)

```sh
pkill -f docker-api-proxy 2>/dev/null || true
rm -f /tmp/docker-proxy.sock
unset DOCKER_HOST            # ensure no socket override is inherited
docker version --format 'Server {{.Server.Version}} (MinAPIVersion {{.Server.MinAPIVersion}})'
```

Expected: a Docker 29.x server with `MinAPIVersion` ≥ 1.40 — i.e. exactly the environment that
broke Testcontainers 1.21.3 and required the proxy.

## 2. Run the full backend build

```sh
./gradlew build        # lint + compile + all unit & integration tests
```

Expected: **BUILD SUCCESSFUL**, with the container-backed tests passing:
- `adapters/persistence-postgres` → `TaskConcurrencyTest`
- `apps/api-service` → `SubmitDocumentE2ETest`, `MultiStageFlowE2ETest`, `ClaimDecideE2ETest`,
  `WorkListAndStatusE2ETest`, `PerfSmokeTest`

## 3. Confirm a real container actually started (not skipped)

While the suite runs (or from the test logs), confirm a Postgres testcontainer was created:

```sh
docker ps --filter "ancestor=postgres:16-alpine"        # during the run
# or, after the run, check Gradle test output mentions Testcontainers pulling/starting postgres
```

Expected: a transient `postgres:16-alpine` container appears and is torn down — proving the test
talked to the host Docker daemon directly via Testcontainers' native negotiation.

## 4. Regression guard — workaround is gone

```sh
test ! -f scripts/docker-api-proxy.py && echo "OK: proxy script deleted"
! grep -rn "docker-api-proxy\|docker-proxy.sock\|DOCKER_HOST" \
    build-logic .github/workflows .specify/memory \
  && echo "OK: no proxy/socket/host wiring remains"
```

Expected: both `OK:` lines print. (SC-003)

## 5. Coverage parity

Compare the test count before and after the change:

```sh
find . -path '*/build/test-results/test/*.xml' | xargs grep -h "<testsuite " | \
  sed -E 's/.*tests="([0-9]+)".*/\1/' | paste -sd+ | bc
```

Expected: the total equals the pre-change total — no tests lost or silently skipped (SC-005).

## 6. Full CI parity

```sh
mise run ci            # reproduces the exact CI sequence locally
```

Then push and confirm the GitHub Actions `CI` workflow is green. The CI run must succeed **without**
the old "Start Docker API proxy" step and **without** any Docker-in-Docker / `container:` job — the
`ubuntu-latest` runner's host daemon is used directly. (US2, SC-004)

## Rollback

If the upgrade regresses, revert the branch's commits; the prior proxy-based path (Testcontainers
1.21.3 + `scripts/docker-api-proxy.py` + `testing.gradle.kts` `DOCKER_HOST` wiring +
`ci.yml` proxy step + Principle VI proxy mandate) is restored as a unit.
