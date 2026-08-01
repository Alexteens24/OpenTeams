# Troubleshooting

Thu thập trước khi báo lỗi:

- Paper/Folia build và Java version;
- OpenTeams commit/JAR checksum;
- database engine/version và namespace;
- log đầy đủ từ `Enabling OpenTeams`;
- `/teamadmin doctor` output nếu chạy được;
- correlation ID của mutation lỗi.

## `no such table: core_lease_fences`

### Nguyên nhân thường gặp

- JAR không chứa migration resource;
- Flyway location/classloader không scan được resource;
- database alpha cũ có incomplete schema/history;
- build/deploy nhầm artifact không phải shaded Core JAR.

### Xử lý trong indev

1. Stop server.
2. Backup database để điều tra nếu cần.
3. Kiểm tra JAR:

```bash
jar tf openteams-core-*.jar | grep V1__core_schema.sql
```

4. Với disposable alpha database, xóa database và sidecars.
5. Start lại và xác nhận Flyway validate/migrate V1.

Không tạo riêng `core_lease_fences` bằng tay: những Core tables khác có thể cũng thiếu hoặc sai constraint.

## Flyway báo “No migrations found”

Đảm bảo deployable artifact là output `shadowJar` của module `openteams-core`. Migration phải ở:

```text
db/migration/common/V1__core_schema.sql
```

Core bind thread context classloader khi chạy Flyway. Nếu log vẫn không tìm thấy migration, cung cấp output `jar tf`, không chỉ stack trace.

## Plugin vào `READ_ONLY`

Xem [Read-only incident runbook](./operations#read-only-incident). Kiểm tra:

- database reachable trong connection timeout;
- credentials/TLS không đổi;
- connection pool/database không cạn;
- cùng namespace có instance khác;
- clock/host pause đủ lâu để lease 45 giây hết hạn.

Không xóa `core_leases` trong lúc có process active.

## Namespace already leased

Một instance khác đang có unexpired lease. Nếu instance đó thực sự active, đây là protection đúng.

Nếu process cũ đã chết:

- đợi lease hết hạn;
- đảm bảo process cũ không thể resume;
- start instance mới và quan sát fence token takeover.

Không chạy hai backend active trên cùng namespace nếu muốn cả hai cùng mutate.

## Không tạo được team sau disband

Current flow phải remove/transition membership trong cùng transaction và publish cache mới. Kiểm tra:

1. disband đã trả success;
2. `/team info` báo không còn team;
3. runtime đang WRITABLE;
4. doctor không có dangling membership;
5. log có mutation conflict/database error.

Nếu DB không còn membership nhưng cache vẫn báo có team, đó là cache consistency bug: ghi lại exact interleaving và correlation ID.

## Owner không leave được

Đây là invariant có chủ đích. Active team không được mất owner. Dùng:

```text
/team transfer <player>
```

hoặc:

```text
/team disband confirm
```

## Offline invite không tìm thấy player

Offline lookup dựa trên last-known player directory. Player cần từng join server để OpenTeams gọi `rememberPlayer`, và tên phải khớp normalized directory entry.

Kiểm tra:

- chính tả/tên hiện tại;
- player đã từng join sau khi plugin được cài;
- database `player_directory` có row trong đúng namespace;
- query không fail do read-only/database outage.

## Dialog không mở

Đặt:

```yaml
ui:
  mode: auto
```

Core sẽ fallback chat nếu dynamic Dialog creation thất bại. Nếu `chat` mode hoạt động nhưng Dialog không:

- ghi lại Paper build;
- kiểm tra experimental Dialog API compatibility;
- tìm warning/exception từ `DialogTeamUserInterface`;
- không cài thêm plugin hot-reload để thử sửa.

## Không đọc được error trong chat

Current UI đóng Dialog trước khi gửi mutation error. Nếu vẫn bị che, cung cấp action cụ thể và Paper client/server version; đây có thể là khác biệt Dialog lifecycle ở build mới.

## Team chat lộ ra global ngay sau join

Current implementation serialize preference load/toggle và không route cho tới khi state sẵn sàng. Nếu tái hiện:

- ghi timestamp login và message;
- database latency;
- preference row;
- có reload/hot-disable trước đó không;
- log callback exception.

## Unsafe warning trên Java mới

`WARNING: A terminally deprecated method in sun.misc.Unsafe has been called` thường đến từ dependency/runtime library trên Java mới, không nhất thiết là OpenTeams init failure. Phân biệt warning với stack trace có `OpenTeams failed to initialize safely`. Nâng dependency khi upstream hỗ trợ; plugin compile bytecode Java 21.

## PlugMan có được hỗ trợ không?

Không. Full server restart là supported lifecycle. Xem danh sách resources tại [Production runbook](./operations#update-jar).

## Báo bug chất lượng

Một report tốt gồm:

```text
Expected:
Actual:
Exact commands/click sequence:
Paper/Folia + Java:
OpenTeams SHA:
Database engine:
Fresh DB or reused alpha DB:
Relevant log from startup:
Doctor output:
Correlation ID:
```
