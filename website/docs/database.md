# Database

OpenTeams hỗ trợ SQLite, MySQL và MariaDB thông qua HikariCP/JDBC. Database là authoritative state; cache không thay thế persistence.

## Chọn engine

| Engine | Phù hợp | JDBC concurrency | Ghi chú |
|---|---|---:|---|
| SQLite | Một server, setup đơn giản, dev/test | 1 | WAL, synchronous FULL, FK ON, busy timeout 5s |
| MySQL | Remote managed database | `pool-size` | URL bật SSL và TCP keepalive |
| MariaDB | Remote MariaDB | `pool-size` | URL bật TCP keepalive |

Nếu chỉ có một Paper/Folia instance, SQLite là default hợp lý. Remote engine hữu ích cho backup/operations tập trung, nhưng **không biến OpenTeams thành active-active multi-server shared state**.

## SQLite pragmas

Core cấu hình khi startup:

```sql
PRAGMA foreign_keys=ON;
PRAGMA journal_mode=WAL;
PRAGMA synchronous=FULL;
PRAGMA busy_timeout=5000;
```

Hikari maximum/minimum pool size đều là 1 cho SQLite. TeamService JDBC semaphore cũng giới hạn 1, giúp authoritative query và mutation không chạy song song trên nhiều connection SQLite.

## Namespace

Mọi primary key quan trọng bắt đầu bằng `namespace`. Namespace đồng thời là lease boundary.

```yaml
database:
  namespace: survival-main
```

Các dataset độc lập có thể nằm trong cùng remote database bằng namespace khác. Hai instance active dùng **cùng namespace** sẽ không cùng giữ write lease.

## Schema overview

| Table | Vai trò |
|---|---|
| `teams` | Aggregate root, owner, state, visibility, limit, version |
| `team_name_claims` | Unique normalized name claim |
| `team_tag_claims` | Unique normalized tag claim |
| `role_templates` | Role metadata và priority |
| `role_permissions` | Permission set theo role |
| `team_members` | Global membership uniqueness theo player/namespace |
| `team_invitations` | Pending invitations và expiry |
| `team_join_requests` | Public join requests và expiry |
| `team_bans` | Team-local bans, reason và optional expiry |
| `team_settings` | Encoded typed setting values và version |
| `player_preferences` | Team chat, staff spy, locale override field |
| `player_directory` | Last-known name để resolve offline player |
| `audit_entries` | Mutation audit và correlation ID |
| `core_lease_fences` | Monotonic next fencing token |
| `core_leases` | Current instance lease/heartbeat/expiry |

## Aggregate consistency

`teams.version` là optimistic concurrency token. Mutation đọc expected version và chỉ update khi version vẫn khớp. Conflict dự kiến được retry tối đa ba lần trong service layer.

Một successful mutation thực hiện:

1. validate runtime/lease và domain state;
2. lock/validate lease fence trong transaction;
3. update domain rows;
4. increment aggregate version;
5. append audit row cùng correlation ID;
6. commit;
7. publish immutable cache snapshot;
8. emit post-commit event.

Rollback không được thay cache hoặc emit success.

## Consistent reads

Một `TeamSnapshot` gồm team row, members, role permissions và settings. Authoritative aggregate load dùng explicit read transaction để các SELECT không quan sát nhiều commit khác nhau giữa chừng.

Public-team search và directory/inbox queries là authoritative async reads. Cached methods không mở JDBC transaction.

## Lease và fencing

Lease lifetime cố định **45 giây**. Heartbeat scheduler chạy mỗi **300 ticks** (xấp xỉ 15 giây ở 20 TPS) trên virtual-thread executor.

Mỗi acquisition nhận fence token tăng đơn điệu. Mọi write transaction thực hiện conditional update trên:

```text
namespace + instance_id + fence_token + expires_at
```

Transaction giữ lock trên lease row. Một stale instance không thể commit sau khi instance khác takeover với token mới.

## Runtime state machine

<div class="ot-flow">
  <div><b>STARTING</b><small>config/schema/lease</small></div>
  <div><b>WRITABLE</b><small>reads + mutations</small></div>
  <div><b>DEGRADED</b><small>cached reads</small></div>
  <div><b>RECOVERING</b><small>cache rebuild</small></div>
  <div><b>WRITABLE</b><small>lease confirmed</small></div>
</div>

Khi heartbeat không update/acquire được lease, runtime degrade. Nếu heartbeat sau đó khỏe, Core bắt đầu recovery: resolve membership cho online players theo batch, deduplicate team IDs, load fresh snapshots và publish replacement cache atomically. Writes chỉ mở lại nếu recovery thành công và lease vẫn held.

## Backup

### SQLite

An toàn nhất:

1. stop server;
2. đợi shutdown hoàn tất;
3. copy `openteams.db` và mọi sidecar còn tồn tại;
4. checksum backup;
5. start lại và chạy doctor.

### MySQL/MariaDB

Dùng snapshot/backup transaction-consistent của database provider. Backup phải gồm toàn bộ OpenTeams tables và Flyway history. Ghi lại namespace và commit/JAR version tương ứng.

## Schema migration policy

Trong indev, V1 là consolidated baseline và có thể được sửa trực tiếp. Database alpha cũ phải được tạo lại. Sau schema freeze đầu tiên, thay đổi mới mới được phát hành thành ordered version migrations.
