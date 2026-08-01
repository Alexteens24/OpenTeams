# Development

This guide is for contributors working on Core or the public API.

## Repository and toolchain

The Gradle modules are `openteams-api`, `openteams-core`, `openteams-dialog-ui`, `openteams-test-kit`, and `openteams-example-addon`; internal notes live in `docs/` and this VitePress site in `website/`.

The wrapper is Gradle `9.2.1`, using a Java 25 toolchain with compiler `--release 21`, Paper API, JUnit Platform, and Shadow for the deployable Core JAR.

```bash
./gradlew clean test build
./gradlew :openteams-core:test
./gradlew :openteams-example-addon:build
cd website && npm install && npm run docs:build
```

The deployable artifact is the Core shadow JAR; the plain Core JAR task is skipped. Public site base is `/OpenTeams/` and internal links use VitePress routes.

## Test scope and conventions

Current tests cover SQLite mutations/invariants, aggregate and membership reads, cache generation races, service policy/publication, registry cleanup, chat preference state, validation, and localization. MySQL/MariaDB Testcontainers, real Paper/Folia process CI, crash injection, and benchmarks remain release gates.

Public API returns immutable copies. Cached methods state that behavior in their names. Database writes keep lease assertion and audit in the transaction, publish cache only after commit, and never block an entity thread on JDBC. Callbacks reschedule by region/entity. Extensions always have a Bukkit owner and removal path; UI strings use translation keys.

## Adding a mutation

1. Add `MutationType` and a request record if needed.
2. Expose `CompletionStage<OperationResult<TeamSnapshot>>`.
3. Evaluate policies with one stable correlation ID.
4. Implement a JDBC transaction with `assertLease`, permission/priority/domain checks, and audit append.
5. Load the committed snapshot, publish after commit, then emit `TeamMutationCommittedEvent`.
6. Add service, JDBC, cache-race, command/UI, locale, and documentation coverage.

## Adding an extension type

Keep public records in API, accept the actual Bukkit owner, define ownership/collision rules, use a thread-safe Core registry, clean on `PluginDisableEvent`, invalidate materialized cache, document sync/async and timeout behavior, and extend the example addon fixture.

## Schema changes during indev

V1 may currently be consolidated. Update the schema resource, recreate old test databases, update JDBC tests and the Database/Release status pages, and never fake migration history. After schema freeze, never edit a released migration; add a new ordered version.

## Smoke test and pull request checklist

On a clean Paper server: deploy Core, start on supported Java, wait for `WRITABLE`, run doctor, test create → invite → accept → transfer/leave → disband → recreate, offline invite after prior join, chat persistence across reconnect, and clean pool shutdown.

- [ ] `./gradlew test build`
- [ ] `npm run docs:build` for documentation changes
- [ ] No database, build, or `node_modules` artifacts
- [ ] Public API diff reviewed; transaction/cache ordering explained
- [ ] Error/locale keys complete; docs/config/example updated with source
