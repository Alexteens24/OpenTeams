# Installation

This guide takes a stopped Paper/Folia server to a `WRITABLE` OpenTeams runtime using default SQLite storage.

## Requirements

| Component | Value |
|---|---|
| Server | Paper or Folia `1.21.11+` |
| Java runtime | 21 or newer |
| JAR | `openteams-core-1.0.0-SNAPSHOT.jar` |
| Write access | `plugins/OpenTeams/` |
| External database/port | Not needed for SQLite |

## 1. Install the JAR

Build it as described in [Download and build](./download). With the server stopped, copy only the Core shaded JAR into `plugins/`; do not install separate API or Dialog UI JARs.

```bash
cp openteams-core/build/libs/openteams-core-*.jar /path/to/server/plugins/
```

The first start creates `plugins/OpenTeams/config.yml` and `plugins/OpenTeams/openteams.db`.

## 2. Start and verify

```bash
java -jar paper.jar --nogui
```

<div class="ot-flow">
  <div><b>CONFIG</b><small>read config.yml</small></div>
  <div><b>SCHEMA</b><small>validate baseline</small></div>
  <div><b>LEASE</b><small>acquire fence</small></div>
  <div><b>CACHE</b><small>resync online players</small></div>
  <div><b>WRITABLE</b><small>enable mutations</small></div>
</div>

The runtime becomes `WRITABLE` only after initial online-player cache resync succeeds. Run `teamadmin`, `teamadmin doctor`, and `/team`. Expect writable database status, healthy invariants, a Dialog or clickable chat fallback, and a complete Core schema.

## 3. Basic configuration

```yaml
database:
  type: sqlite
  namespace: default
  sqlite-file: openteams.db

ui:
  mode: auto
  default-locale: vi_VN
  follow-player-locale: false
```

Read [Configuration](./configuration) before changing the engine, namespace, pool, or policy timeout.

## MySQL and MariaDB

Create a database user with DDL/DML rights, configure `host`, `port`, `database`, `username`, and `password`, choose `mysql` or `mariadb`, and assign the intended logical namespace. Start and run doctor. Core enables TCP keepalive; MySQL also enables SSL, so configure database certificates appropriately.

## Addons and removal

Addons must declare `depend: [OpenTeams]`, load the API through Bukkit's `ServicesManager`, and never cast the implementation plugin. To uninstall, stop the server, back up data if needed, remove the Core JAR, and optionally remove `plugins/OpenTeams/`.

::: warning SQLite data
Never remove the SQLite file while the server is running. WAL/SHM sidecars may contain state that has not been checkpointed.
:::
