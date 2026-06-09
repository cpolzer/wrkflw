# Reference: Kotlin API (KDoc)

The Kotlin API reference is generated from KDoc comments by **Dokka**.

!!! info "Not yet generated"
    Dokka output is produced once the Gradle build exists. Wire it up as part of the build
    (a `:dokkaHtmlMultiModule` task across modules) and publish its output into this site (e.g.,
    under `reference/api/`), then link it here.

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
