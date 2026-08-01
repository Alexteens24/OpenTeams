# Development

Hướng dẫn dành cho contributor làm việc trên OpenTeams Core hoặc public API.

## Repository layout

```text
OpenTeams/
├─ openteams-api/
├─ openteams-core/
├─ openteams-dialog-ui/
├─ openteams-test-kit/
├─ openteams-example-addon/
├─ docs/                 # internal architecture/release notes
└─ website/              # VitePress public documentation
```

## Toolchain

- Gradle wrapper `9.2.1`
- Java 25 toolchain
- Java compiler `--release 21`
- Paper API repository
- JUnit Platform
- Shadow plugin cho deployable Core JAR

## Build và test

```bash
./gradlew clean test build
```

Module-specific:

```bash
./gradlew :openteams-api:build
./gradlew :openteams-core:test
./gradlew :openteams-dialog-ui:test
./gradlew :openteams-example-addon:build
```

Deployable artifact là `openteams-core` shadow JAR; plain Core JAR task bị skip.

## Documentation

```bash
cd website
npm install
npm run docs:dev
npm run docs:build
npm run docs:preview
```

Public URL dùng base `/OpenTeams/`. Internal Markdown links nên dùng VitePress routes; public/config logo paths phải base-aware.

## Test focus

Current suite bao phủ:

- SQLite JDBC mutations và invariants;
- aggregate read/membership behavior;
- cache version/generation races;
- TeamService policy/cache publication;
- addon registry ownership/cleanup;
- chat preference state machine;
- name/tag validation;
- localization catalogs.

Release gates còn thiếu gồm MySQL/MariaDB Testcontainers, real Paper/Folia process CI, crash injection và benchmarks.

## Source conventions

- Public API trả immutable records/copies.
- Cached method name phải nói rõ cache behavior.
- Database mutation phải giữ audit và lease assertion trong transaction.
- Cache chỉ publish sau commit.
- Không chặn Paper/Folia entity thread bằng JDBC.
- Completion callback phải schedule đúng region/entity trước Bukkit mutation.
- Addon extension phải có Bukkit owner và unregister path.
- UI text đi qua translation keys/resource bundles.

## Thêm mutation

Checklist:

1. Thêm `MutationType`.
2. Thêm request record nếu payload mới.
3. Expose `CompletionStage<OperationResult<TeamSnapshot>>` trong `TeamService`.
4. Evaluate policy với stable correlation ID.
5. Implement JDBC transaction có `assertLease`.
6. Validate permission/priority/domain invariants.
7. Append audit trong transaction.
8. Load committed snapshot.
9. Publish cache sau commit.
10. Emit `TeamMutationCommittedEvent`.
11. Thêm service/JDBC/cache race tests.
12. Thêm UI/command message và docs.

## Thêm extension type

- Public interface/records nằm trong API module.
- Registration nhận actual Bukkit `Plugin`.
- Key ownership/collision rule rõ ràng.
- Core registry thread-safe.
- `PluginDisableEvent` cleanup.
- Cache invalidation nếu contribution được materialize vào snapshot.
- Sync/async và timeout contract được document.
- Example addon làm compatibility fixture.

## Schema changes trong indev

V1 hiện có thể consolidate. Khi sửa:

- update schema resource;
- xóa test database cũ;
- update JDBC integration tests;
- update [Database](./database) và [Release status](./release-status);
- không tạo fake migration history chỉ để giữ alpha DB.

Sau schema freeze, không sửa migration đã phát hành; thêm versioned migration mới.

## Smoke test Paper

1. Build shadow JAR.
2. Tạo server test sạch.
3. Deploy chỉ Core JAR.
4. Start với Java supported.
5. Chờ `WRITABLE`.
6. Run doctor.
7. Test create → invite → accept → transfer/leave → disband → recreate.
8. Test offline invite sau target từng join.
9. Test team-chat persistence qua reconnect.
10. Stop sạch và kiểm tra pool shutdown.

## Pull request checklist

- [ ] `./gradlew test build`
- [ ] `npm run docs:build` nếu docs thay đổi
- [ ] Không commit database/build/node_modules artifacts
- [ ] Public API diff được review
- [ ] Transaction/cache ordering được giải thích
- [ ] Error/locale keys đầy đủ
- [ ] Docs/config/example addon cập nhật cùng source
