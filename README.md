# OpenTeams

OpenTeams is a correctness-first team platform for Paper and Folia. The project
separates an immutable public API from the Paper implementation and extension
registries used by independent addons.

## Current development baseline

- Java 21 bytecode
- Paper 1.21.11+
- SQLite, MySQL and MariaDB
- Transactional membership, join-request, ban, role and setting changes
- Correlated audit/result/post-event mutation pipeline
- Timeout-bounded addon policy hooks and plugin-owned extension lifecycle
- Cache-only chat and friendly-fire hot paths
- Persistent team-chat toggle and staff spy
- Read-only degradation, database lease recovery and cache rebuild
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

## Modules

- `openteams-api` — immutable snapshots, async services and addon registries
- `openteams-core` — plugin runtime, SQL persistence, cache and gameplay rules
- `openteams-dialog-ui` — isolated UI adapter and chat fallback
- `openteams-test-kit` — fixtures for addon developers
- `openteams-example-addon` — public API compatibility example

OpenTeams is licensed under Apache-2.0.
