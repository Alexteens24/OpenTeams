# Extension points

Mỗi registry nhận Bukkit `Plugin owner`. Core normalize plugin name thành owner ID lowercase và namespaced các key không phải command.

## Key ownership

Ví dụ plugin tên `OpenTeams-ExampleAddon` có owner ID:

```text
openteams-exampleaddon
```

Key raw `example.enabled` được lưu canonical:

```text
openteams-exampleaddon:example.enabled
```

Addon không thể đăng ký key có namespace của owner khác. Duplicate canonical key bị từ chối.

## Commands

```java
Registration command = api.commands().register(plugin,
    new CommandRegistry.CommandContribution(
        "example",
        List.of("ex"),
        "myaddon.command.example",
        "myaddon.command.example.description",
        (sender, arguments) -> {
            sender.sendMessage(Component.text("Connected"));
            return CompletableFuture.completedFuture(1);
        }
    ));
```

Command chạy dưới `/team example`. Handler async trả integer result.

Core từ chối:

- name/alias trùng Core reserved command;
- name/alias trùng contribution hiện tại;
- command permission sender không có.

Reserved set hiện bao gồm create, info, invite, accept, leave, kick, transfer, rename, tag, disband, chat, request, approve, ban, unban, role, setting và help.

## Placeholders

```java
Registration placeholder = api.placeholders().register(plugin,
    new PlaceholderRegistry.Placeholder(
        "team-level",
        (viewerId, snapshot) -> Component.text(snapshot.members().size()),
        Component.text("0")
    ));
```

Resolver nhận viewer ID và immutable team snapshot.

::: warning Hot-path contract
Resolver phải non-blocking: không JDBC, HTTP, disk I/O hoặc synchronous Bukkit lookup đắt. Trả fallback khi không thể resolve bằng dữ liệu cache đã có.
:::

Public PlaceholderAPI adapter vẫn là release gate; registry là Core extension primitive, không tự tạo PlaceholderAPI expansion.

## Typed settings

```java
Registration setting = api.settings().register(plugin,
    new TeamSettingRegistry.Setting<>(
        "example.enabled",
        Boolean.class,
        true,
        new TeamSettingRegistry.Codec<>() {
            public String encode(Boolean value) { return value.toString(); }
            public Boolean decode(String value) {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException("Expected boolean");
                }
                return Boolean.parseBoolean(value);
            }
        },
        value -> true,
        "example.use"
    ));
```

Write path:

1. resolve canonical registered key;
2. decode string qua codec;
3. validate typed value;
4. check setting permission;
5. persist encoded string và increment team version;
6. publish new snapshot.

Setting invalid/unknown trả `INVALID_ARGUMENT` thay vì ghi arbitrary string.

## Team permissions

```java
Registration permission = api.permissions().register(plugin,
    new TeamPermissionRegistry.Permission(
        "example.use",
        "example.permission.use",
        Set.of("owner", "co_owner", "moderator")
    ));
```

Default permissions được merge theo role key vào member snapshots. Owner wildcard vẫn match. Đăng ký permission trước setting/UI action dùng nó để startup state rõ ràng.

## UI actions

```java
Registration action = api.userInterface().register(plugin,
    new TeamUiRegistry.UiAction(
        "dashboard",
        TeamUiRegistry.Area.DASHBOARD,
        100,
        "example.ui.label",
        "example.ui.description",
        "example.use",
        context -> true,
        context -> CompletableFuture.completedFuture(
            TeamUiRegistry.ActionOutcome.REFRESH)
    ));
```

### Areas

- `DASHBOARD`
- `MEMBERS`
- `SETTINGS`

Lower/higher priority ordering được adapter dùng để sắp contributions theo implementation hiện tại. Action render/execute chỉ khi viewer có permission và availability trả true.

`UiContext` chứa viewer ID, team ID và team version. Core reject stale context trước handler. Không giữ context như live object; authoritative query lại nếu action chạy lâu.

### Outcomes

| Outcome | Hành vi |
|---|---|
| `REFRESH` | Rebuild command center từ state mới |
| `CLOSE` | Đóng UI; addon sở hữu tương tác tiếp theo |

Availability là synchronous hot callback: không database/HTTP. Handler async và phải schedule về entity owner trước Bukkit mutation.

## Translations

```java
Registration vi = api.translations().register(plugin, Locale.forLanguageTag("vi-VN"), Map.of(
    "example.ui.label", "Tiện ích mẫu",
    "example.ui.description", "Chạy action của addon"
));
```

Lookup match exact language tag hoặc language code. Dùng unique translation key prefix để tránh collision semantic.

## Mutation policies

```java
Registration policy = api.policies().register(plugin,
    new MutationPolicyRegistry.PolicyContribution(
        "region-rule",
        100,
        Duration.ofMillis(100),
        intent -> CompletableFuture.completedFuture(
            allowed(intent) ? PolicyDecision.allow()
                            : PolicyDecision.deny("myaddon.policy.denied"))
    ));
```

Policies chạy theo priority tăng dần trước transaction.

### Timeout/failure semantics

- Per-policy timeout phải `> 0` và `<= 2s`.
- Global deadline từ `addons.policy-global-timeout-ms` áp lên cả chain.
- Explicit `deny` dừng mutation.
- Timeout, exception hoặc exhausted global deadline **fail open** và được warning log.
- Timed-out CompletableFuture được cancel best-effort.

Fail-open ngăn addon lỗi làm Core mất availability, nhưng policy không phù hợp cho security boundary duy nhất nếu deny tuyệt đối là bắt buộc.

## Disable cleanup

Khi owner disable, Core xóa commands, UI actions, placeholders, settings, permissions, translations và policies. Addon-derived permission cache được invalidated để cached `hasPermission` không giữ contribution đã biến mất.
