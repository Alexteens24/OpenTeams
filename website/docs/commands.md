# Commands

OpenTeams đăng ký Brigadier command tree qua Paper Lifecycle API. Alias mặc định của `/team` là `/teams`.

## Command gốc

| Command | Mô tả | Yêu cầu |
|---|---|---|
| `/team` | Mở Dialog/chat command center | `openteams.command.team` |
| `/team create <name>` | Tạo team mới | Chưa thuộc team |
| `/team info` | Load và hiển thị team hiện tại | Đang thuộc team |
| `/team explore [query]` | Tìm tối đa 10 team public ở trang đầu | Player |
| `/team invitations` | Liệt kê invitation đang chờ | Player |

`/team` và mọi subcommand đều yêu cầu Bukkit permission `openteams.command.team`, mặc định cấp cho tất cả.

## Invitation và join request

| Command | Mô tả | Team permission/trạng thái |
|---|---|---|
| `/team invite <player>` | Mời player online hoặc offline đã biết | `team.invite` |
| `/team accept <team-id>` | Chấp nhận invitation | Target, chưa thuộc team |
| `/team decline <team-id>` | Từ chối invitation | Target |
| `/team request <team-id>` | Xin vào team public | Chưa thuộc team |
| `/team approve <player>` | Accept join request của player | `team.join-request.accept` |

UI còn hỗ trợ reject/cancel/revoke flows qua public service API dù command tree không expose tất cả thành subcommand riêng.

## Membership và moderation

| Command | Mô tả | Yêu cầu |
|---|---|---|
| `/team leave` | Rời team | Member không phải Owner |
| `/team kick <player>` | Loại member | `team.kick`, priority cao hơn target |
| `/team transfer <player>` | Chuyển ownership | Owner; target là member |
| `/team ban <player> [reason]` | Ban và loại target nếu đang ở team | `team.ban`, priority cao hơn target |
| `/team unban <player>` | Gỡ ban | `team.ban` |
| `/team role <player> <role>` | Đổi role | `team.role.change`; không gán Owner |

Ownership transfer chuyển Owner cũ thành `co_owner`; không tạo team active với owner trống.

## Team settings

| Command | Mô tả | Permission |
|---|---|---|
| `/team rename <name>` | Đổi tên và normalized name | `team.rename` |
| `/team tag <tag>` | Đổi tag 1–8 ký tự | `team.settings.manage` |
| `/team visibility <public\|private>` | Đổi public discovery | `team.settings.manage` |
| `/team setting <key> <value>` | Ghi typed Core/addon setting | Permission khai báo bởi setting |
| `/team disband confirm` | Giải tán team | Owner |

Core setting hiện có:

```text
/team setting friendly-fire true
/team setting friendly-fire false
```

## Team chat

| Command | Mô tả |
|---|---|
| `/team chat` | Toggle persistent team-chat mode |
| `/team chat <message>` | Gửi một message vào team mà không đổi mode |

Nếu sender chưa có team, broadcast bị từ chối. Staff spy nhận bản sao nếu đã bật `/teamadmin spy`.

## Admin commands

| Command | Mô tả | Bukkit permission |
|---|---|---|
| `/teamadmin` | Database mode, UI adapter, addon command count | `openteams.admin` |
| `/teamadmin doctor` | Chạy integrity report async | `openteams.admin` |
| `/teamadmin cleanup confirm` | Xóa expired invitations/requests/bans và old audit | `openteams.admin` |
| `/teamadmin spy` | Toggle staff team-chat spy | `openteams.admin.spy` |

Doctor report gồm:

- active team thiếu owner member;
- owner có role sai;
- dangling member rows;
- expired invitation/request/ban counts;
- tổng audit rows.

Expired rows không làm doctor `FAILED`; ba invariant đầu mới quyết định health.

## Addon subcommands

Addon đăng ký `CommandContribution` sẽ xuất hiện dưới:

```text
/team <extension> [arguments...]
```

Core kiểm tra contribution Bukkit permission trước khi gọi async handler. Name/alias không được trùng Core command hoặc contribution khác. Addon exception trả lỗi chung và được log.

## Console và player-only

`/teamadmin` hỗ trợ console. Phần lớn `/team` flows yêu cầu `Player`; console sẽ nhận thông báo command requires a player. `/team info` trong command dashboard cũng được thiết kế cho player context.
