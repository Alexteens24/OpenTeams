# OpenTeams

OpenTeams is a correctness-first team platform for Paper and Folia. The project
separates an immutable public API from the Paper implementation and extension
registries used by independent addons.

Documentation: [alexteens24.github.io/OpenTeams](https://alexteens24.github.io/OpenTeams/)

## Current development baseline

- Java 21 bytecode
- Paper 1.21.11+
- SQLite, MySQL and MariaDB
- Transactional membership, join-request, ban, role and setting changes
- Correlated audit/result/post-event mutation pipeline
- Timeout-bounded addon policy hooks and plugin-owned extension lifecycle
- Cache-only chat and friendly-fire hot paths
- Persistent team-chat toggle and staff spy
- Dialog-first player command center with public-team discovery, invitation and
  join-request inboxes, offline roster names and risk-based confirmations
- Clickable chat/command fallback when Paper Dialogs are disabled or unavailable
- Read-only degradation, database lease recovery and cache rebuild
- Transaction-level fencing against stale writers after lease takeover
- Atomic cache publication and batched recovery
- Admin doctor and retention cleanup commands
- Folia-aware entity/global scheduling

## Build

```bash
./gradlew clean build
```

The deployable plugin is generated at
`openteams-core/build/libs/openteams-core-<version>.jar`.

The current `V1__core_schema.sql` is a consolidated development baseline.
Delete development databases created by older alpha schemas; upgrade migrations
will begin only after the first published schema is frozen.

The bundled UI defaults to Vietnamese with a consistent small-caps style. Set
`ui.follow-player-locale: true` to honor each Minecraft client's locale instead;
unsupported or incomplete locales fall back to `ui.default-locale`.

## Modules

- `openteams-api` — immutable snapshots, async services and addon registries
- `openteams-core` — plugin runtime, SQL persistence, cache and gameplay rules
- `openteams-dialog-ui` — isolated UI adapter and chat fallback
- `openteams-test-kit` — fixtures for addon developers
- `openteams-example-addon` — public API compatibility example

OpenTeams is licensed under Apache-2.0.
