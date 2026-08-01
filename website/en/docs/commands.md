# Commands

OpenTeams registers a Brigadier tree through Paper's Lifecycle API. `/teams` aliases `/team`. All `/team` commands require `openteams.command.team`, granted to everyone by default.

## General

| Command | Purpose | Requirement |
|---|---|---|
| `/team` | Open Dialog/chat command center | Player |
| `/team create <name>` | Create a team | Not in a team |
| `/team info` | Load the current team | Team member |
| `/team explore [query]` | Search up to 10 public teams | Player |
| `/team invitations` | List pending invitations | Player |

## Invitations and requests

| Command | Purpose | Team permission/state |
|---|---|---|
| `/team invite <player>` | Invite a known online/offline player | `team.invite` |
| `/team accept <team-id>` | Accept an invitation | Target, no current team |
| `/team decline <team-id>` | Decline an invitation | Target |
| `/team request <team-id>` | Request a public team | No current team |
| `/team approve <player>` | Approve a request | `team.join-request.accept` |

The public service API also supports reject, cancel, and revoke flows that are not all exposed as separate subcommands.

## Membership and moderation

| Command | Purpose | Requirement |
|---|---|---|
| `/team leave` | Leave | Non-owner member |
| `/team kick <player>` | Remove a member | `team.kick`, higher priority |
| `/team transfer <player>` | Transfer ownership | Owner; target is a member |
| `/team ban <player> [reason]` | Ban and remove target | `team.ban`, higher priority |
| `/team unban <player>` | Remove a ban | `team.ban` |
| `/team role <player> <role>` | Change role | `team.role.change`; cannot assign Owner |

Ownership transfer demotes the previous owner to `co_owner` in the same transaction.

## Settings and chat

| Command | Permission/purpose |
|---|---|
| `/team rename <name>` | `team.rename` |
| `/team tag <tag>` | `team.settings.manage` |
| `/team visibility <public\|private>` | `team.settings.manage` |
| `/team setting <key> <value>` | Permission declared by the setting |
| `/team disband confirm` | Owner only |
| `/team chat` | Toggle persistent team-chat mode |
| `/team chat <message>` | Send once without changing mode |

The current Core setting is `friendly-fire`, accepting `true` or `false`. Team chat requires membership; staff with spy enabled receive a copy.

## Administrator commands

| Command | Purpose | Bukkit permission |
|---|---|---|
| `/teamadmin` | Database mode, UI adapter, addon command count | `openteams.admin` |
| `/teamadmin doctor` | Async integrity report | `openteams.admin` |
| `/teamadmin cleanup confirm` | Remove expired temporary data and old audits | `openteams.admin` |
| `/teamadmin spy` | Toggle team-chat spy | `openteams.admin.spy` |

Doctor reports missing owners, wrong owner roles, dangling members, expired invitation/request/ban counts, and audit rows. Only the first three determine `healthy()`.

Addon `CommandContribution`s appear as `/team <extension>`. Core checks their Bukkit permission before the async handler. Names and aliases cannot collide. `/teamadmin` supports console; most team flows require a player.
