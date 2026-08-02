# OpenTeams-Homes 0.1.0

Official team teleport addon for OpenTeams. It provides one shared Team Home and
multiple named Team Warps without importing Core implementation or database code.

## Requirements

- Java 21
- Paper 1.21.11 or Folia 1.21.11
- OpenTeams API `>=0.1.0,<0.2.0`

Build with:

```bash
./gradlew :addons:openteams-homes:shadowJar
```

The standalone artifact is written to
`addons/openteams-homes/build/libs/openteams-homes-0.1.0.jar`.

## Commands

- `/team home [info|set|delete]`
- `/team warp list [query] [page]`
- `/team warp teleport <name>`
- `/team warp info <name>`
- `/team warp create <name>`
- `/team warp update <name> [confirm]`
- `/team warp rename <old> <new>`
- `/team warp delete <name> [confirm]`

The OpenTeams dashboard also receives Team Home and Team Warps Dialog actions.
Chat output remains fully usable when Dialogs are unavailable.

## Storage

`storage.type` supports `sqlite`, `mysql`, and `mariadb`. Homes owns its datasource,
Flyway history and `oth_*` tables. It never accesses the OpenTeams Core datasource.
SQLite defaults to `plugins/OpenTeams-Homes/homes.db`.

Warp names are case-insensitive ASCII names of 1–24 letters, numbers, `_` or `-`.
The default limit is 20 Warps per team. Home does not count toward this limit.

## Teleport behavior

Home and Warp share the same teleport pipeline. Membership, team permission,
destination ID/version and server ID are checked again after warmup. Warmups are
cancelled by movement, damage, death, quit, external teleport, destination changes,
membership changes or plugin shutdown. Cooldown begins only after a successful
async teleport.

World/block checks run on the destination region scheduler and player operations run
on the entity scheduler, allowing the same JAR to support Paper and Folia.
