# Feature Specification: CI Auto-Versioning via Conventional Commits

**Feature Branch**: `006-ci-auto-versioning`

**Created**: 2026-06-19

**Status**: Draft

## Clarifications

### Session 2026-06-19

- Q: What is the deployment target for the frontend? → A: Docker image built and verified in CI (consistent with backend services), as preparation for Kubernetes-based deployment. Registry push is mocked to avoid costs.
- Q: Are Docker images pushed to a registry as part of the release? → A: No — images are built, scanned with a static image analysis tool (e.g., Trivy), and structurally verified in CI but not pushed to any remote registry.
- Q: How should built images be verified? → A: Static image scanning (e.g., Trivy/Grype) — scans the image filesystem and dependencies for known CVEs without running the container. No runtime smoke-start required.
- Q: Should `docker-compose.yml` be updated with versioned image tags on release? → A: No — the local `docker-compose.yml` is developer-focused and does not reference CI-built images. Version pinning of compose files is out of scope.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Automated Version Bump After Merge (Priority: P1)

A developer merges a pull request whose commits follow the Conventional Commits convention. The CI pipeline automatically analyses the commit messages and opens a dedicated "release PR" that bumps the shared product version and generates an updated changelog — without any manual intervention.

**Why this priority**: This is the core of the feature. Everything else (artifact publishing, tagging) depends on a correct version being determined from commit history.

**Independent Test**: Merge a PR containing a `feat:` commit and verify that a release PR is opened automatically with the minor version incremented and a changelog entry added.

**Acceptance Scenarios**:

1. **Given** a PR containing only `fix:` commits is merged to `main`, **When** CI completes, **Then** a release PR is opened that bumps the patch version (e.g., `1.0.0 → 1.0.1`) and adds the fix to the changelog.
2. **Given** a PR containing a `feat:` commit is merged to `main`, **When** CI completes, **Then** a release PR is opened that bumps the minor version (e.g., `1.0.0 → 1.1.0`).
3. **Given** a PR containing a breaking change (`feat!:` or `BREAKING CHANGE:` footer) is merged, **When** CI completes, **Then** a release PR is opened that bumps the major version (e.g., `1.0.0 → 2.0.0`).
4. **Given** a PR containing only non-version-triggering commits (`chore:`, `docs:`, `ci:`) is merged, **When** CI completes, **Then** no release PR is opened and no version changes.
5. **Given** multiple conventional-commit PRs are merged before the release PR is reviewed, **When** the release PR is viewed, **Then** it accumulates all pending version bumps correctly (highest bump wins).

---

### User Story 2 - Release Publication on Release PR Merge (Priority: P1)

A maintainer reviews the auto-generated release PR (which contains the bumped version and changelog) and merges it. This triggers the final publishing step: a Git tag and GitHub Release are created, and all deployable artifacts are built and published tagged with the new version.

**Why this priority**: Equally critical — the version bump is only valuable if it results in traceable, versioned deployment artifacts.

**Independent Test**: Merge a release PR and verify that a Git tag, a GitHub Release with the changelog body, and versioned Docker images for both backend services appear in the registry.

**Acceptance Scenarios**:

1. **Given** a release PR is merged, **When** CI completes, **Then** a Git tag (e.g., `v1.2.3`) and a corresponding GitHub Release exist, and the release body contains the generated changelog.
2. **Given** a release PR is merged, **When** CI completes, **Then** Docker images for `api-service`, `worker-service`, and the frontend are built and tagged with the exact version (`1.2.3`) and `latest`, and each image passes static vulnerability scanning with no critical or high-severity findings. No push to a remote registry occurs.
3. **Given** all three Docker images have been built and tagged in CI, **When** an operator pulls the versioned images and starts the stack, **Then** the application runs at exactly the version stated in the image tag.
4. **Given** a release PR is merged, **When** a developer pulls the versioned Docker image, **Then** the image runs the application at exactly the version stated in the tag.

---

### User Story 3 - Developer Changelog Visibility (Priority: P2)

A developer or stakeholder wants to understand what changed between two versions. The changelog is always up to date, machine-generated from commit messages, and readable both in the repository and on the GitHub Releases page.

**Why this priority**: Reduces manual documentation burden and makes releases self-describing, but does not block deployment.

**Independent Test**: After several conventional-commit merges and one release, verify the changelog file in the repository reflects all changes and the GitHub Release body is identical.

**Acceptance Scenarios**:

1. **Given** a release has been published, **When** a user views the GitHub Release page, **Then** the changelog lists all features, fixes, and breaking changes included since the previous release, grouped by type.
2. **Given** a `CHANGELOG.md` (or equivalent) exists in the repository, **When** a new release is published, **Then** the file is prepended with the new release section without overwriting prior history.

---

### Edge Cases

- What happens when the release PR is closed without merging? The bot re-opens or recreates it on the next conventional commit merge.
- What happens if a Docker image build or smoke-verification fails after the release PR is merged? The Git tag and GitHub Release are already created; the pipeline must fail visibly. The image build step must be re-runnable (idempotent) so it can be retried without creating a duplicate release.
- What happens if no conventional commits have been merged since the last release? No release PR is opened; the state is stable.
- What happens when two developers merge PRs simultaneously before the release PR is processed? The accumulated changes are consolidated into a single release PR with the highest applicable version bump.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST analyse conventional commit messages on every merge to `main` and determine the appropriate semantic version increment (major, minor, or patch).
- **FR-002**: The system MUST automatically open a pull request (release PR) proposing the new version number and an updated changelog whenever a version-triggering commit is detected.
- **FR-003**: The release PR MUST display the human-readable changelog grouped by commit type (`feat`, `fix`, breaking changes) for all unreleased commits.
- **FR-004**: When the release PR is merged, the system MUST create a Git tag and a GitHub Release using the new version number.
- **FR-005**: When the release PR is merged, the system MUST build Docker images for `api-service` and `worker-service`, tag each with the exact version string and `latest`, and pass each image through static vulnerability scanning. Registry push is intentionally deferred (mocked) to avoid costs.
- **FR-006**: When the release PR is merged, the system MUST build a Docker image for the frontend, tag it with the exact version string and `latest`, and pass it through static vulnerability scanning, consistent with the backend service images. Registry push is intentionally deferred (mocked) to avoid costs.
- **FR-011**: Static image scanning MUST report any critical or high-severity CVEs found in a built image as a pipeline failure, blocking the release until resolved.
- **FR-007**: The entire product MUST share a single version number; all deployment artifacts published in the same release carry the same version tag.
- **FR-008**: Commits that do not trigger a version change (`chore:`, `docs:`, `ci:`, `test:`, `refactor:`) MUST NOT cause a release PR to be opened.
- **FR-009**: The versioning pipeline MUST be fully automated and require no manual version editing in any build file.
- **FR-010**: Version metadata MUST be embedded in the built artifacts so the running application can report its own version.

### Key Entities

- **Release PR**: An automatically generated pull request that proposes a version bump and changelog update; serves as the human review gate before publication.
- **Semantic Version**: A three-part version number (`MAJOR.MINOR.PATCH`) derived from conventional commit types since the last release.
- **Changelog**: A structured, human-readable record of changes per release, generated from commit messages and stored in the repository.
- **Deployment Artifact**: A versioned Docker image built and verified in CI — one each for `api-service`, `worker-service`, and the frontend — all tagged with the same release version. The uniform image format is a deliberate preparation for Kubernetes-based deployment. Registry push is mocked in the current implementation to avoid costs; the pipeline structure is ready for a real push when a registry is provisioned.
- **Git Tag**: A repository marker (e.g., `v1.2.3`) created at the commit corresponding to the merged release PR, providing a permanent reference to the released state.
- **Static Image Scanner**: A tool (e.g., Trivy, Grype) that analyses a built Docker image's filesystem and package manifests for known CVEs without executing the container. Used as the verification gate before a release is considered complete.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A version bump PR is opened within 5 minutes of a conventional-commit PR being merged to `main`, with no manual steps required.
- **SC-002**: 100% of merges to `main` that contain version-triggering commits result in a release PR; 0% of non-triggering merges do.
- **SC-003**: All three Docker images (api-service, worker-service, frontend) are built, tagged, and statically scanned within 15 minutes of the release PR being merged. No registry push occurs in this phase.
- **SC-006**: Static image scanning produces a machine-readable vulnerability report for every release; zero critical or high-severity CVEs are present in any published image.
- **SC-004**: The changelog attached to every GitHub Release contains all `feat:` and `fix:` commits since the previous release and at least one entry for every breaking change.
- **SC-005**: A developer can reproduce a previously released artifact by checking out the corresponding Git tag and running the standard build, with no version-related manual configuration.

---

## Assumptions

- Conventional Commits formatting on all PRs is already enforced or will be enforced before this feature is activated (see spec 002 pre-commit hooks).
- The project uses a monorepo structure where all services and the frontend are released together under a single shared version; independent per-service versioning is out of scope.
- Docker images are published to GitHub Container Registry (`ghcr.io`) using the repository's built-in `GITHUB_TOKEN`; no external registry credentials are required.
- All three services (api-service, worker-service, frontend) are containerized to a uniform Docker image format. This makes them deployable via the same orchestration tooling and is an explicit preparation step for a future Kubernetes-based deployment.
- Docker images are built and smoke-verified in CI but not pushed to any remote registry. The pipeline is structured so that replacing the mock push step with a real `docker push` to `ghcr.io` (or any OCI-compatible registry) requires only configuration changes, not structural rework.
- The version string is injected into backend services at build time via Gradle project properties and into the frontend via the build tool's environment variable mechanism.
- The CI runner for release publication requires permission to create GitHub Releases and tags, but no container registry credentials are needed in this phase.
- The local `docker-compose.yml` is developer-focused and does not reference CI-built images; it is out of scope for this feature.
- Only the `main` branch triggers versioning; feature branches and draft PRs do not.
