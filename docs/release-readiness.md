# Core 1.0 release readiness

## Implemented

- Authoritative SQLite/MySQL/MariaDB JDBC layer and automatic Flyway baseline.
- Atomic lifecycle, invitation, join request, ban, owner transfer, role and
  typed addon-setting mutations.
- Immutable cache snapshots with stale-version rejection and explicit
  `LOADING`, `PRESENT`, `ABSENT` and `FAILED` membership state.
- Database lease, degraded read-only mode and cache resynchronization before
  write recovery.
- Monotonic database fencing token validated inside every Core write
  transaction, preventing stale-writer commits after takeover.
- Atomic team/membership cache publication with concurrent stale-version tests.
- Batched online-player recovery with team snapshot deduplication.
- Foreign keys for Core-owned team, role, member, invitation, ban and setting
  relationships.
- Correlation IDs shared by API results, audit records and asynchronous
  post-commit events.
- Timeout-bounded, fail-open addon policy hooks.
- Plugin-owned commands, placeholders, permissions, settings, translations,
  policies and UI contributions with automatic disable cleanup.
- Cache-only friendly-fire and team-chat paths; persisted chat toggle/staff spy.
- `/teamadmin doctor` and confirmed retention cleanup.
- Dialog-first player lifecycle, public-team discovery, player-name directory,
  risk-based confirmations and clickable chat fallback.
- English player-facing message catalog and addon translation resolution.
- SQLite integration tests, cache tests and addon policy/lifecycle tests.

## Release gates still required

- Testcontainers coverage for MySQL and MariaDB, plus real Paper and Folia
  process tests.
- Broader crash-injection tests at each documented transaction/cache boundary.
- Public CRUD/admin tooling for custom role templates and per-role permission
  overrides.
- PlaceholderAPI adapter, chat moderation hook, complete Vietnamese coverage and
  final localization pass for administrative responses.
- Benchmark suite for chat, damage, placeholders and high-contention writes.
- API compatibility check (for example japicmp) and generated reference docs.
- TeamChest remains a separate addon milestone and is not part of the Core jar.

The project version is `1.0.0-SNAPSHOT`; this checklist prevents treating the
artifact as a production-stable 1.0 release prematurely.
