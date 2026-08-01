# Chào mừng đến OpenTeams

**OpenTeams** là nền tảng team cho Paper và Folia. Plugin cung cấp trải nghiệm quản lý team bằng Paper Dialog, command fallback, transactional persistence và một public API ổn định về mặt thiết kế cho addon.

## Tìm nhanh

<CardGrid>
  <DocCard title="Tính năng" icon="✨" link="/docs/features" desc="Phạm vi gameplay, consistency và extension platform." />
  <DocCard title="Tải và build" icon="⬇️" link="/docs/download" desc="Lấy source, build JAR và xác minh artifact." />
  <DocCard title="Cài đặt" icon="📦" link="/docs/installation" desc="Từ server trống đến runtime WRITABLE." />
  <DocCard title="Commands" icon="⌨️" link="/docs/commands" desc="Toàn bộ player và admin command tree." />
  <DocCard title="Configuration" icon="⚙️" link="/docs/configuration" desc="Mọi config key, default và trade-off." />
  <DocCard title="Database" icon="🗄️" link="/docs/database" desc="Storage engines, lease fencing và recovery." />
  <DocCard title="Troubleshooting" icon="🧯" link="/docs/troubleshooting" desc="Lỗi schema, read-only, Dialog, offline invite và reload." />
  <DocCard title="Addon API" icon="🔌" link="/docs/addon-api" desc="Queries, mutations, events và extension lifecycle." />
</CardGrid>

## OpenTeams phù hợp khi nào?

OpenTeams phù hợp nếu server cần:

- team lifecycle đầy đủ: create, invite, join request, leave, transfer và disband;
- moderation theo role priority: kick, ban, approve và role assignment;
- UI hiện đại nhưng vẫn có command/chat fallback;
- database state có transaction, audit và cache consistency rõ ràng;
- addon contributions có ownership, permission và timeout contract;
- Paper hoặc Folia với Java 21 bytecode.

OpenTeams **chưa** nên được xem là stable 1.0 nếu bạn cần custom role editor hoàn chỉnh, MySQL/MariaDB Testcontainers coverage, public compatibility guarantee hoặc generated API reference. Xem [Trạng thái phát hành](./release-status).

## Mental model ngắn

<StatGrid>

**Database**<br>
Nguồn sự thật authoritative.

**Snapshot cache**<br>
Hot-path reads không chạm JDBC.

**Mutation pipeline**<br>
Policy → transaction → cache → event.

</StatGrid>

Một `TeamSnapshot` là dữ liệu bất biến tại một version cụ thể. Method có hậu tố `Cached` không truy cập database. Authoritative query và mutation trả `CompletionStage` và hoàn tất trên OpenTeams worker thread.

## Yêu cầu

| Thành phần | Yêu cầu |
|---|---|
| Server | Paper hoặc Folia, API `1.21.11+` |
| Java bytecode | 21 |
| Build toolchain | Java 25 toolchain qua Gradle |
| Storage | SQLite, MySQL hoặc MariaDB |
| Plugin dependencies | Không có dependency bắt buộc |

## Tiếp theo

Bắt đầu với [Tải và build](./download), sau đó làm theo [Cài đặt](./installation). Nếu bạn đang tích hợp plugin khác, đi thẳng tới [Addon API](./addon-api).
