# UI & localization

OpenTeams có hai UI adapter và message catalogs nằm trong `openteams-dialog-ui`.

## UI modes

| Mode | Hành vi |
|---|---|
| `auto` | Tạo Dialog adapter; fallback sang clickable chat nếu dynamic Dialog thất bại |
| `dialog` | Hiện dùng cùng Dialog adapter và fallback behavior như `auto` |
| `chat` | Luôn dùng Adventure chat components và run-command click events |

Paper experimental Dialog types không xuất hiện trong public API. Addon đóng góp platform-neutral `UiAction`; Core adapter quyết định render.

## Locale selection

```yaml
ui:
  default-locale: vi_VN
  follow-player-locale: false
```

Khi `follow-player-locale: false`, mọi player dùng default locale. Khi bật:

1. thử addon translation ở player locale;
2. thử Core resource bundle ở player locale;
3. fallback sang default locale;
4. nếu vẫn thiếu, hiển thị translation key.

Catalog bundled hiện có base English và `vi_VN`.

## Color policy

Default messages ưu tiên text không màu. Màu chỉ giữ ở:

- success/error feedback;
- destructive hoặc confirmation actions;
- selected dashboard actions;
- enabled state;
- clickable command CTAs.

Small caps chỉ dùng hạn chế cho label/heading đặc biệt, không áp lên toàn bộ câu dài.

## Dialog error behavior

Khi async mutation thất bại, Dialog được đóng trước khi error component gửi vào chat. Điều này tránh tình trạng player bị màn hình Dialog che và không đọc được nguyên nhân lỗi.

## Addon translations

```java
Registration translations = api.translations().register(
    plugin,
    Locale.forLanguageTag("vi-VN"),
    Map.of(
        "example.ui.label", "Tiện ích mẫu",
        "example.ui.description", "Mở chức năng của addon"
    )
);
```

UI action nên dùng key có namespace addon. Không dùng raw display text trong `labelKey`/`descriptionKey`.

## MiniMessage chat format

```yaml
chat:
  format: "<aqua>[<tag>]</aqua> <white><player>:</white> <gray><message></gray>"
```

Reserved placeholders:

| Placeholder | Giá trị |
|---|---|
| `<tag>` | Tag team hoặc fallback phù hợp |
| `<player>` | Tên sender |
| `<message>` | Adventure message component |

Kiểm tra format trên test server. MiniMessage syntax lỗi có thể làm chat render không như mong đợi.
