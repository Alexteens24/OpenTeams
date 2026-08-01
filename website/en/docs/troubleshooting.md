# Troubleshooting

Before reporting an issue, collect Paper/Folia and Java versions, OpenTeams commit/JAR checksum, database engine/version and namespace, full logs from `Enabling OpenTeams`, doctor output, and the failed mutation's correlation ID.

## `no such table: core_lease_fences`

The JAR may lack migration resources, Flyway/classloader scanning may have failed, an old alpha database may have incomplete history, or the wrong non-shaded artifact was deployed.

Stop and back up, then verify:

```bash
jar tf openteams-core-*.jar | grep V1__core_schema.sql
```

For disposable indev data, remove the old database and sidecars, restart, and confirm Flyway finds/migrates `db/migration/common/V1__core_schema.sql`. Do not create only `core_lease_fences` manually; other tables or constraints may also be wrong.

## “No migrations found” or `READ_ONLY`

Deploy the `openteams-core` shadow JAR and include `jar tf` output in reports. Core binds the thread context classloader for Flyway.

For read-only state, check reachability within the configured timeout, credentials/TLS, pool capacity, competing namespace instances, and host pauses long enough to expire the 45-second lease. Never delete `core_leases` while a process is active. A dead old process must be unable to resume; wait for expiry and observe a fenced takeover.

## Gameplay and lifecycle issues

- **Cannot create after disband:** confirm disband succeeded, `/team info` is empty, runtime is writable, doctor finds no dangling membership, and logs show no conflict/database error. A database/cache disagreement is a consistency bug—record the exact interleaving and correlation ID.
- **Owner cannot leave:** intentional. Transfer with `/team transfer <player>` or disband with `/team disband confirm`.
- **Offline invite cannot resolve:** the player must have joined since OpenTeams was installed so `player_directory` contains their current normalized name in this namespace.
- **Dialog does not open:** use `ui.mode: auto` for chat fallback; record Paper build and Dialog exceptions. Do not hot-reload to test a fix.
- **Chat error hidden:** current code closes Dialog before errors. Report the exact action and client/server version if it remains hidden.
- **Team chat leaks globally after join:** record login/message timestamps, database latency, preference row, reload history, and callback exceptions. Preference state is designed to serialize load/toggle and wait until ready.

## Java `Unsafe` warning and PlugMan

`WARNING: A terminally deprecated method in sun.misc.Unsafe has been called` commonly comes from a dependency on newer Java and is not itself an OpenTeams initialization failure. Distinguish it from a stack trace following `OpenTeams failed to initialize safely`.

PlugMan/hot reload is unsupported. Use a complete server restart.

## Useful bug report template

```text
Expected / Actual / Exact commands or clicks
Paper/Folia + Java / OpenTeams SHA / Database engine
Fresh or reused alpha DB / Startup log / Doctor output / Correlation ID
```
