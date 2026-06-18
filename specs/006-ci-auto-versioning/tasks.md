# Tasks: CI Auto-Versioning via Conventional Commits

**Input**: Design documents from `specs/006-ci-auto-versioning/`

**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅ contracts/ ✅

**Organization**: Tasks are grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)

---

## Phase 1: Setup — Version Source Migration

**Purpose**: Establish `gradle.properties` as the single authoritative version source. Must complete before Dockerfiles reference it and before any workflow can inject the correct version.

- [ ] T001 Add `version=0.1.0` to `gradle.properties` (new property; file currently empty)
- [ ] T002 [P] Remove hardcoded `version = "0.1.0-SNAPSHOT"` from `apps/api-service/build.gradle.kts` (inherits from `gradle.properties` automatically)
- [ ] T003 [P] Remove hardcoded `version = "0.1.0-SNAPSHOT"` from `apps/worker-service/build.gradle.kts` (inherits from `gradle.properties` automatically)
- [ ] T004 Verify Gradle resolves version correctly: run `./gradlew properties | grep '^version'` and confirm output is `version: 0.1.0` for both app subprojects

---

## Phase 2: Foundational — OCI Labels, Dockerfiles, release-please Config

**Purpose**: Core artifacts that all workflows depend on. Must be complete before any GitHub Actions workflow is meaningful.

**⚠️ CRITICAL**: Phase 3 (US1) and Phase 4 (US2) cannot be verified until this phase is complete.

- [ ] T005 [P] Add `ARG VERSION` + three OCI labels to the final stage of `apps/api-service/Dockerfile` per `contracts/oci-image-labels.md` (`org.opencontainers.image.version`, `.title="api-service"`, `.source`)
- [ ] T006 [P] Add `ARG VERSION` + three OCI labels to the final stage of `apps/worker-service/Dockerfile` per `contracts/oci-image-labels.md` (`.title="worker-service"`)
- [ ] T007 Create `ui/Dockerfile` — multi-stage: Node 22 Alpine builder with `ARG VERSION`, `ENV VITE_APP_VERSION=$VERSION`, `npm ci`, `npm run build`; then Nginx Alpine serving `dist/` with OCI labels per `contracts/oci-image-labels.md` (`.title="ui"`)
- [ ] T008 Create `release-please-config.json` at repository root per `contracts/release-please-config.md`: `release-type: simple`, root package `.`, `extra-files` updating `gradle.properties` (generic regex `version=.*` → `version=${version}`) and `ui/package.json` (json `$.version`), `changelog-path: CHANGELOG.md`
- [ ] T009 Create `.release-please-manifest.json` at repository root with initial state `{ ".": "0.1.0" }` to align with the version set in T001

**Checkpoint**: Run `docker build --build-arg VERSION=0.1.0-test -t wrkflw/api-service:test -f apps/api-service/Dockerfile .` and `skopeo inspect docker-daemon:wrkflw/api-service:test | jq '.Labels["org.opencontainers.image.version"]'` — should return `"0.1.0-test"`. Repeat for worker-service and ui.

---

## Phase 3: User Story 1 — Automated Version Bump After Merge (Priority: P1) 🎯 MVP

**Goal**: Every conventional-commit merge to `main` triggers release-please to open or update a Release PR with the correct semantic version bump and changelog section.

**Independent Test**: Push a branch with `feat: add placeholder feature` as the commit message, merge it to `main` via a PR, and verify that release-please opens a PR titled `chore(main): release 0.2.0` within 5 minutes.

### Implementation for User Story 1

- [ ] T010 [US1] Create `.github/workflows/release-please.yml` triggered on `push: branches: [main]` using `googleapis/release-please-action@v4` with `config-file: release-please-config.json` and `manifest-file: .release-please-manifest.json`
- [ ] T011 [US1] Add `permissions: contents: write, pull-requests: write` to the `release-please` job in `.github/workflows/release-please.yml`
- [ ] T012 [US1] Verify the workflow does not conflict with the existing `ci.yml` concurrency group — confirm the `release-please.yml` job has a distinct concurrency key (e.g., `release-please-${{ github.ref }}`)

**Checkpoint**: After merging T010–T012, push a `fix:` commit to `main`. Within 5 minutes a Release PR must appear proposing version `0.1.1` with a CHANGELOG entry. No Release PR appears for a `chore:` commit.

---

## Phase 4: User Story 2 — Release Publication on Release PR Merge (Priority: P1)

**Goal**: Merging the release PR triggers `publish.yml`, which builds all three Docker images, scans each with Trivy, and verifies OCI labels with skopeo. The GitHub Release body contains the changelog. No images are pushed to a registry.

**Independent Test**: Merge the Release PR created in US1 verification. Confirm: (a) a Git tag `v0.1.1` exists, (b) a GitHub Release titled `v0.1.1` with changelog body exists, (c) the `publish.yml` workflow run completes without errors, (d) the Trivy step produces output with no CRITICAL/HIGH findings, (e) the skopeo step outputs `true`.

### Implementation for User Story 2

- [ ] T013 [US2] Create `.github/workflows/publish.yml` triggered on `on: release: types: [published]`; add top-level `env: VERSION: ${{ github.event.release.tag_name }}` and a strip-`v` step (`VERSION_BARE: ${{ env.VERSION[1:] }}` via `run: echo "VERSION_BARE=${GITHUB_REF_NAME#v}" >> $GITHUB_ENV`)
- [ ] T014 [US2] Add a `build-and-verify` job with `strategy: matrix` covering three entries: `{service: api-service, dockerfile: apps/api-service/Dockerfile}`, `{service: worker-service, dockerfile: apps/worker-service/Dockerfile}`, `{service: ui, dockerfile: ui/Dockerfile}`; set `fail-fast: false` so a single image failure does not cancel sibling builds
- [ ] T015 [US2] Add `docker build` step to `publish.yml` matrix job: `docker build --build-arg VERSION=${{ env.VERSION_BARE }} -t wrkflw/${{ matrix.service }}:${{ env.VERSION_BARE }} -t wrkflw/${{ matrix.service }}:latest -f ${{ matrix.dockerfile }} .`
- [ ] T016 [P] [US2] Add `aquasecurity/trivy-action` step after build in `publish.yml`: `image-ref: wrkflw/${{ matrix.service }}:${{ env.VERSION_BARE }}`, `exit-code: 1`, `severity: CRITICAL,HIGH`, `format: sarif`, `output: trivy-${{ matrix.service }}.sarif`
- [ ] T017 [P] [US2] Add skopeo label verification step after Trivy in `publish.yml`: `sudo apt-get install -y skopeo` then `skopeo inspect docker-daemon:wrkflw/${{ matrix.service }}:${{ env.VERSION_BARE }} | jq -e --arg v "${{ env.VERSION_BARE }}" '.Labels["org.opencontainers.image.version"] == $v'` — non-zero exit fails the job
- [ ] T018 [US2] Add `github/codeql-action/upload-sarif@v3` step after Trivy in `publish.yml` to upload `trivy-${{ matrix.service }}.sarif` to GitHub Security tab; add `permissions: security-events: write` to the job

**Checkpoint**: Trigger manually via a test release tag. Confirm all three matrix entries in `publish.yml` pass the build → Trivy → skopeo → SARIF upload sequence.

---

## Phase 5: User Story 3 — Developer Changelog Visibility (Priority: P2)

**Goal**: `CHANGELOG.md` in the repository is kept up to date by release-please and the GitHub Release body is identical to the latest changelog section.

**Independent Test**: After a release, open `CHANGELOG.md` in the repository — it must contain a section headed `## [0.1.1]` listing all `feat:` and `fix:` commits since the previous release, grouped by type. The GitHub Release body must contain the same text.

### Implementation for User Story 3

- [ ] T019 [US3] Confirm `CHANGELOG.md` is listed in `release-please-config.json` as `changelog-path` (already set in T008); if `CHANGELOG.md` does not yet exist in the repository, create it with an empty `# Changelog` heading so release-please can prepend to it
- [ ] T020 [US3] Ensure `CHANGELOG.md` is NOT listed in `.gitignore` (check all `.gitignore` files in the repo); it must be tracked by git so release-please can commit updates to it in the Release PR

**Checkpoint**: After the US1 verification release PR is merged, `git log --oneline` must show a commit from the release-please bot that modified `CHANGELOG.md`. The file must contain the new version section.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Validation, documentation, and local parity checks.

- [ ] T021 [P] Add `CHANGELOG.md` and `.release-please-manifest.json` to the `exclude_docs` list in `mkdocs.yml` if they appear under `docs/` — they are not documentation site content (verify paths first; likely at repo root and already excluded)
- [ ] T022 Run `mise run ci` locally and confirm it passes after all Dockerfile and build file changes from Phases 1–2; this validates Principle VI compliance
- [ ] T023 [P] Execute the local verification commands from `specs/006-ci-auto-versioning/quickstart.md` for all three images — build, skopeo inspect, and Trivy scan — confirm each passes without errors
- [ ] T024 Update `specs/006-ci-auto-versioning/checklists/requirements.md` to mark all items resolved now that implementation is complete

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 (T001 must set version before Dockerfiles reference it)
- **Phase 3 (US1)**: Depends on Phase 2 (config files must exist before workflow references them)
- **Phase 4 (US2)**: Depends on Phase 3 (the `release:published` event is created by US1's release-please)
- **Phase 5 (US3)**: Depends on Phase 2 (changelog path config set in T008); can run in parallel with Phase 3
- **Phase 6 (Polish)**: Depends on all preceding phases

### User Story Dependencies

- **US1 (P1)**: Requires Phase 1 + Phase 2 complete — no dependency on US2 or US3
- **US2 (P1)**: Requires US1 complete (needs a GitHub Release to trigger on)
- **US3 (P2)**: Requires Phase 2 complete (T008 sets changelog-path) — independent of US2

### Within Each Phase

- T002 and T003 are parallel (different files)
- T005, T006, T007 are parallel (different files)
- T016 and T017 are parallel within each matrix entry (different CI steps, same job)
- T019 and T020 are parallel (different files)

---

## Parallel Execution Examples

### Phase 2 — Run simultaneously

```
Task T005: Add OCI labels to apps/api-service/Dockerfile
Task T006: Add OCI labels to apps/worker-service/Dockerfile
Task T007: Create ui/Dockerfile
Task T008: Create release-please-config.json
Task T009: Create .release-please-manifest.json
```

### Phase 4 — Within publish.yml per matrix entry

```
# After T015 (docker build) completes for each service:
Task T016: Trivy scan  ─┐
Task T017: skopeo verify ┘  (parallel, different commands, same image)
```

---

## Implementation Strategy

### MVP First (US1 + US2 — the complete release pipeline)

1. Complete Phase 1: Version source migration (T001–T004)
2. Complete Phase 2: Dockerfiles + config files (T005–T009)
3. Complete Phase 3: release-please workflow (T010–T012)
4. Complete Phase 4: publish workflow (T013–T018)
5. **STOP and VALIDATE**: Trigger a test release end-to-end per `quickstart.md`
6. Then add Phase 5 (US3 changelog) and Phase 6 (polish)

### Incremental Delivery

1. Phase 1 + 2 → Docker images buildable locally with version labels ✓
2. Phase 3 → Automated Release PRs on every conventional commit merge ✓
3. Phase 4 → Full release pipeline: build + scan + verify on Release PR merge ✓
4. Phase 5 → Changelog in repo + GitHub Release body ✓
5. Phase 6 → Local parity + docs ✓

---

## Notes

- No unit tests are generated for this feature: CI workflow files are infrastructure-as-code; correctness is validated by the Trivy + skopeo gates and by the end-to-end acceptance scenarios in `spec.md`
- All Docker builds in the publish workflow use the full monorepo context (`.`) as build context — required because the Kotlin Dockerfiles call `./gradlew` from the repo root
- `fail-fast: false` on the matrix is intentional: a failed ui image build should not cancel the already-running api-service scan
- The `VERSION_BARE` env var strips the `v` prefix from the git tag (`v0.1.0` → `0.1.0`) for OCI label and image tag consistency
