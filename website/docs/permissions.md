# Roles & permissions

OpenTeams có hai lớp authorization độc lập:

1. **Bukkit permissions** kiểm soát quyền vào command tree/admin tooling.
2. **Team permissions** nằm trong role snapshot và kiểm soát domain action trong một team.

## Bukkit permissions

| Permission | Default | Tác dụng |
|---|---|---|
| `openteams.command.team` | `true` | Dùng `/team` và `/teams` |
| `openteams.admin` | `op` | Dùng `/teamadmin`, doctor và cleanup |
| `openteams.admin.spy` | `op` | Dùng `/teamadmin spy` |

Addon command contribution có thể khai báo Bukkit permission riêng.

## Role templates mặc định

Role templates được seed một lần cho mỗi namespace khi chưa có template nào.

| Key | Display | Priority | Member limit | Protected |
|---|---|---:|---:|---|
| `owner` | Owner | 1000 | 1 | Có |
| `co_owner` | Co-owner | 750 | Không giới hạn riêng | Không |
| `moderator` | Moderator | 500 | Không giới hạn riêng | Không |
| `member` | Member | 100 | Không giới hạn riêng | Không |

## Team permission matrix

| Permission | Owner | Co-owner | Moderator | Member |
|---|:---:|:---:|:---:|:---:|
| `*` | ✓ | — | — | — |
| `team.invite` | ✓ | ✓ | ✓ | — |
| `team.kick` | ✓ | ✓ | ✓ | — |
| `team.ban` | ✓ | ✓ | ✓ | — |
| `team.join-request.accept` | ✓ | ✓ | ✓ | — |
| `team.role.change` | ✓ | ✓ | — | — |
| `team.rename` | ✓ | ✓ | — | — |
| `team.settings.manage` | ✓ | ✓ | — | — |

Owner wildcard thỏa mọi Core/addon team permission.

## Priority rule

Với kick, ban và một số manager-target actions:

```text
actor role priority > target role priority
```

Điều này có nghĩa:

- Moderator không thao tác lên Moderator, Co-owner hoặc Owner.
- Co-owner thao tác được lên Moderator/Member nhưng không lên Co-owner/Owner.
- Owner có priority cao nhất nhưng owner target vẫn được bảo vệ bởi domain invariants.

## Ownership invariants

- Chỉ role key `owner` có member limit 1.
- Active team owner phải tồn tại trong member list với role `owner`.
- Owner không thể leave, kick/ban chính mình hoặc bị role change.
- Không thể gán `owner` bằng `/team role`; dùng ownership transfer.
- Transfer đặt target thành Owner và owner cũ thành Co-owner trong cùng transaction.

## Addon permissions

Addon đăng ký permission:

```java
api.permissions().register(plugin,
    new TeamPermissionRegistry.Permission(
        "example.use",
        "example.permission.use",
        Set.of("owner", "co_owner", "moderator")
    ));
```

`defaultRoles` được merge vào member snapshots. Typed setting và UI action dùng cùng permission engine; vì vậy permission runtime của addon được enforce nhất quán.

Khi addon disable, contribution bị xóa và affected snapshots/cache authorization được invalidated để không giữ quyền addon cũ.

## Custom roles hiện tại

Schema/API có query `roles()` và domain hỗ trợ role metadata. Tuy nhiên public CRUD/admin tooling cho custom role templates và per-role permission override vẫn là release gate. Không sửa trực tiếp tables nếu bạn không kiểm soát invariants và upgrade path.
