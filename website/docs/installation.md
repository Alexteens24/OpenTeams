# Cài đặt

Hướng dẫn này đi từ server Paper/Folia đang stop tới OpenTeams runtime `WRITABLE` với SQLite mặc định.

## Requirements

| Thành phần | Giá trị |
|---|---|
| Server | Paper hoặc Folia `1.21.11+` |
| Java runtime | 21 trở lên |
| JAR | `openteams-core-0.1.0.jar` |
| Quyền ghi | Thư mục `plugins/OpenTeams/` |
| Port/database ngoài | Không cần khi dùng SQLite |

## 1. Lấy JAR

Build artifact theo [Tải và build](./download). Không cài riêng API hoặc Dialog UI JAR.

## 2. Copy vào server

Khi server đã stop:

```bash
cp openteams-core/build/libs/openteams-core-*.jar /path/to/server/plugins/
```

Cấu trúc sau lần start đầu tiên:

```text
server/
├─ plugins/
│  ├─ openteams-core-0.1.0.jar
│  └─ OpenTeams/
│     ├─ config.yml
│     └─ openteams.db
└─ paper.jar
```

## 3. Start server

```bash
java -jar paper.jar --nogui
```

Startup an toàn đi qua:

<div class="ot-flow">
  <div><b>CONFIG</b><small>đọc config.yml</small></div>
  <div><b>SCHEMA</b><small>validate baseline</small></div>
  <div><b>LEASE</b><small>acquire fence</small></div>
  <div><b>CACHE</b><small>resync online</small></div>
  <div><b>WRITABLE</b><small>mở mutations</small></div>
</div>

Log cuối phải chứa thông báo plugin enabled và database namespace. Runtime chỉ chuyển `WRITABLE` sau initial online-player cache resync thành công.

## 4. Xác minh commands

Trong console:

```text
teamadmin
teamadmin doctor
```

Trong game:

```text
/team
```

Kỳ vọng:

- `/teamadmin` báo database mode `WRITABLE`;
- doctor không phát hiện invariant lỗi;
- `/team` mở Paper Dialog hoặc clickable chat fallback;
- database file có schema Core.

## 5. Cấu hình cơ bản

Mặc định phù hợp cho test một server:

```yaml
database:
  type: sqlite
  namespace: default
  sqlite-file: openteams.db

ui:
  mode: auto
  default-locale: vi_VN
  follow-player-locale: false
```

Xem [Configuration](./configuration) trước khi đổi engine, namespace, pool hoặc policy timeout.

## MySQL/MariaDB

1. Tạo database và user với quyền DDL/DML.
2. Điền `host`, `port`, `database`, `username`, `password`.
3. Chọn `type: mysql` hoặc `type: mariadb`.
4. Dùng namespace riêng cho logical dataset.
5. Start và chạy doctor.

Core tự tạo JDBC URL với TCP keepalive; MySQL còn bật SSL. Đảm bảo server database được cấu hình certificate phù hợp.

## Cài addon

Addon phải khai báo:

```yaml
depend: [OpenTeams]
```

OpenTeams Core phải enable trước addon. Addon lấy API qua Bukkit ServicesManager, không cast plugin implementation. Xem [Addon API](./addon-api).

## Gỡ cài đặt

1. Stop server.
2. Backup nếu cần giữ team data.
3. Xóa Core JAR.
4. Xóa `plugins/OpenTeams/` nếu muốn xóa cả config/database.

::: warning Dữ liệu SQLite
Không xóa SQLite file trong lúc server đang chạy. WAL/SHM sidecar có thể chứa state chưa checkpoint.
:::
