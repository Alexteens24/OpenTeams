# Addon API

`openteams-api` is independent of the Paper implementation and Dialog internals. Runtime currently reports API version `0.1.0`; indev has no binary compatibility gate yet.

## Dependency and service discovery

```yaml
name: MyTeamsAddon
version: 0.1.0
main: com.example.myaddon.MyAddonPlugin
api-version: '1.21.11'
folia-supported: true
depend: [OpenTeams]
```

Use `compileOnly(project(":openteams-api"))` in the monorepo plus the Paper API. The API is not yet on a public Maven repository; separate repos can use a local/composite build or internal repository. Do not shade API classes supplied by Core.

```java
OpenTeams api = Bukkit.getServicesManager().load(OpenTeams.class);
if (api == null) throw new IllegalStateException("OpenTeams API unavailable");
```

Never cast `getPlugin("OpenTeams")` to an implementation type.

## Entry point

`teams()` exposes team queries/mutations and `players()` exposes the persistent player directory; `commands()`, `placeholders()`, `settings()`, `permissions()`, `userInterface()`, `translations()`, and `policies()` expose registries. `apiVersion()` returns the API string and `readOnly()` reports whether writes are unavailable.

## Cached and authoritative queries

Thread-safe cache-only methods include `findCached`, `membershipCached`, `relationCached`, and `hasPermissionCached`. Membership is `LOADING`, `PRESENT`, `ABSENT`, or `FAILED`; do not treat loading/failure as confirmed absence, and do not treat `TeamRelation.UNKNOWN` as `DIFFERENT`.

Authoritative `CompletionStage` methods include `find`, `loadMembership`, public search, invitation/request/ban queries, and `roles`. `PlayerDirectory` provides `resolve`, exact-name lookup, prefix search, and `remember`. They may access JDBC and return immutable records such as `PlayerSummary`, `TeamSummary`, `Invitation`, `JoinRequest`, `Ban`, `Role`, and `Page<T>`.

`TeamSnapshot` contains identity, owner/state/visibility, limit/version/timestamps, copied settings, and copied members. Each `TeamMemberSnapshot` contains role, copied permissions, timestamps, and wildcard-aware `hasPermission`. Snapshots are not live objects.

## Mutations and errors

Every write accepts a `TeamRequests` record with `actorId` and returns `CompletionStage<OperationResult<TeamSnapshot>>`. The surface covers create/disband; invite accept/decline/revoke; leave/kick/transfer/role; rename/tag/visibility; request accept/reject/cancel; ban/unban; and typed settings.

Success includes the committed snapshot and correlation ID. Failure includes `TeamErrorCode`, translation key, translation arguments, and correlation ID. Branch on codes/keys, not human messages:

```text
NOT_FOUND · FORBIDDEN · INVALID_ARGUMENT · CONFLICT · LIMIT_REACHED
ALREADY_IN_TEAM · NOT_IN_TEAM · INVITATION_NOT_FOUND · INVITATION_EXPIRED
READ_ONLY · DATABASE_UNAVAILABLE · INTERNAL_ERROR
```

## Threading and events

::: danger Completion callbacks are not entity-safe
Authoritative work completes on an OpenTeams virtual worker thread. Schedule Player, Entity, or Inventory changes on the owning Paper/Folia scheduler.
:::

`TeamMutationCommittedEvent` is an asynchronous, observational Bukkit event emitted **after database commit and cache publication**. It is not cancellable and cannot roll back a mutation; `before()` may be empty for create.

## Lifecycle

Keep every `Registration`, call `close()` from addon `onDisable`, and clear your list. Core also listens for `PluginDisableEvent` and removes contributions by owner as a safety net. See [Extension points](./extensions).
