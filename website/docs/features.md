# Tính năng

Trang này mô tả phạm vi OpenTeams hiện đã triển khai. Các mục chưa đạt release gate được liệt kê riêng trong [Trạng thái phát hành](./release-status).

## Team lifecycle

- Tạo team với tên Unicode được chuẩn hóa NFKC và unique không phân biệt hoa/thường.
- Tên từ 3–24 ký tự; hỗ trợ chữ, số, dấu gạch dưới và khoảng trắng.
- Tag tùy chọn từ 1–8 ký tự chữ/số.
- Mời player online hoặc offline thông qua last-known player directory.
- Invitation có expiry và inbox cho người nhận.
- Public team discovery theo tên/tag với phân trang.
- Join request hai chiều: player theo dõi request đã gửi; team quản lý request đang chờ.
- Leave, kick, ownership transfer và disband có invariant bảo vệ owner.
- Ban/unban kèm reason và lịch sử thời điểm.

## Dialog-first player experience

`/team` mở command center phù hợp với trạng thái người chơi:

- player chưa có team thấy create, Explore và invitation inbox;
- member thấy overview, roster, team chat và leave;
- manager thấy invitation/request/moderation actions;
- owner thấy transfer, settings và disband;
- addon có thể đóng góp action vào Dashboard, Members hoặc Settings.

Paper Dialog được cô lập trong module `openteams-dialog-ui`. Nếu việc tạo Dialog thất bại, UI chuyển về Adventure chat components và commands. Khi một mutation từ Dialog lỗi, Dialog được đóng trước để phản hồi chat không bị che.

## Roles và authorization

Core seed bốn role mặc định:

| Role | Priority | Phạm vi mặc định |
|---|---:|---|
| Owner | 1000 | Wildcard `*` |
| Co-owner | 750 | Invite, kick, ban, approve request, role, rename, settings |
| Moderator | 500 | Invite, kick, ban, approve request |
| Member | 100 | Gameplay cơ bản |

Authorization kiểm tra permission key. Với thao tác lên một player khác, actor còn phải có priority cao hơn target. Xem bảng đầy đủ tại [Roles & permissions](./permissions).

## Team chat và friendly fire

- Gửi tức thời qua immutable team cache; không query JDBC trên AsyncChatEvent.
- `/team chat` bật/tắt preference và lưu vào database.
- Preference load/toggle được serialize để kết quả async cũ không ghi đè thao tác mới.
- Staff spy có toggle riêng qua `/teamadmin spy`.
- Format dùng MiniMessage với placeholder `<tag>`, `<player>` và `<message>`.
- Friendly fire đọc relation cache-only trên damage event.
- Setting `friendly-fire` typed theo team có thể override default server.

## Correctness và recovery

- Database là nguồn sự thật.
- Domain rows và audit row commit cùng transaction.
- `teams.version` là optimistic aggregate token.
- Membership uniqueness được khóa bằng primary key `(namespace, player_id)`.
- Cache chỉ publish sau commit; stale version bị từ chối.
- Membership cache có generation để query cũ không ghi đè mutation mới.
- Một live instance giữ lease cho mỗi namespace.
- Monotonic fence token được kiểm tra trong từng write transaction.
- Khi mất lease/database, runtime chuyển `DEGRADED_READ_ONLY`.
- Sau khi lease trở lại, online-player cache được rebuild trước khi mở ghi.

## Addon platform

Addon có thể đăng ký:

- `/team` subcommands;
- cached Adventure placeholders;
- typed settings và custom team permissions;
- Dialog/chat dashboard actions;
- locale translation maps;
- bounded pre-commit mutation policies.

Mỗi registration thuộc Bukkit `Plugin` đã đăng ký. Core tự gỡ toàn bộ contributions khi addon disable, kể cả khi addon quên gọi `Registration.close()`.

## Administration

- `/teamadmin` hiển thị database mode, UI adapter và số addon command.
- `/teamadmin doctor` kiểm tra lease và domain invariants.
- `/teamadmin cleanup confirm` dọn audit và dữ liệu tạm hết hạn theo retention.
- Fail-safe startup: lỗi initialization sẽ disable plugin thay vì để trạng thái dở dang.
- Graceful shutdown đóng service workers, chat store, Hikari pool và lease.
