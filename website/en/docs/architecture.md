# Architecture

OpenTeams is a Gradle multi-module project built around an immutable public API, Core persistence/service boundaries, and an isolated UI adapter.

## Modules

| Module | Responsibility |
|---|---|
| `openteams-api` | Public records, services, requests/results, events, registries |
| `openteams-core` | Lifecycle, JDBC, cache, commands, chat, listeners, runtime state |
| `openteams-dialog-ui` | Paper Dialog, chat fallback, message bundles |
| `openteams-test-kit` | Snapshot fixtures for tests/addon authors |
| `openteams-example-addon` | Public lifecycle compatibility example |

Experimental Dialog types stay in the UI module; addons compile against API, never Core.

## Data flow

A cached read goes directly from event/addon through a cached TeamService method to an immutable snapshot—no JDBC. An authoritative read runs on a service worker, enters the engine-bounded JDBC semaphore, and uses a read transaction.

A mutation flows through correlation ID → ordered addon policies → runtime/lease gate → JDBC transaction (domain checks, fence lock, writes/version, audit) → commit → cache publication → async committed event → result. A policy deny precedes the transaction; timeout/exception fails open.

The mandatory ordering is `database commit < cache put < committed event < success observation`. Failed transactions never alter cache. Observer failure cannot undo a committed transaction.

## Immutable cache and consistency

TeamCache indexes IDs and player memberships while tracking load states/generations under a read/write lock. Older snapshot versions cannot overwrite newer ones; membership indexes change atomically; stale authoritative load results cannot mark a player absent/failed after a mutation; recovery can atomically replace all relevant state.

`teams.version` orders aggregates. Member and setting rows carry versioning, service conflicts retry at most three times, and claim tables enforce name/tag uniqueness under concurrent creates or renames. Primary key `team_members(namespace, player_id)` guarantees at most one team per player in a namespace.

## Lifecycle, fencing, and threading

```text
STARTING → WRITABLE ⇄ DEGRADED_READ_ONLY → RECOVERING → WRITABLE → STOPPING
```

Startup configures storage/schema/lease/roles/registries and initial cache. Read-only blocks mutation while preserving cache paths. Recovery rebuilds online state before reopening writes. Stop rejects new work and closes resources.

Fence counters increase monotonically. Every write locks and conditionally verifies the current lease row, so a paused process with an old token cannot commit after takeover.

Authoritative work and heartbeat JDBC run on named virtual threads with semaphore bounds. Cached methods are thread-safe. Bukkit entity/inventory changes must use their owning scheduler; committed events are async. The global-region scheduler sets heartbeat cadence while a dedicated executor performs JDBC.

## Failure philosophy

Startup exceptions disable safely; storage/lease outage degrades read-only; policy exceptions warn and fail open; addon command/UI exceptions give safe user errors; optimistic conflict retries within bounds; invalid input returns structured `TeamErrorCode` instead of raw SQL exceptions.
