# Changelog

## [0.2.0](https://github.com/cpolzer/wrkflw/compare/v0.1.0...v0.2.0) (2026-06-19)


### Features

* adapters and app composition roots ([539553f](https://github.com/cpolzer/wrkflw/commit/539553f518deeb436523c71284e02cbd80a38270))
* **ci:** add JaCoCo coverage and JUnit test reporting ([8b73b77](https://github.com/cpolzer/wrkflw/commit/8b73b775dc45318156dd9a955402bc6e12040721))
* **ci:** auto-versioning pipeline via conventional commits ([#006](https://github.com/cpolzer/wrkflw/issues/006)) ([08cbe75](https://github.com/cpolzer/wrkflw/commit/08cbe7569bd86f46c9b92a8c33b5695bcfa094d9))
* **ci:** implement auto-versioning pipeline (006) ([ba03add](https://github.com/cpolzer/wrkflw/commit/ba03add1ff899b8bce2bda96d739583bbbfb1fc4))
* **contracts:** add TypeSpec as source of truth for REST API contract ([3ca4c10](https://github.com/cpolzer/wrkflw/commit/3ca4c10400a23da689ae83f25d0264c4b8222c84))
* **dev:** add full-stack Docker Compose setup with documentation ([fb52410](https://github.com/cpolzer/wrkflw/commit/fb524107ed892fad32f83b087deb3372fa28b87a))
* **docs:** embed interactive OpenAPI reference via neoteroi-mkdocs ([96455c2](https://github.com/cpolzer/wrkflw/commit/96455c2bbbb85e85d40d9138aaae14f1b8429de5))
* domain and application layers (hexagonal core) ([c0454f6](https://github.com/cpolzer/wrkflw/commit/c0454f6b03b4668f2d3bfac4fe2904c84a2d9ca6))
* **flows:** add GET /flows submitter list endpoint across full stack ([56587ec](https://github.com/cpolzer/wrkflw/commit/56587ecffc214e4a31ffb01d0edf2c1a08080af7))
* **flow:** US3 multi-stage flow — SUBMIT outcome, FLOW_COMPLETED audit, rework cycle ([223795e](https://github.com/cpolzer/wrkflw/commit/223795e53adbb16de70a6785c892f5f5ab60744e))
* **infra:** upgrade Testcontainers to 2.0.5, remove Docker API proxy workaround ([3c04511](https://github.com/cpolzer/wrkflw/commit/3c0451180fc47ccb0fc707f415d39954f8ab7630))
* **infra:** upgrade Testcontainers to 2.0.5, remove Docker API proxy workaround ([aff0a8a](https://github.com/cpolzer/wrkflw/commit/aff0a8ab5ccd6845a04ff9d803898c28a6c43a0e))
* **mise:** add frontend task coverage and include ui:check in ci ([bb950fa](https://github.com/cpolzer/wrkflw/commit/bb950fa9f24335904aaf236debfa12b66a2e94ae))
* Phase 3 Phase 8 — polish, perf smoke test, logging, docs (T064–T070) ([a0eaf3c](https://github.com/cpolzer/wrkflw/commit/a0eaf3c5fda257c736eb12475da97654f420434f))
* Phase 3 US1 — submit document for approval (T025–T033) ([012dd9b](https://github.com/cpolzer/wrkflw/commit/012dd9bdbd8e21983faa3123410be693e251c2ba))
* Phase 3 US2 — claim/release/decide (T034–T045) ([12b8901](https://github.com/cpolzer/wrkflw/commit/12b89012cad002fb3ed8f6a0cee75de9f362ce57))
* Phase 3 US5 — transactional outbox + CloudEvents publishing (T058–T063) ([791ddd9](https://github.com/cpolzer/wrkflw/commit/791ddd94e9d3b553afde4baf94f7c7f284e84fd5))
* **query:** US4 worklist and flow-status query layer ([0d71673](https://github.com/cpolzer/wrkflw/commit/0d71673b468b2c566415b713a1c74966a25a721d))
* **spec:** add Vue/Onyx web frontend specification ([332066b](https://github.com/cpolzer/wrkflw/commit/332066bcce7b75230a9534ae75b811d8aafd0ba8))
* **spec:** Vue/Onyx web frontend specification ([31bc945](https://github.com/cpolzer/wrkflw/commit/31bc9453a4dd08ad3a44f0f35a9f2eb11dcff33b))
* T003/T014/T024 — lint, boundary test, Koin modules ([3d77793](https://github.com/cpolzer/wrkflw/commit/3d77793a48ee741f7b3399adc149a246b447a0e3))
* **tooling:** add pre-commit hooks, commit-msg validation, and lint gate ([a886e28](https://github.com/cpolzer/wrkflw/commit/a886e2888ede326b6b4ca07b2256520a902a164d))
* **ui:** add Submit entry point to MySubmissionsView + fix full-stack submission bugs ([8cd4236](https://github.com/cpolzer/wrkflw/commit/8cd4236e5ffbc75ef3cc5e45d8397ff58b4ce19f))
* **ui:** complete Vue 3/Onyx frontend — all 52 tasks done ([f0fcc9b](https://github.com/cpolzer/wrkflw/commit/f0fcc9bf494831c32be28a4e7743a233c45dca99))


### Bug Fixes

* add Dokka 2.0.0 for dokkaHtmlMultiModule CI task ([f509865](https://github.com/cpolzer/wrkflw/commit/f5098652e23d74398afcc917cf8f62d98beb091c))
* **build-logic:** resolve java.io.File ambiguity in testing convention plugin ([815a956](https://github.com/cpolzer/wrkflw/commit/815a956b079f5e87670d59b61e1471d38a3ececc))
* **ci:** add Postgres service + flywayMigrate→generateJooq dependency ([d248f46](https://github.com/cpolzer/wrkflw/commit/d248f46fe9d602fd2ed1ee85a86cfa886003577f))
* **ci:** install npm deps before ui:check and cache node_modules ([d6d2d03](https://github.com/cpolzer/wrkflw/commit/d6d2d036896aa8f47a954bc882dc1f2bf87e0aa3))
* commit gradle-wrapper.jar and unblock it from *.jar ignore rule ([20c7dea](https://github.com/cpolzer/wrkflw/commit/20c7dea7a52328e04af276faf5d17185c70e13f0))
* **docs:** make docs:coverage degrade gracefully when no test exec files exist ([ba6cb00](https://github.com/cpolzer/wrkflw/commit/ba6cb00d767d1ff7cfd77f90799c4f2be4e93755))
* **docs:** make mise run docs:build succeed with real KDoc content ([78bd02e](https://github.com/cpolzer/wrkflw/commit/78bd02e928dcc8812443205564fbea5c09a82087))
* **infra:** resolve full-stack submission failures found during e2e testing ([f97cb78](https://github.com/cpolzer/wrkflw/commit/f97cb7823db169b421661796afde9b1729c8b68a))
* sequential CI tasks + conditional Docker proxy + optional DOCKER_HOST ([fb2433d](https://github.com/cpolzer/wrkflw/commit/fb2433d66a607b4a3f46c3bd03b8ed08eab2e9a1))
* **spec-006:** resolve all speckit-analyze findings ([cd3e0ad](https://github.com/cpolzer/wrkflw/commit/cd3e0ad8f8390037c7335e862d9f2479a5e21859))
* **tests:** align E2E group names with V3 migration (authors→initiators, reviewers→legal-reviewers) ([1cfa4bf](https://github.com/cpolzer/wrkflw/commit/1cfa4bf5cfd0999c518f3274b575e038d81fca9e))
* **tests:** implement missing interface members in unit test fakes ([f851d15](https://github.com/cpolzer/wrkflw/commit/f851d15f326d5a9510b2b9bf55ca223b18d6186e))
* **types:** resolve TS errors from stricter TypeSpec-generated types ([ea6ba63](https://github.com/cpolzer/wrkflw/commit/ea6ba63048e68dc544bc9e20484e46735b85dee8))
* **ui:** use relative base URL so requests route through Vite proxy ([d3c5cda](https://github.com/cpolzer/wrkflw/commit/d3c5cda2bd3d5d68cc99bf8fe53e96939f6e48e1))

## Changelog

All notable changes to this project are documented here.
This file is maintained automatically by [release-please](https://github.com/googleapis/release-please).
