# Features

This page describes the currently implemented scope. Items that have not passed their release gate are listed in [Release status](./release-status).

## Team lifecycle

- Create teams with NFKC-normalized Unicode names, unique case-insensitively.
- Names are 3–24 characters and may contain letters, numbers, underscores, and spaces.
- Optional alphanumeric tags are 1–8 characters.
- Invite online or offline players through the last-known player directory.
- Invitations expire and appear in the recipient's inbox.
- Paginated discovery searches public teams by name or tag.
- Two-sided join requests: players track sent requests; teams manage pending requests.
- Leave, kick, ownership transfer, and disband preserve owner invariants.
- Ban and unban support reasons and optional expiry.

## Dialog-first player experience

`/team` opens a command center appropriate for the player's current state. Players without a team see create, Explore, and invitations; members see overview, roster, chat, and leave; managers see invitation, request, and moderation tools; owners see transfer, settings, and disband. Addons may contribute Dashboard, Members, or Settings actions.

Paper Dialog is isolated in `openteams-dialog-ui`. If Dialog creation fails, OpenTeams falls back to Adventure chat components and commands. If a Dialog mutation fails, the Dialog closes before the chat error is sent.

## Roles and authorization

| Role | Priority | Default scope |
|---|---:|---|
| Owner | 1000 | Wildcard `*` |
| Co-owner | 750 | Invite, kick, ban, requests, roles, rename, settings |
| Moderator | 500 | Invite, kick, ban, approve requests |
| Member | 100 | Basic gameplay |

Authorization checks permission keys; actions targeting another player also require a higher role priority. See [Roles & permissions](./permissions).

## Team chat and friendly fire

- Both gameplay paths read only immutable cache state.
- `/team chat` toggles a persistent preference.
- Preference loading and toggling are serialized so stale async results cannot overwrite newer input.
- Staff spy has a separate `/teamadmin spy` toggle.
- Chat format uses MiniMessage placeholders `<tag>`, `<player>`, and `<message>`.
- The typed `friendly-fire` setting can override the server default per team.

## Correctness and recovery

- The database is authoritative; domain changes and audit rows commit together.
- `teams.version` is an optimistic aggregate token.
- Primary key `(namespace, player_id)` enforces global membership uniqueness.
- Cache publishes only after commit and rejects stale versions.
- Membership cache generations prevent old query results from overwriting newer mutations.
- One live instance holds the lease for a namespace; every write verifies a monotonic fence token.
- Database or lease loss moves the runtime to `DEGRADED_READ_ONLY`.
- Recovery rebuilds online-player cache before writes reopen.

## Addon platform and administration

Addons can register `/team` subcommands, cached Adventure placeholders, typed settings, team permissions, UI actions, locale maps, and bounded pre-commit policies. Every registration belongs to a Bukkit `Plugin`; Core removes all contributions on addon disable.

Administrators get database/UI status, `/teamadmin doctor`, confirmed retention cleanup, fail-safe startup, and graceful shutdown of workers, chat state, leases, and the Hikari pool.
