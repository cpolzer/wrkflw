# Data Model: CI Auto-Versioning

There is no application-layer database schema for this feature. The "data model" is the set of configuration artifacts and the OCI image label contract that define the versioning system's state.

---

## Version Manifest

**File**: `.release-please-manifest.json`

Maintained automatically by release-please. Tracks the current released version per package path.

```json
{
  ".": "0.1.0"
}
```

| Field | Type   | Description |
|-------|--------|-------------|
| `"."` | string | Current semver version of the root package (the entire product) |

---

## release-please Configuration

**File**: `release-please-config.json`

Static configuration file committed to the repository. Defines the release strategy and which additional files receive version updates.

| Field | Value | Description |
|-------|-------|-------------|
| `release-type` | `"simple"` | Single-root semver versioning driven by conventional commits |
| `packages["."]` | object | Root package — the whole monorepo as one releasable unit |
| `extra-files` | array | Additional files updated atomically with each version bump |

**Extra files managed**:

| File | Update mechanism | Pattern |
|------|-----------------|---------|
| `gradle.properties` | Generic regex replace | `version=<semver>` |
| `ui/package.json` | JSON path update | `$.version` |

---

## Gradle Version Property

**File**: `gradle.properties`

| Property | Example | Description |
|----------|---------|-------------|
| `version` | `0.1.0` | The current product version, read automatically by all Gradle subprojects. Replaces the hardcoded `version = "0.1.0-SNAPSHOT"` in individual `build.gradle.kts` files. |

---

## OCI Image Label Contract

All three Docker images (`api-service`, `worker-service`, `ui`) MUST carry these OCI-standard labels, applied at build time via `--build-arg VERSION=<version>`:

| Label | Example value | Source |
|-------|--------------|--------|
| `org.opencontainers.image.version` | `1.2.0` | `--build-arg VERSION` passed by CI |
| `org.opencontainers.image.title` | `api-service` | Hardcoded in Dockerfile |
| `org.opencontainers.image.source` | `https://github.com/cpolzer/wrkflw` | Hardcoded in Dockerfile |

Skopeo verifies `org.opencontainers.image.version` matches the release tag (stripped of the `v` prefix) after each build.

---

## GitHub Release Event

Emitted by release-please when its release PR is merged. The `publish.yml` workflow subscribes to `release: types: [published]`.

| Field | Example | Description |
|-------|---------|-------------|
| `github.event.release.tag_name` | `v1.2.0` | Git tag created by release-please; includes `v` prefix |
| `github.event.release.body` | changelog markdown | Auto-generated from conventional commit history |
| `github.event.release.name` | `v1.2.0` | Release title |

The publish workflow derives `VERSION = tag_name[1:]` (strips `v`) for Docker image tags.

---

## Docker Image Tag Convention

| Tag | Example | Description |
|-----|---------|-------------|
| `<version>` | `1.2.0` | Exact release version; immutable once pushed |
| `latest` | `latest` | Mutable alias to the most recent release |

In the current implementation (mocked push), both tags are applied locally but not pushed to any registry. The tag structure matches what a future Kubernetes deployment manifest or Helm values file would reference.
