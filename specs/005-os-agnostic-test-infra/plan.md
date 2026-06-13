# Implementation Plan: OS-Agnostic Test Infrastructure

**Branch**: `005-os-agnostic-test-infra` | **Date**: 2026-06-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/005-os-agnostic-test-infra/spec.md`

## Summary

Backend container-backed tests currently pass only when a hand-started Unix-socket proxy
(`scripts/docker-api-proxy.py`) rewrites the Docker Engine API version, working around
Testcontainers 1.21.3's shaded `docker-java` pinning API `1.32` against a Docker 29.x host that
requires ≥ 1.40. This plan replaces that machine-specific shim by **upgrading Testcontainers to
2.0.5**, whose native API-version negotiation works with Docker 29.x directly. The proxy script,
its conditional `DOCKER_HOST` wiring in the shared test build config, and the CI proxy step are
**deleted**. CI stays on GitHub-hosted `ubuntu-latest` runners using the host Docker daemon — no
Docker-in-Docker required (see research R7). The constitution's Principle VI, which currently
mandates the proxy, is **amended** to drop that requirement. No production code and no test
assertions change.

## Technical Context

**Language/Version**: Kotlin 2.0.21 on JVM (toolchain JDK 21)

**Primary Dependencies**: Testcontainers `1.21.3` → **`2.0.5`** (`testcontainers`,
`testcontainers-junit-jupiter`, `testcontainers-postgresql`, `testcontainers-bom`); JUnit 5
(Jupiter) + Kotest; Temporal `temporal-testing` (in-JVM `TestWorkflowEnvironment`, unaffected)

**Storage**: PostgreSQL 16 (via `PostgreSQLContainer`) for persistence/E2E integration tests

**Testing**: Gradle `test` (JUnit Platform); `./gradlew build` / `mise run ci` is the gate

**Target Platform**: Developer workstations (Linux/macOS) + GitHub-hosted `ubuntu-latest` CI

**Project Type**: Kotlin Gradle multi-module monorepo (hexagonal); change is confined to test
wiring + build/dependency configuration

**Performance Goals**: N/A — no runtime behavior changes. Test execution time should be neutral
or slightly improved (one fewer socket hop than the proxied path).

**Constraints**: OS-agnostic — a single configuration path across Linux/macOS and local/CI with
no environment-specific branching (FR-003). No manual pre-steps (FR-001/FR-002). No coverage loss
(FR-007/SC-005).

**Scale/Scope**: 9 touch points (see research Summary table): 1 version catalog, 3 module build
files, 6 test files (import migration), 1 shared test build config, 1 deleted script, 1 CI
workflow, 1 constitution amendment.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Hexagonal Architecture | ✅ N/A | No domain/application/adapter source changes; only test wiring + build config. Inward-only dependency rule untouched. |
| II. Test-First Discipline | ✅ UPHELD | This feature exists to make the existing integration/E2E tests run reliably and portably. All container-backed integration tests (real Postgres) and the in-JVM workflow tests are preserved; SC-005 requires identical test count and outcomes. No mock substitution introduced. |
| III. Auditability & Traceability | ✅ N/A | No state transitions or audit behavior changed. |
| IV. Orchestration Behind a Port | ✅ N/A | Temporal usage unchanged; `TestWorkflowEnvironment` path untouched. |
| V. Explicit Contracts & Consistency | ✅ N/A | No ports, events, or persisted shapes change. |
| VI. Local Validation Before Push | ⚠️ AMENDMENT REQUIRED | **Principle VI currently mandates `scripts/docker-api-proxy.py` MUST be running**, and the *Development Workflow → Local validation gate* repeats it. This feature deletes that proxy. The constitution MUST be amended to remove the proxy mandate. The amendment *strengthens* Principle VI's goal (frictionless local validation with zero manual pre-steps) and weakens no Core Principle. Recorded in Complexity Tracking; executed as a task in this feature with a MINOR version bump per Governance. |

**Gate result: PASS (with required constitution amendment).** No Core Principle is violated. The
single conflict is a documentation mandate that this feature must update in lockstep; it is
tracked below rather than left implicit.

## Project Structure

### Documentation (this feature)

```text
specs/005-os-agnostic-test-infra/
├── spec.md              ✅ complete
├── plan.md              ✅ this file
├── research.md          ✅ Phase 0 output (verified findings R1–R8)
├── quickstart.md        ✅ Phase 1 output (validation runbook)
├── checklists/
│   └── requirements.md  ✅ spec quality checklist (all pass)
└── tasks.md             📋 Phase 2 output (/speckit-tasks — not yet created)
```

No `data-model.md` — this feature introduces no data entities. No `contracts/` — it adds no
external interface (no API, CLI, or event surface); it is internal build/test wiring only. Both
are intentionally omitted per the template's "skip if not applicable" guidance.

### Source Code (files to change)

```text
gradle/
└── libs.versions.toml                          ← version bump + 2 artifact renames

build-logic/src/main/kotlin/
└── testing.gradle.kts                           ← remove proxy-socket / DOCKER_HOST wiring

adapters/temporal/
└── build.gradle.kts                             ← remove 2 unused Testcontainers test deps

adapters/persistence-postgres/src/test/kotlin/dev/wrkflw/persistence/
└── TaskConcurrencyTest.kt                        ← PostgreSQLContainer import → org.testcontainers.postgresql

apps/api-service/src/test/kotlin/dev/wrkflw/
├── SubmitDocumentE2ETest.kt                      ← PostgreSQLContainer import migration
├── MultiStageFlowE2ETest.kt                      ← PostgreSQLContainer import migration
├── ClaimDecideE2ETest.kt                         ← PostgreSQLContainer import migration
├── WorkListAndStatusE2ETest.kt                   ← PostgreSQLContainer import migration
└── PerfSmokeTest.kt                              ← PostgreSQLContainer import migration

scripts/
└── docker-api-proxy.py                           ← DELETE

.github/workflows/
└── ci.yml                                        ← remove "Start Docker API proxy" step

.specify/memory/
└── constitution.md                               ← amend Principle VI + Quality Gates (MINOR bump)
```

**Structure Decision**: Existing monorepo layout is unchanged. No modules added or moved. The
`adapters/persistence-postgres` and `apps/api-service` modules consume Testcontainers via `libs.*`
catalog aliases, so the version/coordinate change is centralized in `gradle/libs.versions.toml`;
their `build.gradle.kts` files need no text edits. `adapters/temporal` is the only module build
file edited (to drop dead deps).

## Implementation Design

### 1. Version catalog (`gradle/libs.versions.toml`)

- `testcontainers = "1.21.3"` → `testcontainers = "2.0.5"`
- `testcontainers-junit5 = { module = "org.testcontainers:junit-jupiter", ... }`
  → module `"org.testcontainers:testcontainers-junit-jupiter"`
- `testcontainers-postgresql = { module = "org.testcontainers:postgresql", ... }`
  → module `"org.testcontainers:testcontainers-postgresql"`
- `testcontainers` and `testcontainers-bom` module strings unchanged.

### 2. Shared test build config (`build-logic/src/main/kotlin/testing.gradle.kts`)

Remove the `proxySocket` / `proxySocketExists` vals and the `if (proxySocketExists) environment("DOCKER_HOST", ...)`
block. The `tasks.withType<Test>` block keeps `useJUnitPlatform()` and `testLogging`. Result: no
host/socket override anywhere in the build (FR-006).

### 3. Temporal module (`adapters/temporal/build.gradle.kts`)

Remove `testImplementation(libs.testcontainers)` and `testImplementation(libs.testcontainers.junit5)`
(verified unused — R6). Keep `libs.temporal.testing`.

### 4. Test import migration (6 files — R4)

`import org.testcontainers.containers.PostgreSQLContainer`
→ `import org.testcontainers.postgresql.PostgreSQLContainer`.
The `@Testcontainers` / `@Container` imports (`org.testcontainers.junit.jupiter.*`) stay as-is.
No test body or assertion changes.

### 5. Delete the proxy (`scripts/docker-api-proxy.py`)

`git rm scripts/docker-api-proxy.py`.

### 6. CI workflow (`.github/workflows/ci.yml`)

Delete the entire `Start Docker API proxy (Docker 29.x compat)` step (the `curl … MinAPIVersion …`
conditional + `python3 scripts/docker-api-proxy.py &`). The subsequent `Run CI` step
(`mise run ci`) is unchanged and uses the host Docker daemon directly. **No DinD / `container:` /
`services:` keys added** — GitHub-hosted `ubuntu-latest` already provides the daemon (R7).

### 7. Constitution amendment (`.specify/memory/constitution.md`)

In **Principle VI** remove the bullet mandating `scripts/docker-api-proxy.py` and reword to state
that container-backed tests now run against the host engine directly via Testcontainers' native
discovery — no manual pre-step. In **Development Workflow → Local validation gate**, drop the
"start `scripts/docker-api-proxy.py`" sentence. Bump version `1.1.0 → 1.2.0` (MINOR: a
materially relaxed requirement) and update the Sync Impact Report header.

## Validation Strategy

Primary acceptance is the existing suite passing with **no proxy and no `DOCKER_HOST`** set:

1. Ensure no proxy running and no override: `pkill -f docker-api-proxy || true`; `rm -f /tmp/docker-proxy.sock`; `unset DOCKER_HOST`.
2. `./gradlew build` (lint + compile + all tests) — must be green (FR-007, SC-001).
3. Confirm container-backed tests actually started a container (not skipped) — inspect test logs / `docker ps` during the run.
4. Grep guard: repo contains no `docker-api-proxy`, no `docker-proxy.sock`, no test `DOCKER_HOST` wiring (SC-003).
5. `mise run ci` for full local/CI parity, then confirm the GitHub Actions run is green (US2, SC-004).

Detailed runbook in [quickstart.md](./quickstart.md).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Constitution amendment to Principle VI (and Quality Gates) | Principle VI literally mandates the `docker-api-proxy.py` file this feature deletes; leaving it would make a constitutional MUST reference a deleted file and contradict the implemented reality. | "Keep the proxy to avoid touching the constitution" was rejected — it preserves the exact OS-specific, manual-prerequisite friction the feature exists to remove (FR-001/002/003). The amendment relaxes a requirement in line with Principle VI's own stated goal, so it is the minimal honest change. |
