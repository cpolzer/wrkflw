# Feature Specification: CI Auto-Versioning via Conventional Commits

**Feature Branch**: `006-ci-auto-versioning`

**Created**: 2026-06-19

**Status**: Draft

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
2. **Given** a release PR is merged, **When** CI completes, **Then** Docker images for `api-service` and `worker-service` are pushed to the container registry, tagged with both the exact version (`1.2.3`) and `latest`.
3. **Given** a release PR is merged, **When** CI completes, **Then** the frontend production build is produced and attached to the GitHub Release as a downloadable artifact.
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
- What happens if a Docker image build fails after the release PR is merged? The Git tag and GitHub Release are already created; the pipeline must fail visibly and not silently produce an untagged image.
- What happens if no conventional commits have been merged since the last release? No release PR is opened; the state is stable.
- What happens when two developers merge PRs simultaneously before the release PR is processed? The accumulated changes are consolidated into a single release PR with the highest applicable version bump.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST analyse conventional commit messages on every merge to `main` and determine the appropriate semantic version increment (major, minor, or patch).
- **FR-002**: The system MUST automatically open a pull request (release PR) proposing the new version number and an updated changelog whenever a version-triggering commit is detected.
- **FR-003**: The release PR MUST display the human-readable changelog grouped by commit type (`feat`, `fix`, breaking changes) for all unreleased commits.
- **FR-004**: When the release PR is merged, the system MUST create a Git tag and a GitHub Release using the new version number.
- **FR-005**: When the release PR is merged, the system MUST build and push Docker images for `api-service` and `worker-service`, each tagged with the exact version string and `latest`.
- **FR-006**: When the release PR is merged, the system MUST produce a production build of the frontend and attach it to the GitHub Release as a downloadable archive.
- **FR-007**: The entire product MUST share a single version number; all deployment artifacts published in the same release carry the same version tag.
- **FR-008**: Commits that do not trigger a version change (`chore:`, `docs:`, `ci:`, `test:`, `refactor:`) MUST NOT cause a release PR to be opened.
- **FR-009**: The versioning pipeline MUST be fully automated and require no manual version editing in any build file.
- **FR-010**: Version metadata MUST be embedded in the built artifacts so the running application can report its own version.

### Key Entities

- **Release PR**: An automatically generated pull request that proposes a version bump and changelog update; serves as the human review gate before publication.
- **Semantic Version**: A three-part version number (`MAJOR.MINOR.PATCH`) derived from conventional commit types since the last release.
- **Changelog**: A structured, human-readable record of changes per release, generated from commit messages and stored in the repository.
- **Deployment Artifact**: A versioned, publishable output of the build — Docker image (backend services) or static archive (frontend) — tagged with the release version.
- **Git Tag**: A repository marker (e.g., `v1.2.3`) created at the commit corresponding to the merged release PR, providing a permanent reference to the released state.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A version bump PR is opened within 5 minutes of a conventional-commit PR being merged to `main`, with no manual steps required.
- **SC-002**: 100% of merges to `main` that contain version-triggering commits result in a release PR; 0% of non-triggering merges do.
- **SC-003**: All three deployment artifacts (api-service image, worker-service image, frontend archive) are produced and published within 15 minutes of the release PR being merged.
- **SC-004**: The changelog attached to every GitHub Release contains all `feat:` and `fix:` commits since the previous release and at least one entry for every breaking change.
- **SC-005**: A developer can reproduce a previously released artifact by checking out the corresponding Git tag and running the standard build, with no version-related manual configuration.

---

## Assumptions

- Conventional Commits formatting on all PRs is already enforced or will be enforced before this feature is activated (see spec 002 pre-commit hooks).
- The project uses a monorepo structure where all services and the frontend are released together under a single shared version; independent per-service versioning is out of scope.
- Docker images are published to GitHub Container Registry (`ghcr.io`) using the repository's built-in `GITHUB_TOKEN`; no external registry credentials are required.
- The frontend is shipped as a static production bundle attached to the GitHub Release, not as a standalone Docker image (though adding a frontend Docker image is a straightforward future extension).
- The version string is injected into backend services at build time via Gradle project properties and into the frontend via the build tool's environment variable mechanism.
- The CI runner for release publication has permission to push to `ghcr.io` and to create GitHub Releases and tags.
- Only the `main` branch triggers versioning; feature branches and draft PRs do not.
