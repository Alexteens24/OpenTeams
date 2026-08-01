# Production runbook

OpenTeams chưa stable 1.0, nhưng runbook này mô tả cách deploy và vận hành an toàn cho test/staging hoặc production evaluation.

## Pre-deployment checklist

- [ ] Paper/Folia và Java đáp ứng [requirements](./installation#requirements).
- [ ] Artifact được build từ commit SHA đã ghi nhận.
- [ ] Database backup đã được kiểm tra.
- [ ] `config.yml` không chứa default remote password.
- [ ] Namespace đúng với intended dataset.
- [ ] Không có instance khác active trên cùng namespace.
- [ ] Có cửa sổ full restart; không dùng hot reload.

## Deploy lần đầu

1. Stop server.
2. Copy Core shaded JAR vào `plugins/`.
3. Start và theo dõi log từ `Enabling OpenTeams`.
4. Xác nhận migration, lease acquisition và enabled message.
5. Chờ initial cache resync chuyển runtime sang `WRITABLE`.
6. Chạy `teamadmin doctor`.
7. Test create/invite/chat trên account test.

## Update JAR

```text
backup → stop → replace JAR → start → doctor → smoke test
```

Không copy đè JAR trong lúc server chạy rồi gọi PlugMan. OpenTeams sở hữu:

- Hikari connection pool;
- database lease/fencing token;
- heartbeat virtual-thread executor;
- TeamService workers và immutable cache;
- chat preference store;
- Bukkit service registration;
- addon-owned extension registrations.

Partial disable/enable ngoài Paper lifecycle đầy đủ có thể để callback/task/classloader cũ tồn tại.

## Health check

### `/teamadmin`

Trả:

- database mode: `WRITABLE` hoặc `READ_ONLY`;
- active UI mode/adapter;
- số addon command contributions.

### `/teamadmin doctor`

Doctor query database trên virtual thread và trả:

```text
Doctor OK|FAILED
missing owner=<n>
wrong owner role=<n>
dangling members=<n>
expired invite/request/ban=<n>/<n>/<n>
audit rows=<n>
```

`healthy()` chỉ yêu cầu ba domain invariant đầu bằng 0. Expired row count là maintenance signal.

## Cleanup

```text
/teamadmin cleanup confirm
```

Cleanup yêu cầu valid write lease và chạy một transaction để xóa:

- invitations có `expires_at < now`;
- join requests đã hết hạn;
- temporary bans đã hết hạn;
- audit entries cũ hơn `audit.retention-days`.

Không schedule command quá thường xuyên nếu database lớn; theo dõi query/lock time trước khi tự động hóa bên ngoài.

## Read-only incident

Khi log báo lease lost hoặc database unavailable:

1. Không restart liên tục và không sửa rows thủ công.
2. Chạy `/teamadmin` và doctor nếu connection còn query được.
3. Kiểm tra database connectivity, credentials, TLS và capacity.
4. Kiểm tra có instance khác dùng cùng namespace.
5. Khôi phục database service.
6. Quan sát runtime đi `RECOVERING`.
7. Chờ log `Database lease and cache recovered; mutations are enabled.`
8. Chạy doctor và smoke mutation.

Trong `DEGRADED_READ_ONLY`:

- cached team reads vẫn có thể dùng;
- team chat/friendly fire tiếp tục bằng snapshot hiện tại;
- authoritative writes trả `READ_ONLY`;
- cache không được tự nhận là fresh cho tới recovery.

## Graceful shutdown

On disable, Core:

1. chuyển runtime `STOPPING`;
2. unregister Bukkit services;
3. shutdown heartbeat executor, chờ tối đa 5 giây rồi interrupt;
4. close team chat;
5. close TeamService workers;
6. close DatabaseManager/Hikari và release resources.

Đợi log shutdown hoàn tất trước khi backup SQLite hoặc start process mới.

## Monitoring nên thu thập

- startup/shutdown logs;
- heartbeat/read-only/recovery transitions;
- doctor output định kỳ;
- audit row growth và cleanup counts;
- Hikari acquisition timeout/error rate;
- database latency/connection saturation;
- plugin mutation failures theo `TeamErrorCode`;
- correlation ID cho incident-specific mutations.

## Rollback

Vì indev schema không có backward compatibility guarantee:

- rollback JAR chỉ an toàn nếu schema/config không đổi giữa hai commits;
- luôn giữ database backup gắn với artifact SHA;
- nếu V1 baseline đã đổi, restore matching backup hoặc tạo database test mới;
- không chạy old JAR trên schema mới chỉ vì Flyway history vẫn ghi V1.
