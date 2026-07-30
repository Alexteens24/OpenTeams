# OpenTeams Core architecture

## Correctness boundary

The database is authoritative. A mutation validates state, writes domain rows
and appends its audit entry in one transaction. The immutable cache is replaced
only after commit. A failed transaction must not change cache state or emit a
successful result.

`teams.version` is the aggregate concurrency token. Membership uniqueness is
also enforced by the `(namespace, player_id)` primary key. Expected optimistic
conflicts are retried at most three times; they are never overwritten.

## Runtime states

- `STARTING`: configuration, migration, role seeding and lease acquisition.
- `WRITABLE`: reads and mutations are available.
- `DEGRADED_READ_ONLY`: cached reads, chat and friendly-fire remain available; mutations
  return `READ_ONLY`.
- `RECOVERING`: the database lease is back, but writes stay disabled until the
  online-player cache has been rebuilt successfully.
- `STOPPING`: no new mutation is accepted and resources are closed.

Only one live instance may own a database namespace. Every takeover receives a
monotonically increasing fencing token. Each write validates and locks the
matching `instance_id + fence_token` lease row inside the same JDBC transaction
as the domain mutation, so a paused/stale instance cannot commit after another
instance has completed takeover. The lease expires after an unclean shutdown
and is renewed by a lifecycle-owned virtual-thread executor.

## Threading contract

- Cached API queries are thread-safe and never touch JDBC.
- Authoritative queries and mutations complete on named Java 21 virtual threads.
- JDBC concurrency is bounded by the configured pool/semaphore; SQLite uses one
  writer.
- Completion callbacks are not entity-safe. Addons must schedule Bukkit entity
  or inventory work through the owning entity scheduler.
- Chat and damage listeners use immutable cache snapshots only.
- Public objects are records containing copied collections; no snapshot is live.
- Team and membership cache indexes are published under one read/write lock;
  stale versions are rejected inside the same write critical section.

## Recovery

Recovery resolves all online player memberships in bounded SQL `IN` batches,
deduplicates team IDs and loads team/member/setting snapshots per batch. The
runtime stays `RECOVERING` until the replacement cache state is published.

## Mutation pipeline

Every write receives one correlation UUID before policy evaluation. Addon
policies execute in priority order with both a per-policy timeout and one global
deadline. Timed-out `CompletableFuture`s are cancelled. A timeout or addon
exception is logged and fails open, while an explicit denial stops the write.
The same correlation UUID is returned in `OperationResult`, persisted in the
transactional audit row and included in `TeamMutationCommittedEvent`.

The committed event is asynchronous and is emitted only after database commit
and cache publication. It is observational and cannot roll back a mutation.

## Extension ownership

Registries accept the actual Bukkit `Plugin` instance and derive the namespace
from its descriptor. An addon cannot claim a different namespace. Core removes
all commands, policies, settings, permissions, placeholders, translations and
UI actions automatically on `PluginDisableEvent`.

## Experimental Paper APIs

Paper Dialog types exist only in `openteams-dialog-ui`. The public API exposes
platform-neutral UI actions. If dynamic dialog creation fails, Core falls back
to Adventure chat components and commands.
