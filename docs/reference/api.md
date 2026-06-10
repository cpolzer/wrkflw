# Reference: Kotlin API (KDoc)

The Kotlin API reference is generated from KDoc comments by **Dokka** and published to <a href="../kdoc/index.html"><code>reference/kdoc/</code></a>.

To regenerate locally:

```bash
mise run docs:api     # runs ./gradlew dokkaHtmlCollector, copies output to docs/reference/kdoc/
mise run docs:build   # full site including the API reference
```

## What belongs here vs. elsewhere

- **This page (Dokka)** — types, functions, and contracts of the code itself (especially the
  `domain` ports and aggregates).
- **[REST API](rest-api.md)** — the external HTTP contract.
- **[Database](database.md)** — the persisted schema.

## KDoc conventions

- Document **ports** (interfaces in `domain/.../port`) thoroughly — they are the seams of the
  system and the most valuable API surface.
- Keep comments about **intent and invariants**, not restatements of the signature (see the
  project's comment-quality expectations).
