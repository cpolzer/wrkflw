# Quickstart: CI Auto-Versioning

## How a release happens

```
Developer PR (feat: ...) ──merge to main──► release-please opens Release PR
                                                        │
                                              maintainer reviews & merges
                                                        │
                                         GitHub Release created (v1.2.0)
                                                        │
                                    publish.yml fires on release:published
                                                        │
                              ┌─────────────────────────┼────────────────────────┐
                              ▼                         ▼                        ▼
                    docker build api-service   docker build worker-service   docker build ui
                              │                         │                        │
                         trivy scan              trivy scan                trivy scan
                              │                         │                        │
                         skopeo verify           skopeo verify            skopeo verify
                              │                         │                        │
                              └─────────────────────────┴────────────────────────┘
                                                        │
                                               release complete ✓
```

## Making a version-triggering commit

```bash
# Patch bump (bug fix)
git commit -m "fix: correct task status transition on timeout"

# Minor bump (new feature)
git commit -m "feat: add bulk task assignment endpoint"

# Major bump (breaking change)
git commit -m "feat!: rename ClaimTask command fields

BREAKING CHANGE: taskId field renamed to id in ClaimTaskCommand"
```

## Commits that do NOT trigger a release

```bash
git commit -m "chore: update dependencies"
git commit -m "docs: add integration test guide"
git commit -m "ci: add coverage reporting"
git commit -m "test: add concurrency test for task claim"
git commit -m "refactor: extract version parsing utility"
```

## Checking the current version

```bash
# From gradle.properties (authoritative)
grep '^version=' gradle.properties

# From the release-please manifest
cat .release-please-manifest.json

# From the frontend package.json
cat ui/package.json | jq '.version'
```

## Verifying an image label after a local build

```bash
VERSION=1.2.0

# Build (example: api-service)
docker build --build-arg VERSION=$VERSION \
  -t wrkflw/api-service:$VERSION \
  -f apps/api-service/Dockerfile .

# Verify label with skopeo
skopeo inspect docker-daemon:wrkflw/api-service:$VERSION \
  | jq '.Labels["org.opencontainers.image.version"]'
# expected output: "1.2.0"

# Run Trivy scan
trivy image --exit-code 1 --severity CRITICAL,HIGH wrkflw/api-service:$VERSION
```

## Initial setup (one-time)

1. Add `version=0.1.0` to `gradle.properties`
2. Remove `version = "0.1.0-SNAPSHOT"` from `apps/api-service/build.gradle.kts` and `apps/worker-service/build.gradle.kts`
3. Commit `release-please-config.json` and `.release-please-manifest.json` to `main`
4. Add `RELEASE_PLEASE_TOKEN` to repository secrets (GitHub token with `contents: write` and `pull-requests: write`)
5. Merge a `feat:` commit — the release-please bot will open the first Release PR
