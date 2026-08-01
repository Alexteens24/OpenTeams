# Addon development

Declare `depend: [OpenTeams]`, compile against `openteams-api`, then obtain the
service from Bukkit:

```java
OpenTeams api = Bukkit.getServicesManager().load(OpenTeams.class);
```

Every extension is registered with the addon's `Plugin` instance and returns a
`Registration`. Close registrations from `onDisable`; Core also removes all
owned registrations automatically when Bukkit disables the addon. Addons may
register:

- `/team` subcommands;
- cached placeholder resolvers;
- typed team settings and internal permissions;
- dashboard actions;
- locale translation maps.
- pre-commit mutation policies with a bounded timeout.

Policies may explicitly deny a mutation. Exceptions and timeouts fail open so a
broken addon cannot stop Core. Listen to `TeamMutationCommittedEvent` for
post-commit work; it runs asynchronously after the cache is updated. Use its
correlation ID to connect addon logs with Core audit records.

Command names and aliases cannot overlap Core commands or another addon.
Placeholder/visibility resolvers must be non-blocking and must not access a
database or HTTP service. Mutation handlers return `CompletionStage` and must
move back to a Paper/Folia scheduler before changing players, entities or
inventories.

The example plugin in `openteams-example-addon` is the compatibility fixture for
the supported registration lifecycle.

## UI contributions

`TeamUiRegistry.UiAction` targets a typed `DASHBOARD`, `MEMBERS` or `SETTINGS`
area. Supply translation keys for both its label and description, a team
permission, an availability predicate and an asynchronous handler. Core checks
the permission, resolves addon translations and rejects stale UI contexts before
calling the handler. Return `REFRESH` when the dashboard should be rebuilt after
the action, or `CLOSE` when the addon owns the next interaction.

UI handlers receive an immutable context containing the viewer, team ID and team
version. Load fresh data through `TeamService` before doing long-lived work; do
not retain the context as a live team object. Register matching localized strings
through `TranslationRegistry` as demonstrated by the example addon.

Player-facing query APIs expose paginated public-team discovery, pending
invitations and requests, bans, roles, and the last known name directory. These
queries are asynchronous and may touch JDBC; only the explicitly documented
cached methods are safe for hot paths.
