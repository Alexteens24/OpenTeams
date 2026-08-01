# Trạng thái phát hành

<span class="ot-badge">1.0.0-SNAPSHOT · indev</span>

OpenTeams đang trong giai đoạn development. Số version trong `plugin.yml` không phải cam kết production-stable 1.0.

## Đã triển khai

- SQLite, MySQL và MariaDB JDBC layer với HikariCP.
- Consolidated development schema baseline qua Flyway.
- Atomic team lifecycle, invitation, join request, ban, transfer, role và setting mutations.
- Immutable cache với stale-version rejection và generation-protected membership load.
- Database lease, fencing token, read-only degradation và recovery cache rebuild.
- Transactional audit với correlation ID xuyên API result và committed event.
- Timeout-bounded addon policies và plugin-owned extension lifecycle.
- Cache-only friendly fire/team chat; persistent chat toggle và staff spy.
- Dialog-first command center, public discovery, offline invitations và clickable fallback.
- English/Vietnamese message catalogs và addon translation resolution.
- SQLite integration tests, cache/service/chat/UI localization tests.
- `/teamadmin doctor` và retention cleanup.

## Release gates còn mở

Các mục sau phải hoàn tất trước khi coi `1.0` là stable:

1. Testcontainers coverage cho MySQL và MariaDB.
2. Real Paper và Folia process tests trong CI.
3. Crash-injection tests tại transaction/cache publication boundaries.
4. Public admin tooling cho custom role templates và per-role overrides.
5. PlaceholderAPI adapter và chat moderation hook.
6. Hoàn thiện bản dịch và admin responses.
7. Benchmark suite cho chat, damage, placeholder và high-contention writes.
8. API compatibility gate (ví dụ japicmp) và generated reference docs.

## Schema policy trong indev

`V1__core_schema.sql` là một **consolidated development baseline**. Khi schema alpha đổi, file V1 có thể được chỉnh trực tiếp.

::: danger Không upgrade database alpha cũ tại chỗ
Backup nếu cần dữ liệu để debug, sau đó xóa database thử nghiệm cũ và để OpenTeams tạo lại. Versioned upgrade migrations chỉ bắt đầu sau khi schema phát hành đầu tiên được đóng băng.
:::

Với SQLite đã stop server, xóa cả file chính và sidecar nếu tồn tại:

```text
plugins/OpenTeams/openteams.db
plugins/OpenTeams/openteams.db-shm
plugins/OpenTeams/openteams.db-wal
```

## Compatibility hiện tại

| Bề mặt | Trạng thái |
|---|---|
| Paper API | Target `1.21.11+` |
| Folia | `folia-supported: true` |
| Java | Build release 21; Gradle toolchain 25 |
| Public API | Thiết kế module riêng, chưa có binary compatibility gate |
| Database schema | Indev baseline, chưa có upgrade guarantee |
| PlugMan/hot reload | Không được hỗ trợ |
| TeamChest | Addon milestone riêng, không nằm trong Core JAR |
