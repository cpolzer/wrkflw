# Phase 0 Research: OS-Agnostic Test Infrastructure

**Feature**: `005-os-agnostic-test-infra` | **Date**: 2026-06-13

All findings below are verified against live Maven Central metadata, the actual 2.0.5 jar
contents, the current repository, and the running container engine — not assumed.

## R1: Root cause of the current workaround

**Decision**: The proxy exists solely because Testcontainers 1.21.3 ships a shaded `docker-java`
that pins Docker Engine API version `1.32` in its request paths. Docker 29.x raised its
`MinAPIVersion` above `1.32`, so the daemon rejects those calls with
`client version 1.32 is too old`.

**Evidence**: Host runs Docker `29.5.2` (`docker version` → Server 29.5.2). `scripts/docker-api-proxy.py`
rewrites `/v1.3x/` → `/v1.45/` on the Unix socket; `build-logic/src/main/kotlin/testing.gradle.kts`
sets `DOCKER_HOST=unix:///tmp/docker-proxy.sock` only when that socket file exists.

**Rationale**: This is a framework-version problem, not a "Docker unavailable" problem. The fix
belongs in the framework version, not in a host-side shim.

## R2: Testcontainers 2.x resolves it via native API negotiation

**Decision**: Upgrade to Testcontainers **2.0.5** (latest 2.x as of this date). 2.0.2+ restored
proper Engine-API version negotiation/fallback and works with Docker 29.x natively, making the
proxy obsolete.

**Alternatives considered**:
- *Keep 1.21.3, pin `api.version` via `~/.testcontainers.properties` or env* — rejected:
  per-user home config is not checked in (not standardized), and env wiring in the build is the
  same kind of host-specific branch we are removing.
- *Keep 1.21.3, auto-manage the proxy lifecycle from Gradle* — rejected: a Unix-socket proxy is
  Linux/macOS-only (Windows uses named pipes), so it cannot satisfy the OS-agnostic goal (FR-003).
- *Downgrade host Docker* — rejected: not portable, not enforceable across machines/CI.

**Rationale**: Upgrading deletes the problem class entirely and hands engine discovery +
protocol negotiation to the framework, satisfying FR-001/FR-002/FR-004.

## R3: Exact 2.x coordinates (verified on Maven Central)

**Decision**: Module artifact IDs gained a `testcontainers-` prefix in 2.0. Verified all four
artifacts exist at 2.0.5 via `repo1.maven.org/.../maven-metadata.xml`:

| Current (1.21.3) coordinate | 2.x coordinate | Change |
|---|---|---|
| `org.testcontainers:testcontainers` | `org.testcontainers:testcontainers` | unchanged |
| `org.testcontainers:testcontainers-bom` | `org.testcontainers:testcontainers-bom` | unchanged |
| `org.testcontainers:junit-jupiter` | `org.testcontainers:testcontainers-junit-jupiter` | **renamed** |
| `org.testcontainers:postgresql` | `org.testcontainers:testcontainers-postgresql` | **renamed** |

**Rationale**: Only the two prefixed renames touch `gradle/libs.versions.toml`; the core and BOM
module strings stay as-is (they were already `testcontainers`/`testcontainers-bom`).

## R4: Package relocations (verified by inspecting the 2.0.5 jars)

**Decision**:
- `@Testcontainers` and `@Container` annotations remain at `org.testcontainers.junit.jupiter`
  in `testcontainers-junit-jupiter-2.0.5.jar` → **no import change** for annotations.
- `PostgreSQLContainer` now lives at `org.testcontainers.postgresql.PostgreSQLContainer`. The
  old `org.testcontainers.containers.PostgreSQLContainer` is **retained as a deprecated shim**
  in the same jar → existing imports still compile, but emit deprecation warnings.

**Decision**: Migrate the 6 import sites to the new `org.testcontainers.postgresql` package to
keep the build warning-clean. (Verified the project does not set `allWarningsAsErrors`, so this
is hygiene, not a hard compile blocker.)

**Import sites (6 files)**:
- `adapters/persistence-postgres/.../TaskConcurrencyTest.kt`
- `apps/api-service/.../SubmitDocumentE2ETest.kt`
- `apps/api-service/.../MultiStageFlowE2ETest.kt`
- `apps/api-service/.../ClaimDecideE2ETest.kt`
- `apps/api-service/.../WorkListAndStatusE2ETest.kt`
- `apps/api-service/.../PerfSmokeTest.kt`

## R5: Other 2.0 breaking changes vs. this project

**Decision**: No further changes required.
- *JUnit 4 support dropped* — project uses JUnit 5 (`useJUnitPlatform()`, Jupiter) only.
- *Minimum Java 17* — project toolchain is JDK 21 (`jvmToolchain(21)`). Satisfied.

## R6: The `adapters/temporal` module declares unused Testcontainers deps

**Decision**: `adapters/temporal/build.gradle.kts` declares `testImplementation(libs.testcontainers)`
and `libs.testcontainers.junit5`, but **no test under `adapters/temporal/src/test` imports any
Testcontainers type** (its tests use the in-JVM `TestWorkflowEnvironment` only). Remove these two
unused test dependencies rather than migrate them.

**Rationale**: Migrating dead dependencies would carry the cruft forward. Removing them shrinks
the surface and is verified safe (no imports reference them). This keeps "the three modules that
declare container test dependencies" honest — only `persistence-postgres` and `api-service`
genuinely need them after this change.

## R7: CI on GitHub-hosted runners needs no proxy and no Docker-in-Docker

**Decision**: Delete the `Start Docker API proxy (Docker 29.x compat)` step from
`.github/workflows/ci.yml`. **Docker-in-Docker (DinD) is not necessary.**

**Evidence**: The `ci` job runs `runs-on: ubuntu-latest` with **no `jobs.<id>.container:` key**.
On GitHub-hosted runners the job executes directly on the runner VM, where Docker is
pre-installed and the daemon socket (`/var/run/docker.sock`) is available on the host.
Testcontainers connects to that host daemon directly — the canonical, supported GitHub Actions
setup.

**When DinD *would* be required (and why it is not here)**:
- If the CI job itself ran inside a container (`container:` key) without the host socket mounted —
  not our configuration.
- On a self-hosted runner lacking a Docker daemon — not our configuration (GitHub-hosted).

With TC 2.x negotiating the API version, even the current step's conditional ("start proxy only
if `MinAPIVersion < 1.40`") becomes dead logic. The clean outcome is removing the step entirely;
no DinD, no sidecar, no socket override — identical to the local path (FR-003, US2).

**Alternative considered**: *Add a DinD service / `container:` job for hermetic isolation* —
rejected as unnecessary complexity. It would make CI diverge from the local invocation (the very
gap US2 closes) and provides no benefit over the host daemon for these tests.

## R8: Constitution conflict — amendment required

**Decision**: This feature **conflicts with the current constitution** and requires an amendment,
tracked as a follow-up to the implementation.

**Evidence**: `.specify/memory/constitution.md` **Principle VI (Local Validation Before Push)**
currently states the proxy *MUST* be running:
> "On Docker 29.x+ hosts, `scripts/docker-api-proxy.py` MUST be running locally before executing
> tests; its absence will cause Testcontainers to fail..."
The **Development Workflow & Quality Gates → Local validation gate** bullet repeats this mandate.

**Rationale**: Deleting the proxy makes a constitutional MUST reference a non-existent file. The
amendment **strengthens** Principle VI's intent (frictionless local validation) by removing the
manual prerequisite; it does not weaken any Core Principle. The Constitution Check gate below
records this as a required, non-blocking amendment (the principle's *goal* is preserved). The
amendment is a documentation change executed as part of this feature, bumping the constitution to
the next MINOR version per its own Governance rules.

## Summary of all touch points

| File / artifact | Action |
|---|---|
| `gradle/libs.versions.toml` | bump `testcontainers = "2.0.5"`; rename `junit-jupiter`→`testcontainers-junit-jupiter`, `postgresql`→`testcontainers-postgresql` |
| `adapters/persistence-postgres/build.gradle.kts` | no coordinate text change (uses `libs.*` aliases); deps remain |
| `apps/api-service/build.gradle.kts` | no coordinate text change (uses `libs.*` aliases) |
| `adapters/temporal/build.gradle.kts` | **remove** unused `libs.testcontainers` + `libs.testcontainers.junit5` test deps |
| 6 test files (R4) | migrate `PostgreSQLContainer` import to `org.testcontainers.postgresql` |
| `build-logic/src/main/kotlin/testing.gradle.kts` | **remove** proxy-socket / `DOCKER_HOST` wiring |
| `scripts/docker-api-proxy.py` | **delete** |
| `.github/workflows/ci.yml` | **remove** "Start Docker API proxy" step |
| `.specify/memory/constitution.md` | **amend** Principle VI + Quality Gates to drop proxy mandate (MINOR bump) |
