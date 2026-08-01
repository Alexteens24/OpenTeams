# Extension points

Every registry accepts a Bukkit `Plugin owner`. Core lowercases the plugin name for its owner ID and namespaces every non-command key. For example, `OpenTeams-ExampleAddon` registering `example.enabled` owns `openteams-exampleaddon:example.enabled`. Foreign namespaces and duplicate canonical keys are rejected.

## Commands and placeholders

`CommandRegistry.CommandContribution` defines a name, aliases, Bukkit permission, description key, and async handler returning an integer. It appears under `/team`. Core rejects collisions with existing contributions and reserved commands (`create`, `info`, `invite`, `accept`, `leave`, `kick`, `transfer`, `rename`, `tag`, `disband`, `chat`, `request`, `approve`, `ban`, `unban`, `role`, `setting`, `help`) and rejects senders without permission.

`PlaceholderRegistry.Placeholder` receives viewer UUID and immutable snapshot, returning an Adventure component plus a fallback. It is a hot-path callback: never perform JDBC, HTTP, disk I/O, or expensive synchronous Bukkit lookups. The public PlaceholderAPI adapter is still a release gate.

## Typed settings and team permissions

A typed setting declares key, Java class, default, codec, validator, and permission. Writes resolve the canonical registration, decode and validate, authorize, persist the encoded value while incrementing aggregate version, then publish the new snapshot. Unknown or invalid settings return `INVALID_ARGUMENT` rather than persisting arbitrary strings.

`TeamPermissionRegistry.Permission` declares a key, description translation key, and default role set. Defaults merge into member snapshots; owner wildcard continues to match. Register permissions before settings or UI actions that reference them.

## UI actions and translations

`TeamUiRegistry.UiAction` declares a key, `DASHBOARD`/`MEMBERS`/`SETTINGS` area, priority, label and description keys, permission, synchronous availability, and async handler. Rendering/execution require both authorization and availability. `UiContext` identifies viewer, team, and team version; Core rejects stale contexts. Handlers return `REFRESH` or `CLOSE` and must reschedule Bukkit mutations to the owning entity thread.

Translations register a locale and key/value map. Lookup matches an exact tag or language code. Use a unique addon key prefix.

## Mutation policies

Policies have a key, ascending priority, per-policy timeout, and async decision. Timeout must be positive and at most two seconds; `addons.policy-global-timeout-ms` bounds the entire chain. Explicit deny stops the mutation. Timeout, exception, or exhausted global deadline logs a warning and **fails open**; timed-out futures receive best-effort cancellation. Do not use this mechanism as the only security boundary when denial must be absolute.

## Disable cleanup

Disabling the owner removes commands, actions, placeholders, settings, permissions, translations, and policies. Permission-derived cache is invalidated so `hasPermissionCached` cannot retain removed grants.
