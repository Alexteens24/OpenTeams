# Production runbook

OpenTeams is not stable 1.0 yet. This runbook describes safe test, staging, and production-evaluation operations.

## Pre-deployment checklist

- [ ] Paper/Folia and Java meet the [requirements](./installation#requirements).
- [ ] The artifact is tied to a recorded commit SHA.
- [ ] The database backup has been tested.
- [ ] Remote credentials are not defaults.
- [ ] Namespace matches the intended dataset and no other instance is active on it.
- [ ] A full restart window is available; hot reload is not used.

## Deploy or update

For first deploy: stop, copy the shaded Core JAR, start while watching from `Enabling OpenTeams`, confirm schema and lease startup, wait for initial resync and `WRITABLE`, run `teamadmin doctor`, then smoke-test create/invite/chat.

For updates use `backup → stop → replace JAR → start → doctor → smoke test`. Never overwrite the JAR and invoke PlugMan. OpenTeams owns a Hikari pool, lease/fence, heartbeat executor, service workers/cache, chat preference store, Bukkit service, and addon registrations. Partial classloader lifecycle can leave old tasks and callbacks alive.

## Health and cleanup

`/teamadmin` reports `WRITABLE`/`READ_ONLY`, the active UI adapter, and addon command count. `/teamadmin doctor` asynchronously reports missing owners, wrong owner roles, dangling members, expired invitation/request/ban counts, and audit rows. Only the first three are integrity failures.

`/teamadmin cleanup confirm` requires a valid write lease and transactionally removes expired invitations, requests, temporary bans, and audits older than `audit.retention-days`. On large databases, measure query and lock time before external automation.

## Read-only incident

1. Do not restart repeatedly or edit rows manually.
2. Inspect `/teamadmin` and doctor if reads still work.
3. Check connectivity, credentials, TLS, database capacity, and competing instances on the namespace.
4. Restore database service and watch for `RECOVERING`.
5. Wait for `Database lease and cache recovered; mutations are enabled.`
6. Run doctor and a smoke mutation.

In `DEGRADED_READ_ONLY`, cached reads, team chat, and friendly fire may continue. Authoritative writes return `READ_ONLY`, and cache is not declared fresh until recovery completes.

## Shutdown, monitoring, and rollback

Disable transitions to `STOPPING`, unregisters Bukkit services, shuts down heartbeat (five-second wait then interrupt), closes chat and TeamService workers, then closes DatabaseManager/Hikari. Wait for completion before SQLite backup or starting a replacement process.

Collect lifecycle/recovery logs, periodic doctor output, audit growth, Hikari errors/latency, database saturation, failures by `TeamErrorCode`, and correlation IDs.

Indev rollback is safe only when schema/config did not change. Keep each backup paired with its artifact SHA. If V1 changed, restore the matching backup or recreate test data; Flyway history saying V1 does not make a newer schema compatible with an older JAR.
