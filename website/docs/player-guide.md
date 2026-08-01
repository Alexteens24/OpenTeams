# Hướng dẫn người chơi

Phần này mô tả flow sử dụng, không chỉ syntax lệnh. Danh sách command đầy đủ nằm tại [Commands](./commands).

## Mở command center

```text
/team
```

OpenTeams chọn màn hình dựa trên membership và permission hiện tại. Với `ui.mode: auto`, Paper Dialog được ưu tiên; nếu Dialog không khả dụng, plugin gửi clickable Adventure chat components.

## Tạo team

```text
/team create Green Valley
```

Quy tắc tên:

- 3–24 ký tự sau khi strip và normalize NFKC;
- chữ Unicode, số, `_` và khoảng trắng;
- unique theo normalized lowercase name;
- tag từ 1–8 ký tự chữ/số hoặc để trống.

Sau create thành công, creator là Owner và cache được publish với version mới.

## Mời người chơi

```text
/team invite PlayerName
```

Moderator trở lên có permission mặc định. Target có thể offline nếu tên đã tồn tại trong last-known player directory. Invitation tồn tại trong database đến khi được accept, decline, revoke hoặc hết hạn.

Người nhận mở:

```text
/team invitations
/team accept <team-id>
/team decline <team-id>
```

Một player không thể accept nếu đã thuộc team, bị ban, invitation hết hạn hoặc team đã đầy.

## Tìm và xin vào team public

```text
/team explore
/team explore green
/team request <team-id>
```

Chỉ team `PUBLIC` xuất hiện. Request không tự thêm member; manager phải approve:

```text
/team approve <player>
```

Owner/manager cũng có thể xử lý request trong command center.

## Team chat

Gửi một tin mà không đổi mode:

```text
/team chat Xin chào team!
```

Bật/tắt mode persistent:

```text
/team chat
```

Khi mode bật, chat thường được route vào team. Preference được lưu qua lần đăng nhập. OpenTeams chờ state load hoàn tất để không làm lộ một tin team ra global chat do race lúc join.

## Quản lý member

```text
/team kick <player>
/team ban <player> [reason]
/team unban <player>
/team role <player> <role>
```

Actor phải có permission tương ứng và role priority cao hơn target. Owner là role được bảo vệ, không thể bị kick/ban hoặc đổi role bằng flow thông thường.

## Đổi thông tin team

```text
/team rename <name>
/team tag <tag>
/team visibility <public|private>
/team setting friendly-fire <true|false>
```

Co-owner có rename/settings permissions mặc định. Addon có thể đăng ký thêm typed settings, mỗi setting có permission riêng.

## Leave, transfer và disband

Member thường:

```text
/team leave
```

Owner không thể leave và bỏ team active không chủ. Hãy chuyển quyền:

```text
/team transfer <player>
```

Hoặc giải tán:

```text
/team disband confirm
```

Transfer chuyển owner cũ thành `co_owner`. Disband đánh dấu aggregate không còn active và dọn membership theo transaction. Sau commit, owner có thể tạo team mới.

## Khi thao tác lỗi

OpenTeams trả message theo locale và error code như `FORBIDDEN`, `CONFLICT`, `LIMIT_REACHED`, `READ_ONLY` hoặc `DATABASE_UNAVAILABLE`. Trong Dialog flow, UI đóng trước để bạn đọc được lỗi trong chat.

Nếu runtime đang read-only, cached info/chat/friendly-fire vẫn có thể hoạt động nhưng mutations sẽ bị từ chối. Báo admin chạy `/teamadmin doctor`.
