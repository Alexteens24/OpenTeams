# Release status

<span class="ot-badge">0.1.0 · indev</span>

OpenTeams is under active development. The version in `plugin.yml` is not a promise of production-stable 1.0.

## Implemented

- SQLite, MySQL, and MariaDB JDBC layers backed by HikariCP.
- Consolidated Flyway development baseline.
- Atomic lifecycle, invitation, request, ban, transfer, role, and setting mutations.
- Immutable cache with stale-version rejection and generation-protected membership loading.
- Database lease, fencing, read-only degradation, and recovery cache rebuild.
- Transactional audits sharing correlation IDs with API results and committed events.
- Timeout-bounded addon policies and plugin-owned extension lifecycle.
- Cache-only friendly fire/team chat, persistent chat mode, and staff spy.
- Dialog-first command center, discovery, offline invitations, and clickable fallback.
- English and Vietnamese message catalogs and addon translation resolution.
- SQLite integration tests plus cache, service, chat, and UI localization tests.
- `/teamadmin doctor` and retention cleanup.

## Open release gates

1. Testcontainers coverage for MySQL and MariaDB.
2. Real Paper and Folia process tests in CI.
3. Crash-injection tests around transaction/cache publication boundaries.
4. Public administration for custom role templates and per-role overrides.
5. PlaceholderAPI adapter and chat moderation hook.
6. Complete translations and administrator responses.
7. Benchmarks for chat, damage, placeholders, and high-contention writes.
8. An API compatibility gate such as japicmp and generated reference documentation.

## Schema policy during indev

`V1__core_schema.sql` is a **consolidated development baseline** and may be edited directly while the alpha schema changes.

::: danger Do not upgrade an old alpha database in place
Back it up if it is useful for debugging, then remove the old test database and let OpenTeams recreate it. Versioned upgrade migrations begin only after the first release schema is frozen.
:::

For stopped SQLite servers, remove the database and any existing sidecars: `openteams.db`, `openteams.db-shm`, and `openteams.db-wal` under `plugins/OpenTeams/`.

## Current compatibility

| Surface | Status |
|---|---|
| Paper API | Targets `1.21.11+` |
| Folia | `folia-supported: true` |
| Java | Release 21 bytecode; Gradle toolchain 25 |
| Public API | Separate module, no binary compatibility gate yet |
| Database schema | Indev baseline, no upgrade guarantee |
| PlugMan/hot reload | Unsupported |
| TeamChest | Separate addon milestone, not part of Core JAR |
