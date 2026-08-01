# Kiến trúc

OpenTeams là Gradle multi-module project. Boundary chính là public immutable API, Core persistence/service và UI adapter tách biệt.

## Modules

| Module | Trách nhiệm |
|---|---|
| `openteams-api` | Public records, services, request/result types, events, extension registries |
| `openteams-core` | Plugin lifecycle, JDBC, cache, commands, chat, listeners, runtime state |
| `openteams-dialog-ui` | Paper Dialog implementation, chat fallback, localization bundles |
| `openteams-test-kit` | Snapshot fixtures cho tests/addon developers |
| `openteams-example-addon` | Public API lifecycle compatibility example |

Paper experimental Dialog types chỉ nằm trong UI module. Addon compile against API, không Core.

## Data flow

### Cached read

```text
Paper event / addon
        │
        ▼
TeamService cached method
        │
        ▼
immutable TeamCache snapshot
```

Không JDBC. Dùng cho chat, friendly fire và placeholder-like hot paths.

### Authoritative read

```text
caller → TeamService worker → JDBC semaphore → read transaction
                                      │
                                      └→ immutable read model
```

SQLite semaphore = 1; remote engine = configured pool size.

### Mutation

```text
request
  → correlation ID
  → ordered addon policies
  → runtime + lease gate
  → JDBC transaction
      → domain validation
      → lease fence lock
      → domain writes + version
      → audit append
  → commit
  → cache publication
  → async committed event
  → OperationResult
```

Explicit policy deny trả failure trước transaction. Policy timeout/exception fail open.

## Immutable cache

TeamCache giữ:

- team ID → `TeamSnapshot`;
- player ID → membership lookup/index;
- load state/generation cho player;
- indexes được publish dưới một read/write lock.

Publication rules:

- snapshot version cũ không overwrite version mới;
- membership indexes đổi atomically cùng team snapshot;
- authoritative load giữ generation token;
- stale load result không được mark absent/failed sau mutation mới;
- recovery có thể replace toàn bộ relevant state atomically.

## Transaction boundary

Database mutation là correctness boundary. Cache/event không tham gia rollback; vì vậy thứ tự bắt buộc là:

```text
database commit < cache put < committed event < success observation
```

Nếu transaction fail, cache không đổi. Nếu post-commit observer fail, database vẫn đã commit và event không rollback được.

## Consistency token

`teams.version` bảo vệ aggregate. Member/setting rows cũng mang version để snapshot phản ánh mutation order. Service retry optimistic conflict tối đa ba lần, không overwrite unconditional.

Name/tag uniqueness dùng claim tables thay vì chỉ application check, loại race giữa concurrent creators/renames.

## Membership uniqueness

Primary key `team_members(namespace, player_id)` đảm bảo một player tối đa một team trong namespace. Accept invite/request, create và leave/disband đều dựa vào constraint này cùng domain checks.

## Runtime lifecycle

```text
STARTING → WRITABLE ⇄ DEGRADED_READ_ONLY → RECOVERING → WRITABLE → STOPPING
```

- `STARTING`: config, pool, schema, lease, roles, registries/listeners, initial cache.
- `WRITABLE`: reads và mutations.
- `DEGRADED_READ_ONLY`: cached paths còn dùng, mutation bị chặn.
- `RECOVERING`: lease trở lại, rebuild online-player cache.
- `STOPPING`: từ chối work mới và close resources.

## Lease fencing

Lease chỉ bảo vệ một namespace. Fence counter tăng đơn điệu và không reset khi lease row thay owner. Write transaction conditional-update current lease row, tăng validation counter và giữ row lock.

Một paused process với token cũ không thể commit sau takeover vì token/instance mismatch.

## Threading

- TeamService authoritative work chạy trên named Java virtual threads.
- JDBC concurrency được semaphore-bound.
- Cached methods thread-safe.
- Bukkit entity/inventory mutation phải trở về owning scheduler.
- TeamMutationCommittedEvent là async Bukkit event.
- GlobalRegionScheduler chạy heartbeat cadence; actual JDBC heartbeat ở dedicated virtual-thread executor.
- Lifecycle executor có shutdown/await/interrupt boundary.

## UI boundary

`TeamUserInterface` có Dialog và Chat implementation. Dialog adapter nhận service, fallback, messages và supplier của addon UI actions. Dynamic UI luôn load/validate current state; risky actions dùng confirmation.

## Failure philosophy

- Startup exception: log severe và disable safely.
- Database/lease outage: degrade read-only.
- Addon policy exception: warn và fail open.
- Addon command/UI exception: user-safe error + owner warning.
- Optimistic conflict: bounded retry hoặc `CONFLICT`.
- Invalid input: structured `TeamErrorCode`, không raw SQL exception cho user.
