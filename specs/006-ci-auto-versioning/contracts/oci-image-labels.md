# Contract: OCI Image Label Schema

All Docker images produced by the release pipeline MUST conform to this label schema. Skopeo verifies compliance after each build.

## Required Labels

```dockerfile
LABEL org.opencontainers.image.version="<MAJOR.MINOR.PATCH>"
LABEL org.opencontainers.image.title="<service-name>"
LABEL org.opencontainers.image.source="https://github.com/cpolzer/wrkflw"
```

## Verification Command

```bash
skopeo inspect docker-daemon:<image>:<tag> \
  | jq -e '.Labels["org.opencontainers.image.version"] == "<expected-version>"'
```

Returns exit code 0 (pass) or 1 (fail). The CI pipeline treats a non-zero exit as a build failure.

## Service Mapping

| Service | Image name | Title label |
|---------|-----------|-------------|
| API service | `wrkflw/api-service` | `api-service` |
| Worker service | `wrkflw/worker-service` | `worker-service` |
| Frontend | `wrkflw/ui` | `ui` |

## Version Format

The `org.opencontainers.image.version` value MUST be the bare semver string without a `v` prefix (e.g., `1.2.0`, not `v1.2.0`). The Git tag uses a `v` prefix (`v1.2.0`); the CI workflow strips it when constructing this label value.
