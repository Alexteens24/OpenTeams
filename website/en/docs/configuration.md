# Configuration

File: `plugins/OpenTeams/config.yml`. OpenTeams calls `saveDefaultConfig()` at startup; changes require a full server restart.

## Default config

```yaml
database:
  type: sqlite
  namespace: default
  sqlite-file: openteams.db
  host: localhost
  port: 3306
  database: openteams
  username: openteams
  password: change-me
  pool-size: 8
  connection-timeout-ms: 3000
team:
  default-member-limit: 20
  invitation-expiry-seconds: 604800
ui:
  mode: auto
  default-locale: vi_VN
  follow-player-locale: false
friendly-fire:
  mode: deny
audit:
  retention-days: 90
chat:
  format: "<aqua>[<tag>]</aqua> <white><player>:</white> <gray><message></gray>"
addons:
  policy-global-timeout-ms: 500
```

<ConfigGroup title="database" description="Storage engine, namespace, and JDBC pool behavior.">
  <ConfigProperty name="database.type" type="enum" default-value="sqlite" required>`sqlite`, `mysql`, or `mariadb`. Invalid values fail startup safely.</ConfigProperty>
  <ConfigProperty name="database.namespace" type="string" default-value="default" required>Logical data and lease boundary. Only one live writer can hold a namespace lease.</ConfigProperty>
  <ConfigProperty name="database.sqlite-file" type="path" default-value="openteams.db">Path relative to `plugins/OpenTeams/`; SQLite only.</ConfigProperty>
  <ConfigProperty name="database.host" type="string" default-value="localhost">MySQL/MariaDB host.</ConfigProperty>
  <ConfigProperty name="database.port" type="integer" default-value="3306">Remote TCP port.</ConfigProperty>
  <ConfigProperty name="database.database" type="string" default-value="openteams">Remote schema/database name.</ConfigProperty>
  <ConfigProperty name="database.username" type="string" default-value="openteams">Remote login; ignored by SQLite.</ConfigProperty>
  <ConfigProperty name="database.password" type="string" default-value="change-me">Remote password. Protect this file with filesystem permissions.</ConfigProperty>
  <ConfigProperty name="database.pool-size" type="integer" default-value="8">Minimum 1. Remote JDBC concurrency follows this size; SQLite remains 1.</ConfigProperty>
  <ConfigProperty name="database.connection-timeout-ms" type="milliseconds" default-value="3000">Minimum 250 ms; Hikari acquisition/failure boundary.</ConfigProperty>
</ConfigGroup>

Generated URLs are `jdbc:sqlite:<absolute-path>`, `jdbc:mysql://<host>:<port>/<database>?useSSL=true&tcpKeepAlive=true`, and `jdbc:mariadb://<host>:<port>/<database>?tcpKeepAlive=true`.

<ConfigGroup title="team" description="Defaults for new aggregates.">
  <ConfigProperty name="team.default-member-limit" type="integer" default-value="20">New-team capacity; existing teams keep their persisted value.</ConfigProperty>
  <ConfigProperty name="team.invitation-expiry-seconds" type="seconds" default-value="604800">Seven days; also used for join-request expiry.</ConfigProperty>
</ConfigGroup>

<ConfigGroup title="ui" description="Paper Dialog, fallback, and locale selection.">
  <ConfigProperty name="ui.mode" type="enum" default-value="auto">`auto`/`dialog` use Dialog with chat fallback; `chat` always uses clickable chat. Unknown values disable startup.</ConfigProperty>
  <ConfigProperty name="ui.default-locale" type="locale" default-value="vi_VN">Underscores become hyphens before `Locale.forLanguageTag`; this is the fallback bundle.</ConfigProperty>
  <ConfigProperty name="ui.follow-player-locale" type="boolean" default-value="false">Prefer client locale, then configured default, then key text.</ConfigProperty>
</ConfigGroup>

<ConfigGroup title="gameplay" description="Friendly fire and chat.">
  <ConfigProperty name="friendly-fire.mode" type="enum" default-value="deny">`allow` makes the server/default typed setting true; every other value denies.</ConfigProperty>
  <ConfigProperty name="chat.format" type="MiniMessage" default-value="[tag] player: message">Supports `&lt;tag&gt;`, `&lt;player&gt;`, and `&lt;message&gt;`. User messages remain Adventure components, not format markup.</ConfigProperty>
</ConfigGroup>

<ConfigGroup title="maintenance & addons">
  <ConfigProperty name="audit.retention-days" type="days" default-value="90">Cleanup cutoff; cleanup runs only after administrator confirmation.</ConfigProperty>
  <ConfigProperty name="addons.policy-global-timeout-ms" type="milliseconds" default-value="500">Deadline for the whole policy chain; each policy is also capped at two seconds.</ConfigProperty>
</ConfigGroup>

::: danger A namespace is not an arbitrary server ID
Two active servers sharing a database and namespace compete for one lease; only one writes. Use separate namespaces for independent datasets. Active-active shared teams are not currently supported.
:::

Numeric values may be clamped without rewriting the file. Enum/database initialization errors are logged and disable the plugin safely. Restart fully and run `/teamadmin doctor` after changes.
