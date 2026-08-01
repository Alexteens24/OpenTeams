# Player guide

This guide explains workflows rather than only command syntax. See [Commands](./commands) for the complete reference.

## Open the command center

Run `/team`. OpenTeams selects a screen from your current membership and permissions. With `ui.mode: auto`, Paper Dialog is preferred and clickable Adventure chat is the fallback.

## Create a team

```text
/team create Green Valley
```

Names are stripped and NFKC-normalized, 3–24 characters, support Unicode letters, numbers, `_`, and spaces, and are unique by normalized lowercase value. Tags are empty or 1–8 alphanumeric characters. The creator becomes Owner and a new-version snapshot is published after commit.

## Invite players

```text
/team invite PlayerName
```

Moderators and above have the permission by default. Offline targets work if their name exists in the last-known player directory. An invitation remains in the database until accepted, declined, revoked, or expired.

```text
/team invitations
/team accept <team-id>
/team decline <team-id>
```

Accept fails if the player already belongs to a team, is banned, the invitation expired, or the team is full.

## Find and request a public team

```text
/team explore [query]
/team request <team-id>
/team approve <player>
```

Only `PUBLIC` teams appear. A request never adds a member automatically; a manager must approve it through the command or command center.

## Team chat

`/team chat Hello team!` sends once without changing mode. `/team chat` toggles persistent mode, causing normal chat to route to the team. The preference survives reconnects and OpenTeams waits for its state to load so a join-time race cannot leak a team message globally.

## Manage members and settings

```text
/team kick <player>
/team ban <player> [reason]
/team unban <player>
/team role <player> <role>
/team rename <name>
/team tag <tag>
/team visibility <public|private>
/team setting friendly-fire <true|false>
```

The actor needs the corresponding permission and, for targeted actions, a higher role priority. Owner is protected from normal kick, ban, and role-change flows. Co-owners have rename and settings permissions by default. Addons may define additional typed settings with their own permission.

## Leave, transfer, and disband

Members run `/team leave`. An owner cannot leave an active team ownerless; run `/team transfer <player>` or `/team disband confirm`. Transfer demotes the old owner to `co_owner`. Disband deactivates the aggregate and removes membership transactionally, allowing the former owner to create a new team after commit.

## When an action fails

Localized errors include `FORBIDDEN`, `CONFLICT`, `LIMIT_REACHED`, `READ_ONLY`, and `DATABASE_UNAVAILABLE`. Dialog closes before a mutation error is sent to chat. During read-only mode cached information, chat, and friendly fire may continue, while mutations are rejected; ask an administrator to run `/teamadmin doctor`.
