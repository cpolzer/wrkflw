# Feature Specification: OS-Agnostic Test Infrastructure

**Feature Branch**: `005-os-agnostic-test-infra`

**Created**: 2026-06-13

**Status**: Draft

**Input**: User description: "Rework the backend integration/E2E test infrastructure to be OS-agnostic and standardized, removing the machine-specific Docker workaround."

## Clarifications

### Session 2026-06-13

- Q: Which container engine(s) must be validated as actually working for this feature to be considered done? → A: Docker only — validate against Docker (29.x locally, host daemon on GitHub CI), the engine actually in use on both the workstation and CI. Other engines remain auto-detected by the test framework but are not part of the acceptance gate.

## User Scenarios & Testing *(mandatory)*

The "users" of this feature are the engineers who run the backend test suite and the
automated CI pipeline that gates merges. The value is a test suite that behaves identically
everywhere, with no per-machine setup ritual.

### User Story 1 - Run the backend test suite on any machine with no manual setup (Priority: P1)

A developer clones the repository (or pulls the latest changes) onto their workstation —
running a current container engine — and runs the standard backend test command. The
container-backed integration and E2E tests start their containers and pass, without the
developer starting any background process, exporting any environment variable, or running any
helper script first.

**Why this priority**: This is the core of the feature. Today the container-backed tests only
pass on a machine where a hand-started proxy process is already running; without it, every
container-backed test fails. Removing that hidden prerequisite is the whole point.

**Independent Test**: On a machine with a modern container engine and no proxy process
running and no test-specific environment overrides set, run the full backend test suite and
observe all container-backed tests pass.

**Acceptance Scenarios**:

1. **Given** a workstation with a current container engine and no helper proxy running,
   **When** the engineer runs the standard backend test command, **Then** all container-backed
   integration and E2E tests start their containers and pass.
2. **Given** a freshly cloned repository, **When** the engineer runs the test suite without
   reading any "first do X" setup note, **Then** the tests succeed on the first attempt.
3. **Given** a developer on a non-Linux operating system, **When** they run the same test
   command, **Then** the tests pass using the same code paths, with no OS-specific branch.

### User Story 2 - CI runs the same tests with no special configuration (Priority: P1)

The continuous-integration pipeline runs the backend test suite using the same command a
developer uses, against the container engine provided by the CI runner, with no
project-specific shim, socket override, or sidecar process.

**Why this priority**: Standardization only delivers value if the local and CI paths are the
same. If CI needs a different setup than local, the "works on my machine" gap reappears.

**Independent Test**: Run the test suite in a clean CI-like environment that provides only a
standard container engine, and confirm the container-backed tests pass with no extra steps.

**Acceptance Scenarios**:

1. **Given** a CI runner with a standard container engine, **When** the pipeline invokes the
   backend test command, **Then** container-backed tests pass without any project-supplied
   proxy, socket path override, or environment shim.
2. **Given** the local and CI invocations, **When** comparing how each reaches the container
   engine, **Then** both use identical configuration with no environment-specific branching.

### User Story 3 - The workaround scaffolding is fully removed (Priority: P2)

A developer auditing the repository finds no machine-specific Docker workaround: no proxy
helper script, and no conditional socket/host wiring in the shared test build configuration.

**Why this priority**: Leftover dead scaffolding is a maintenance and onboarding hazard —
future contributors copy it, re-enable it, or waste time understanding why it exists. Cleanup
locks in the standardization and prevents regression to the old pattern. It is P2 because the
tests can already pass (P1) before the dead code is physically deleted.

**Independent Test**: Search the repository for the proxy helper and the conditional
host/socket wiring; confirm neither exists, and that the test suite still passes.

**Acceptance Scenarios**:

1. **Given** the reworked infrastructure, **When** searching the repository, **Then** the
   machine-specific proxy helper script no longer exists.
2. **Given** the shared test build configuration, **When** reviewing it, **Then** it contains
   no conditional logic that points the container engine at a custom socket or host.
3. **Given** the removal of the workaround, **When** the full backend test suite runs, **Then**
   it still passes.

### Edge Cases

- **No container engine available**: When no container engine is reachable, container-backed
  tests fail (or are skipped) with a clear, actionable message naming the missing prerequisite
  — not an obscure low-level protocol/version error.
- **Stale workaround left running**: If a developer still has the old proxy process or a custom
  host/socket override lingering in their shell, the test suite must still pass and must not
  depend on that override being present.
- **Pure unit tests**: Tests that need no container (domain logic, in-process workflow
  environment) continue to run with no container engine present at all.
- **Older but still-supported container engine**: On an engine version older than the
  developer workstation's but still within the supported range, container-backed tests still
  succeed via the test framework's version negotiation.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The backend test suite MUST start and run its container-backed integration and
  E2E tests successfully on a machine running a current container engine, without any
  manually started helper process.
- **FR-002**: The test suite MUST NOT require any test-specific environment variable, custom
  socket path, or host override to be set in order for container-backed tests to pass.
- **FR-003**: The test suite MUST use a single, identical configuration path across operating
  systems (Linux, macOS) and across local and CI environments — no OS-specific or
  environment-specific branching in how it reaches the container engine.
- **FR-004**: Container engine discovery and protocol-version compatibility MUST be handled by
  the test framework itself, without project-specific shims. **Docker is the only engine that
  MUST be validated** as part of the acceptance gate (the engine in use on the workstation and
  on CI). Other engines the framework auto-detects (e.g. Podman, Colima, Rancher Desktop,
  nerdctl) are expected to work by virtue of that native discovery but are out of scope for
  explicit validation.
- **FR-005**: The machine-specific proxy helper script MUST be removed from the repository.
- **FR-006**: The shared test build configuration MUST contain no conditional logic that
  redirects the container engine to a custom socket or host.
- **FR-007**: The full backend test suite MUST pass after the workaround is removed, with no
  loss of existing test coverage or behavior.
- **FR-008**: When no container engine is available, container-backed tests MUST fail or skip
  with a clear, actionable message rather than a low-level protocol/version error.
- **FR-009**: Project documentation and onboarding notes MUST be updated so they no longer
  instruct developers to start a proxy or set host/socket overrides before running tests.

### Key Entities

- **Container-backed test**: A test that provisions a real backing service (currently a
  relational database) in a disposable container for the duration of the test run.
- **Test framework / container provider**: The component responsible for locating the
  container engine, negotiating the communication protocol version, and managing container
  lifecycle on behalf of the tests.
- **Container engine**: The host service that actually runs containers; its version and
  protocol-compatibility window are what the previous workaround was compensating for.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a developer workstation with a current container engine, running the standard
  backend test command from a clean shell (no proxy process, no test environment overrides)
  results in 100% of previously passing container-backed tests passing.
- **SC-002**: The number of manual steps a developer must perform before running the
  container-backed test suite drops to zero (from the current "start the proxy first").
- **SC-003**: The repository contains zero machine-specific test workaround artifacts — the
  proxy helper script and the conditional host/socket wiring are both absent.
- **SC-004**: The same single test command succeeds in at least two distinct environments
  (a developer workstation and a clean CI-like environment) with no environment-specific
  configuration differences.
- **SC-005**: Total backend test count and pass/fail outcomes are unchanged from before the
  rework, confirming no coverage was lost in the migration.

## Assumptions

- The current container engine in use on the developer workstation is Docker 29.x; the
  framework upgrade targets compatibility with this and newer engines while retaining
  negotiation/fallback for older supported engines.
- The standardization is achieved by upgrading the container test framework (Testcontainers)
  to a version (2.0.2 or later) whose built-in container-engine discovery and protocol-version
  negotiation work natively with current engines, making the proxy obsolete.
- Migration to the newer framework version involves only mechanical changes: updated
  dependency coordinates (module artifacts gained a `testcontainers-` prefix), relocated
  container class packages (e.g. the database container class moved package), and removal of
  legacy test-framework support that the project does not use (JUnit 4). The project runs on a
  JDK that meets the new minimum (JDK 21 ≥ required 17).
- The affected scope is limited to backend test wiring and dependencies: the version catalog,
  the three modules that declare container test dependencies, the test files that import
  container classes, the shared test build configuration, and the proxy helper script. No
  production/runtime code and no test assertions change.
- The in-process workflow test environment (used for orchestration tests) is unaffected — it
  does not use a container and needs no changes.
- CI provides a standard container engine compatible with the upgraded framework; no changes
  to CI runner provisioning are required beyond removing any now-unnecessary proxy setup steps
  if present.
