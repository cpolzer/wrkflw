# Research: CI Auto-Versioning via Conventional Commits

## Decision 1: Versioning Tool

**Decision**: `release-please` (Google, v4)

**Rationale**: The spec requires a "release PR" review gate — a human-visible pull request that proposes the version bump before publication. `release-please` is the only mainstream tool built around this exact model: it opens a PR accumulating unreleased conventional commits, and the release fires only when that PR is merged. `semantic-release` is fully automated and has no review gate without extra plugins.

**Alternatives considered**:
- `semantic-release` — publishes on every merge to main with no review PR; rejected because the spec explicitly requires a review gate.
- Manual changelog + tag — rejected; the spec requires full automation with no manual version editing.

---

## Decision 2: Authoritative Version Source

**Decision**: `gradle.properties` as the single version file; `release-please` updates it and also `ui/package.json`.

**Rationale**: Gradle reads `gradle.properties` automatically as project properties, making `version` available to every subproject without changing any `build.gradle.kts`. `release-please`'s `simple` release type + `extra-files` config can update arbitrary files with a version regex, covering both `gradle.properties` and `ui/package.json` in one pass.

**Impact on existing files**:
- `gradle.properties` — add `version=0.1.0`
- `apps/api-service/build.gradle.kts` — remove hardcoded `version = "0.1.0-SNAPSHOT"`
- `apps/worker-service/build.gradle.kts` — remove hardcoded `version = "0.1.0-SNAPSHOT"`
- `ui/package.json` — `"version"` field managed by release-please from this point forward

**Alternatives considered**:
- `version.txt` (default for `simple` type) — rejected; doesn't integrate natively with Gradle or npm without extra read steps.
- Per-component release-please manifests — rejected; the spec mandates a single shared version across all artifacts.

---

## Decision 3: release-please Release Type

**Decision**: `simple` release type at repository root, with `extra-files` updating `gradle.properties` and `ui/package.json`.

**Rationale**: A monorepo with a single shared version is a root-level release. `simple` tracks one version at `.` and can update any additional files via regex-based `extra-files`. This keeps the configuration minimal.

**release-please-config.json shape**:
```json
{
  "release-type": "simple",
  "packages": {
    ".": {
      "extra-files": [
        {
          "type": "generic",
          "path": "gradle.properties",
          "search-value": "version=.*",
          "replace-value": "version=${version}"
        },
        {
          "type": "json",
          "path": "ui/package.json",
          "jsonpath": "$.version"
        }
      ]
    }
  }
}
```

---

## Decision 4: Docker Image Build Strategy

**Decision**: Build all three images via `docker build` in GitHub Actions using a matrix job; pass the release version as `--build-arg VERSION=<tag>`. Embed version as OCI standard label `org.opencontainers.image.version`.

**Rationale**: Existing Dockerfiles for `api-service` and `worker-service` already work. Adding `ARG VERSION` + `LABEL` to each is a minimal change. A matrix job keeps the workflow DRY across three services.

**Frontend Dockerfile**: New `ui/Dockerfile` required — multi-stage: Node 22 Alpine for `npm ci && npm run build`, then Nginx Alpine to serve `dist/`. `VITE_APP_VERSION` env var injected at build time so the app can surface its version.

**Alternatives considered**:
- Jib (Gradle plugin for building JVM images without Docker daemon) — rejected; would require CI runner to have registry access for base images, and doesn't cover the Vue frontend.
- Building inside the existing `./gradlew build` step — rejected; Docker image build is a distinct deployment concern, separate from the compile/test step.

---

## Decision 5: Static Vulnerability Scanner

**Decision**: Trivy via `aquasecurity/trivy-action` GitHub Action.

**Rationale**: Trivy is the most widely adopted open-source scanner, has a first-party GitHub Action, scans OS packages and language-level dependencies (including JVM JARs and npm packages), and produces SARIF output that GitHub Security tab can consume natively. Free with no rate limits on self-hosted images.

**Severity gate**: `CRITICAL,HIGH` — pipeline fails on either; `MEDIUM` and below produce warnings only.

**Alternatives considered**:
- Grype (Anchore) — equally capable; rejected in favour of Trivy only because Trivy's official Action is more actively maintained and has broader language support for the JVM ecosystem.
- Docker Scout — requires Docker Hub account; rejected to avoid external credentials.
- Snyk — commercial, requires token; rejected to avoid costs.

---

## Decision 6: Image Metadata Verifier

**Decision**: Skopeo (`skopeo inspect docker-daemon:<image>`) — available on Ubuntu runners via `apt-get install skopeo`.

**Rationale**: Skopeo can inspect OCI image metadata stored in the local Docker daemon without starting the container, making it a lightweight check that the `ARG VERSION` build argument was actually baked into the image labels. The check script reads `Labels["org.opencontainers.image.version"]` from skopeo JSON output and asserts it matches `github.event.release.tag_name` (strip leading `v`).

**Alternatives considered**:
- `docker inspect` — equivalent output, universally available; rejected only because the spec explicitly calls out skopeo and it adds a tool already appropriate for Kubernetes/OCI workflows.

---

## Decision 7: Publish Workflow Trigger

**Decision**: `on: release: types: [published]` — fires when release-please merges its PR and the GitHub Release is auto-created.

**Rationale**: release-please creates the GitHub Release automatically when its PR is merged. Triggering on `release: published` cleanly separates the "create a version" step (release-please) from the "build and verify artifacts" step (publish workflow). The release tag (`github.event.release.tag_name`, e.g., `v1.2.3`) is available directly as a workflow variable.

**Alternatives considered**:
- Trigger on push to main + detect release-please release commit — fragile; requires parsing commit messages in the workflow.
- Trigger on tag creation — also works but is slightly less semantic than `release: published`.

---

## Decision 8: Version Tag Format

**Decision**: Git tag format `vMAJOR.MINOR.PATCH` (e.g., `v1.2.0`). Docker image tags use the bare version without `v` prefix (e.g., `1.2.0` and `latest`).

**Rationale**: `v`-prefix is the release-please default and the GitHub convention. OCI image tags conventionally omit the `v`. The publish workflow strips the leading `v` when constructing image tags.
