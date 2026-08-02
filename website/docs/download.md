# Tải và build

OpenTeams chưa có stable binary release. Ở giai đoạn indev, cách đáng tin cậy là build từ source của commit bạn muốn thử.

## Yêu cầu build

- Git
- JDK 25 cho Gradle toolchain
- Kết nối Maven Central và PaperMC repository

Source được compile với `--release 21`, vì vậy deployable JAR chạy bằng Java 21 trở lên.

## Clone repository

```bash
git clone https://github.com/Alexteens24/OpenTeams.git
cd OpenTeams
```

Muốn tái tạo đúng một revision, checkout commit SHA thay vì luôn dùng branch head:

```bash
git checkout <commit-sha>
```

## Build toàn bộ project

```bash
./gradlew clean build
```

Deployable shaded JAR:

```text
openteams-core/build/libs/openteams-core-0.1.0.jar
```

Các JAR của `openteams-api`, `openteams-dialog-ui`, `openteams-test-kit` và example addon không thay thế Core JAR trên server.

## Xác minh artifact

```bash
jar tf openteams-core/build/libs/openteams-core-*.jar | grep plugin.yml
sha256sum openteams-core/build/libs/openteams-core-*.jar
```

JAR phải chứa:

- `plugin.yml` và `config.yml`;
- `db/migration/common/V1__core_schema.sql`;
- Core classes và relocated runtime dependencies;
- Dialog UI classes/message bundles.

## Chỉ chạy tests

```bash
./gradlew test
```

## Build một module cho addon development

```bash
./gradlew :openteams-api:build
./gradlew :openteams-example-addon:build
```

Example addon là compatibility fixture cho registration lifecycle và public types. Nó cũng là source tham chiếu tốt nhất trước khi generated API docs được bổ sung.

## Update an toàn

1. Đọc [Trạng thái phát hành](./release-status) và diff schema/config.
2. Stop server hoàn toàn.
3. Backup database và thư mục plugin.
4. Thay Core JAR.
5. Start server, chờ runtime `WRITABLE`.
6. Chạy `/teamadmin doctor`.

Không dùng PlugMan. Xem [Production runbook](./operations) để hiểu lý do.
