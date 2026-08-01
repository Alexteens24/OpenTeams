# Roles & permissions

OpenTeams separates **Bukkit permissions**, which gate commands/admin tools, from **team permissions**, which authorize domain actions inside a team.

## Bukkit permissions

| Permission | Default | Effect |
|---|---|---|
| `openteams.command.team` | `true` | Use `/team` and `/teams` |
| `openteams.admin` | `op` | Use `/teamadmin`, doctor, and cleanup |
| `openteams.admin.spy` | `op` | Use `/teamadmin spy` |

## Default role templates

| Key | Display | Priority | Member limit | Protected |
|---|---|---:|---:|---|
| `owner` | Owner | 1000 | 1 | Yes |
| `co_owner` | Co-owner | 750 | Unlimited | No |
| `moderator` | Moderator | 500 | Unlimited | No |
| `member` | Member | 100 | Unlimited | No |

Templates are seeded once per namespace when none exist.

## Team permission matrix

| Permission | Owner | Co-owner | Moderator | Member |
|---|:---:|:---:|:---:|:---:|
| `*` | ✓ | — | — | — |
| `team.invite` | ✓ | ✓ | ✓ | — |
| `team.kick` | ✓ | ✓ | ✓ | — |
| `team.ban` | ✓ | ✓ | ✓ | — |
| `team.join-request.accept` | ✓ | ✓ | ✓ | — |
| `team.role.change` | ✓ | ✓ | — | — |
| `team.rename` | ✓ | ✓ | — | — |
| `team.settings.manage` | ✓ | ✓ | — | — |

Owner wildcard satisfies every Core or addon team permission.

## Priority and ownership invariants

Kick, ban, and similar target actions require `actor priority > target priority`. Moderators therefore cannot manage peers or higher roles; co-owners can manage moderators/members but not peers/owners. Active teams must retain exactly one owner member. Owners cannot leave, be kicked/banned, or be changed through the normal role flow. `/team transfer` atomically promotes the target and demotes the former owner.

## Addon permissions

```java
api.permissions().register(plugin,
    new TeamPermissionRegistry.Permission(
        "example.use", "example.permission.use",
        Set.of("owner", "co_owner", "moderator")
    ));
```

`defaultRoles` merge into member snapshots. Typed settings and UI actions use the same permission engine. Disabling the addon removes the contribution and invalidates affected cached authorization.

The schema and API expose role metadata, but public CRUD tooling for custom templates and per-role overrides remains a release gate. Avoid direct table edits unless you own the invariants and upgrade path.
