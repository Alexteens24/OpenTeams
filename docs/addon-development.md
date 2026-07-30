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
