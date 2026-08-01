# Database

OpenTeams supports SQLite, MySQL, and MariaDB through HikariCP/JDBC. Persistence is authoritative; cache does not replace it.

## Choose an engine

| Engine | Best fit | JDBC concurrency | Notes |
|---|---|---:|---|
| SQLite | One server, development/test | 1 | WAL, FULL sync, foreign keys, 5s busy timeout |
| MySQL | Managed remote database | `pool-size` | SSL and TCP keepalive |
| MariaDB | Remote MariaDB | `pool-size` | TCP keepalive |

Remote storage centralizes operations/backups but does not create active-active shared state.

## SQLite and namespaces

Startup applies `foreign_keys=ON`, `journal_mode=WAL`, `synchronous=FULL`, and `busy_timeout=5000`. Hikari and the TeamService semaphore both limit SQLite to one concurrent connection/work item.

Every important primary key begins with `namespace`, which is also the lease boundary. Separate datasets can share a remote database under different namespaces. Two instances using one namespace cannot both hold its write lease.

## Schema overview

| Table | Responsibility |
|---|---|
| `teams` | Aggregate root, owner, state, visibility, limit, version |
| `team_name_claims`, `team_tag_claims` | Unique normalized claims |
| `role_templates`, `role_permissions` | Role metadata and permissions |
| `team_members` | Global player membership per namespace |
| `team_invitations`, `team_join_requests`, `team_bans` | Pending/temporary workflow data |
| `team_settings` | Encoded typed values and version |
| `player_preferences`, `player_directory` | Chat/spy/locale state and last-known names |
| `audit_entries` | Mutation audit and correlation ID |
| `core_lease_fences`, `core_leases` | Monotonic fences and live lease state |

## Consistency, leases, and recovery

`teams.version` is the optimistic concurrency token; expected conflicts retry at most three times. A successful mutation validates runtime/domain/lease, locks the fence, writes domain rows, increments version, appends its audit, commits, publishes immutable cache, and emits a post-commit event. Rollback changes neither cache nor events.

Aggregate loads use explicit read transactions so team, member, permission, and setting SELECTs observe one consistent view. Directory and inbox searches are authoritative async reads; cached methods never open JDBC.

Lease lifetime is **45 seconds**; heartbeat runs every **300 ticks** (about 15 seconds at 20 TPS). Each acquisition receives a monotonically increasing token. Writes conditionally verify namespace, instance ID, token, and expiry while locking the lease row, so a stale process cannot commit after takeover.

<div class="ot-flow">
  <div><b>STARTING</b><small>config/schema/lease</small></div>
  <div><b>WRITABLE</b><small>reads + writes</small></div>
  <div><b>DEGRADED</b><small>cached reads</small></div>
  <div><b>RECOVERING</b><small>cache rebuild</small></div>
  <div><b>WRITABLE</b><small>lease confirmed</small></div>
</div>

Recovery resolves online memberships in batches, deduplicates team IDs, loads fresh snapshots, and atomically replaces relevant cache state. Writes reopen only if recovery succeeds and the lease remains held.

## Backup and schema policy

For SQLite, stop cleanly, copy the main file and remaining sidecars, checksum the backup, restart, and run doctor. For remote engines, use a transaction-consistent provider snapshot including all OpenTeams tables and Flyway history; record the namespace and matching JAR commit.

During indev, V1 is a consolidated baseline and old alpha databases must be recreated. Ordered version migrations begin after the first schema freeze.
