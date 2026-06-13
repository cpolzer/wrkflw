# Tasks: OS-Agnostic Test Infrastructure

**Feature**: `005-os-agnostic-test-infra` | **Input**: [plan.md](./plan.md), [research.md](./research.md), [spec.md](./spec.md), [quickstart.md](./quickstart.md)

**Tests**: This feature changes test *infrastructure*, not behavior. No new test code is written;
the existing suite IS the test. "Validation" tasks below run that suite as the acceptance check.

## Conventions

- `[P]` = parallelizable (different file, no dependency on an incomplete task).
- `[US1]/[US2]/[US3]` = the user story a task serves (Setup/Foundational/Polish carry no story label).
- Every container-backed validation runs from a **clean shell**: no `docker-api-proxy` process, no
  `/tmp/docker-proxy.sock`, `DOCKER_HOST` unset (see [quickstart.md](./quickstart.md) §1).

---

## Phase 1: Setup

- [ ] T001 Capture the pre-change baseline for coverage parity (SC-005): run `./gradlew test` once on the current branch state with the proxy running as today, and record the total test count and pass/fail per module (save the number in your working notes; it is the comparison target for T015).

---

## Phase 2: Foundational (blocks all user stories)

**Purpose**: The Testcontainers 2.x upgrade is the single prerequisite that makes every user story
possible. Nothing else can be validated until the version + coordinates are in place.

- [ ] T002 Upgrade Testcontainers in `gradle/libs.versions.toml`: set `testcontainers = "2.0.5"`; change the `testcontainers-junit5` module to `org.testcontainers:testcontainers-junit-jupiter`; change the `testcontainers-postgresql` module to `org.testcontainers:testcontainers-postgresql`. Leave the `testcontainers` and `testcontainers-bom` module strings unchanged.
- [ ] T003 Resolve dependencies to confirm the new coordinates download: run `./gradlew :adapters:persistence-postgres:dependencies --configuration testRuntimeClasspath` (and the same for `:apps:api-service`) and verify `testcontainers-junit-jupiter:2.0.5` and `testcontainers-postgresql:2.0.5` appear with no unresolved-dependency errors.

**Checkpoint**: New Testcontainers artifacts resolve. User-story phases can now proceed.

---

## Phase 3: User Story 1 — Zero-setup local test runs (Priority: P1) 🎯 MVP

**Goal**: The full backend suite passes on the Docker 29.x workstation with no proxy and no
`DOCKER_HOST` override.

**Independent Test**: From a clean shell, `./gradlew build` is green and the container-backed tests
start a real `postgres:16-alpine` container (not skipped).

- [ ] T004 [P] [US1] Migrate the `PostgreSQLContainer` import to `org.testcontainers.postgresql.PostgreSQLContainer` in `adapters/persistence-postgres/src/test/kotlin/dev/wrkflw/persistence/TaskConcurrencyTest.kt` (annotations `org.testcontainers.junit.jupiter.*` stay unchanged).
- [ ] T005 [P] [US1] Migrate the `PostgreSQLContainer` import in `apps/api-service/src/test/kotlin/dev/wrkflw/SubmitDocumentE2ETest.kt`.
- [ ] T006 [P] [US1] Migrate the `PostgreSQLContainer` import in `apps/api-service/src/test/kotlin/dev/wrkflw/MultiStageFlowE2ETest.kt`.
- [ ] T007 [P] [US1] Migrate the `PostgreSQLContainer` import in `apps/api-service/src/test/kotlin/dev/wrkflw/ClaimDecideE2ETest.kt`.
- [ ] T008 [P] [US1] Migrate the `PostgreSQLContainer` import in `apps/api-service/src/test/kotlin/dev/wrkflw/WorkListAndStatusE2ETest.kt`.
- [ ] T009 [P] [US1] Migrate the `PostgreSQLContainer` import in `apps/api-service/src/test/kotlin/dev/wrkflw/PerfSmokeTest.kt`.
- [ ] T010 [US1] Remove the proxy wiring from `build-logic/src/main/kotlin/testing.gradle.kts`: delete the `proxySocket` / `proxySocketExists` vals and the `if (proxySocketExists) environment("DOCKER_HOST", ...)` block, keeping `useJUnitPlatform()` and `testLogging`.
- [ ] T011 [US1] Validate US1: from a clean shell (`pkill -f docker-api-proxy || true`; `rm -f /tmp/docker-proxy.sock`; `unset DOCKER_HOST`) run `./gradlew build` and confirm BUILD SUCCESSFUL with all six container-backed tests passing and a `postgres:16-alpine` container observed starting (quickstart §2–3).

**Checkpoint**: MVP delivered — local tests pass with zero manual setup. ✅ SC-001, SC-002.

---

## Phase 4: User Story 2 — CI uses the same path, no special config (Priority: P1)

**Goal**: CI runs the suite on the GitHub-hosted host daemon with no proxy and no Docker-in-Docker.

**Independent Test**: The GitHub Actions `CI` workflow is green with the proxy step removed and no
`container:`/`services:`/DinD keys added.

- [ ] T012 [US2] Remove the `Start Docker API proxy (Docker 29.x compat)` step (the `curl … MinAPIVersion …` conditional and `python3 scripts/docker-api-proxy.py &`) from `.github/workflows/ci.yml`, leaving the `Run CI (mise run ci)` step intact. Do NOT add any `container:`, `services:`, or DinD configuration — the `ubuntu-latest` host daemon is used directly (research R7).
- [ ] T013 [US2] Validate US2: push the branch and confirm the GitHub Actions `CI` workflow passes; verify in the run logs that container-backed tests started a Postgres container and that no proxy step ran.

**Checkpoint**: Local and CI paths are identical. ✅ SC-004.

---

## Phase 5: User Story 3 — Workaround scaffolding fully removed (Priority: P2)

**Goal**: No machine-specific Docker workaround remains anywhere in the repo, and the constitution
no longer references it.

**Independent Test**: Repo-wide grep finds no proxy/socket/host artifacts; the suite still passes.

- [ ] T014 [P] [US3] Delete the proxy script: `git rm scripts/docker-api-proxy.py`.
- [ ] T015 [P] [US3] Remove the unused Testcontainers test dependencies (`testImplementation(libs.testcontainers)` and `testImplementation(libs.testcontainers.junit5)`) from `adapters/temporal/build.gradle.kts`; keep `libs.temporal.testing` (verified no temporal test imports a Testcontainers type — research R6).
- [ ] T016 [US3] Amend `.specify/memory/constitution.md`: in **Principle VI** remove the bullet mandating `scripts/docker-api-proxy.py` and reword it to state container-backed tests now run against the host engine directly via Testcontainers' native discovery (no manual pre-step); in **Development Workflow & Quality Gates → Local validation gate** drop the "start `scripts/docker-api-proxy.py`" sentence; bump the version `1.1.0 → 1.2.0` and update the Sync Impact Report header comment accordingly.
- [ ] T017 [US3] Validate US3 (regression guard, quickstart §4): confirm `scripts/docker-api-proxy.py` no longer exists and `grep -rn "docker-api-proxy\|docker-proxy.sock\|DOCKER_HOST" build-logic .github/workflows .specify/memory` returns nothing.

**Checkpoint**: Zero workaround artifacts remain. ✅ SC-003.

---

## Phase 6: Polish & Validation

- [ ] T018 Coverage parity (SC-005): re-run `./gradlew test`, total the test counts (quickstart §5), and confirm the total matches the T001 baseline — no tests lost or silently skipped.
- [ ] T019 Full local/CI parity: run `mise run ci` end-to-end from a clean shell and confirm it is green (quickstart §6).
- [ ] T020 [P] Update the project memory file `/home/chris/.claude/projects/-home-chris-develop-wrkflw/memory/feedback-testcontainers-docker29.md` to record that the proxy approach is retired in favor of Testcontainers 2.0.5 native negotiation (or delete it if fully obsolete), and update `MEMORY.md` accordingly.

---

## Dependencies & Execution Order

- **Setup (T001)** → first, captures the parity baseline.
- **Foundational (T002–T003)** → blocks everything; must complete before any user story.
- **US1 (T004–T011)** → depends on Foundational. T004–T009 are parallel `[P]` (six independent files); T010 is independent of those; T011 validates after T004–T010.
- **US2 (T012–T013)** → depends on Foundational; independent of US1 (touches only `ci.yml`), though in practice validate US1 first so the CI run is meaningful. T013 requires a push.
- **US3 (T014–T017)** → depends on Foundational. T014 (delete script) should follow T010/T012 so nothing still references the script when removed. T015/T014 are parallel `[P]` (different files). T016 is independent.
- **Polish (T018–T020)** → after all stories; T018/T019 are the final acceptance gate.

## Parallel Opportunities

- The six import migrations T004–T009 can run together (all `[P]`, distinct files).
- T014 and T015 can run together (distinct files).
- T020 can run anytime after T016.

## Implementation Strategy

- **MVP = Phase 1 + Phase 2 + Phase 3 (US1)**: delivers the core value — local tests pass with zero
  setup. Stop-and-ship-able on its own.
- **Increment 2 = US2**: extends the same green path to CI.
- **Increment 3 = US3 + Polish**: removes the dead scaffolding and aligns the constitution, locking
  in the standardization.

## Task Count

20 tasks total — Setup: 1 · Foundational: 2 · US1: 8 · US2: 2 · US3: 4 · Polish: 3.
