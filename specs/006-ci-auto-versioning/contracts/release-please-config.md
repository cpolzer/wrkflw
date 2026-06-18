# Contract: release-please Configuration Schema

Two files govern release-please behaviour. Both are committed to the repository root.

## release-please-config.json

Defines strategy, scope, and extra files to update on each release.

```json
{
  "$schema": "https://raw.githubusercontent.com/googleapis/release-please/main/schemas/config.json",
  "release-type": "simple",
  "packages": {
    ".": {
      "changelog-path": "CHANGELOG.md",
      "release-as": "0.1.0",
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

## .release-please-manifest.json

Machine-maintained state file tracking the current released version. Do not edit manually.

```json
{
  ".": "0.1.0"
}
```

## Conventional Commit → Version Bump Mapping

| Commit prefix | Bump | Example |
|---------------|------|---------|
| `fix:` | patch | `0.1.0 → 0.1.1` |
| `feat:` | minor | `0.1.0 → 0.2.0` |
| `feat!:` or `BREAKING CHANGE:` footer | major | `0.1.0 → 1.0.0` |
| `chore:`, `docs:`, `ci:`, `test:`, `refactor:` | none | no release PR opened |
