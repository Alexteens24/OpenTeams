package me.alexisbinh.openteams.homes.ui;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;

public final class HomesMessages {
    private final Map<String, String> english = english();
    private final Map<String, String> vietnamese = vietnamese();

    public Map<String, String> englishEntries() { return Map.copyOf(english); }
    public Map<String, String> vietnameseEntries() { return Map.copyOf(vietnamese); }

    public Component component(Player player, String key) {
        return component(player, key, Map.of());
    }

    public Component component(Player player, String key, Map<String, String> arguments) {
        var locale = player.locale();
        var template = (locale != null && locale.getLanguage().equals("vi")
                ? vietnamese : english).getOrDefault(key, key);
        for (var argument : arguments.entrySet()) {
            template = template.replace("{" + argument.getKey() + "}", argument.getValue());
        }
        return Component.text(template);
    }

    private static Map<String, String> english() {
        var map = new HashMap<String, String>();
        map.put("homes.ui.home", "Team Home");
        map.put("homes.ui.home.description", "Teleport to or manage the shared team home");
        map.put("homes.ui.warps", "Team Warps");
        map.put("homes.ui.warps.description", "Browse and manage named team warps");
        map.put("homes.ui.loading", "Loading team teleport points…");
        map.put("homes.ui.back", "Back");
        map.put("homes.ui.teleport", "Teleport");
        map.put("homes.ui.set", "Set here");
        map.put("homes.ui.delete", "Delete");
        map.put("homes.ui.rename", "Rename");
        map.put("homes.ui.create", "Create warp");
        map.put("homes.ui.previous", "Previous");
        map.put("homes.ui.next", "Next");
        map.put("homes.ui.search", "Search");
        map.put("homes.ui.home-empty", "Your team has no Home yet.");
        map.put("homes.ui.warps-empty", "No warps match this search.");
        map.put("homes.success.home-set", "Team Home has been saved.");
        map.put("homes.success.warp-created", "Warp {name} has been created.");
        map.put("homes.success.warp-updated", "Warp location has been updated.");
        map.put("homes.success.warp-renamed", "Warp has been renamed to {name}.");
        map.put("homes.success.deleted", "Teleport point has been deleted.");
        map.put("homes.success.teleported", "Teleported successfully.");
        map.put("homes.error.no-team", "You are not in a team.");
        map.put("homes.error.membership-load", "Could not load your team membership. Try again.");
        map.put("homes.error.forbidden", "Your team role cannot perform this action.");
        map.put("homes.error.feature-disabled", "This feature is disabled.");
        map.put("homes.error.home-not-set", "Your team has not set a Home.");
        map.put("homes.error.warp-not-found", "Warp was not found.");
        map.put("homes.error.invalid-name", "Warp names use 1–24 letters, numbers, _ or -.");
        map.put("homes.error.reserved-name", "That name is reserved by a command.");
        map.put("homes.error.duplicate-name", "A warp with that name already exists.");
        map.put("homes.error.limit", "Your team has reached the {limit} warp limit.");
        map.put("homes.error.conflict", "The point changed. The latest data was loaded.");
        map.put("homes.error.database", "The Homes database is temporarily unavailable.");
        map.put("homes.error.not-ready", "Homes is not writable right now. Try again shortly.");
        map.put("homes.error.different-server", "That destination belongs to another server.");
        map.put("homes.error.cooldown", "Wait {seconds}s before teleporting again.");
        map.put("homes.error.world-missing", "The destination world is unavailable.");
        map.put("homes.error.unsafe", "No safe location was found near the saved point.");
        map.put("homes.error.teleport-failed", "Teleport failed.");
        map.put("homes.error.cancelled", "Teleport was cancelled.");
        map.put("homes.error.cancelled-move", "Teleport cancelled because you moved.");
        map.put("homes.error.cancelled-damage", "Teleport cancelled because you took damage.");
        map.put("homes.error.cancelled-quit", "Teleport cancelled because you left.");
        map.put("homes.error.cancelled-death", "Teleport cancelled because you died.");
        map.put("homes.error.cancelled-teleport", "Teleport cancelled by another teleport.");
        map.put("homes.error.cancelled-new-teleport", "The previous teleport was cancelled.");
        map.put("homes.error.cancelled-point-changed", "Teleport cancelled because the destination changed.");
        map.put("homes.error.cancelled-team-changed", "Teleport cancelled because team membership changed.");
        map.put("homes.error.cancelled-disable", "Teleport cancelled because Homes is stopping.");
        map.put("homes.error.team-changed", "You are no longer in the destination team.");
        map.put("homes.confirm.home-overwrite", "Run /team home set confirm to overwrite the current Home.");
        map.put("homes.confirm.home-delete", "Run /team home delete confirm to delete the Team Home.");
        map.put("homes.confirm.warp-update", "Run /team warp update {name} confirm to overwrite its location.");
        map.put("homes.confirm.warp-delete", "Run /team warp delete {name} confirm to delete it.");
        map.put("homes.command.home", "Use /team home [info|set|delete]");
        map.put("homes.command.warp", "Use /team warp list|teleport|info|create|update|rename|delete");
        map.put("homes.permission.home.teleport", "Teleport to Team Home");
        map.put("homes.permission.home.set", "Set or update Team Home");
        map.put("homes.permission.home.delete", "Delete Team Home");
        map.put("homes.permission.warp.view", "View Team Warps");
        map.put("homes.permission.warp.teleport", "Teleport to Team Warps");
        map.put("homes.permission.warp.create", "Create Team Warps");
        map.put("homes.permission.warp.update", "Update Team Warp locations");
        map.put("homes.permission.warp.rename", "Rename Team Warps");
        map.put("homes.permission.warp.delete", "Delete Team Warps");
        return map;
    }

    private static Map<String, String> vietnamese() {
        var map = new HashMap<>(english());
        map.put("homes.ui.home", "Team Home");
        map.put("homes.ui.home.description", "Dịch chuyển hoặc quản lý Home dùng chung của team");
        map.put("homes.ui.warps", "Team Warps");
        map.put("homes.ui.warps.description", "Xem và quản lý các Warp có tên của team");
        map.put("homes.ui.loading", "Đang tải điểm dịch chuyển của team…");
        map.put("homes.ui.back", "Quay lại");
        map.put("homes.ui.teleport", "Dịch chuyển");
        map.put("homes.ui.set", "Đặt tại đây");
        map.put("homes.ui.delete", "Xóa");
        map.put("homes.ui.rename", "Đổi tên");
        map.put("homes.ui.create", "Tạo Warp");
        map.put("homes.ui.previous", "Trang trước");
        map.put("homes.ui.next", "Trang sau");
        map.put("homes.ui.search", "Tìm kiếm");
        map.put("homes.ui.home-empty", "Team của bạn chưa đặt Home.");
        map.put("homes.ui.warps-empty", "Không có Warp phù hợp.");
        map.put("homes.success.home-set", "Đã lưu Team Home.");
        map.put("homes.success.warp-created", "Đã tạo Warp {name}.");
        map.put("homes.success.warp-updated", "Đã cập nhật vị trí Warp.");
        map.put("homes.success.warp-renamed", "Đã đổi tên Warp thành {name}.");
        map.put("homes.success.deleted", "Đã xóa điểm dịch chuyển.");
        map.put("homes.success.teleported", "Dịch chuyển thành công.");
        map.put("homes.error.no-team", "Bạn chưa tham gia team nào.");
        map.put("homes.error.membership-load", "Không thể tải dữ liệu team. Hãy thử lại.");
        map.put("homes.error.forbidden", "Vai trò của bạn không được thực hiện thao tác này.");
        map.put("homes.error.feature-disabled", "Tính năng này đang bị tắt.");
        map.put("homes.error.home-not-set", "Team chưa đặt Home.");
        map.put("homes.error.warp-not-found", "Không tìm thấy Warp.");
        map.put("homes.error.invalid-name", "Tên Warp dài 1–24 ký tự chữ, số, _ hoặc -.");
        map.put("homes.error.reserved-name", "Tên này được dành cho command.");
        map.put("homes.error.duplicate-name", "Tên Warp này đã tồn tại.");
        map.put("homes.error.limit", "Team đã đạt giới hạn {limit} Warp.");
        map.put("homes.error.conflict", "Dữ liệu vừa thay đổi. Đã tải bản mới nhất.");
        map.put("homes.error.database", "Database Homes đang tạm thời không khả dụng.");
        map.put("homes.error.not-ready", "Homes hiện không thể ghi dữ liệu. Hãy thử lại sau.");
        map.put("homes.error.different-server", "Điểm đến nằm trên server khác.");
        map.put("homes.error.cooldown", "Hãy đợi {seconds} giây trước khi dịch chuyển tiếp.");
        map.put("homes.error.world-missing", "World của điểm đến không khả dụng.");
        map.put("homes.error.unsafe", "Không tìm thấy vị trí an toàn gần điểm đã lưu.");
        map.put("homes.error.teleport-failed", "Dịch chuyển thất bại.");
        map.put("homes.error.cancelled", "Đã hủy dịch chuyển.");
        map.put("homes.error.cancelled-move", "Đã hủy dịch chuyển vì bạn di chuyển.");
        map.put("homes.error.cancelled-damage", "Đã hủy dịch chuyển vì bạn nhận sát thương.");
        map.put("homes.error.cancelled-quit", "Đã hủy dịch chuyển vì bạn rời server.");
        map.put("homes.error.cancelled-death", "Đã hủy dịch chuyển vì bạn chết.");
        map.put("homes.error.cancelled-teleport", "Một plugin khác đã hủy warmup bằng teleport.");
        map.put("homes.error.cancelled-new-teleport", "Warmup trước đã được thay thế.");
        map.put("homes.error.cancelled-point-changed", "Điểm đến thay đổi nên dịch chuyển đã bị hủy.");
        map.put("homes.error.cancelled-team-changed", "Thành viên team thay đổi nên dịch chuyển đã bị hủy.");
        map.put("homes.error.cancelled-disable", "Homes đang tắt nên dịch chuyển đã bị hủy.");
        map.put("homes.error.team-changed", "Bạn không còn thuộc team của điểm đến.");
        map.put("homes.confirm.home-overwrite", "Chạy /team home set confirm để ghi đè Home hiện tại.");
        map.put("homes.confirm.home-delete", "Chạy /team home delete confirm để xóa Team Home.");
        map.put("homes.confirm.warp-update", "Chạy /team warp update {name} confirm để đổi vị trí.");
        map.put("homes.confirm.warp-delete", "Chạy /team warp delete {name} confirm để xóa.");
        map.put("homes.command.home", "Dùng /team home [info|set|delete]");
        map.put("homes.command.warp", "Dùng /team warp list|teleport|info|create|update|rename|delete");
        map.put("homes.permission.home.teleport", "Dịch chuyển tới Team Home");
        map.put("homes.permission.home.set", "Đặt hoặc cập nhật Team Home");
        map.put("homes.permission.home.delete", "Xóa Team Home");
        map.put("homes.permission.warp.view", "Xem Team Warps");
        map.put("homes.permission.warp.teleport", "Dịch chuyển tới Team Warps");
        map.put("homes.permission.warp.create", "Tạo Team Warp");
        map.put("homes.permission.warp.update", "Cập nhật vị trí Team Warp");
        map.put("homes.permission.warp.rename", "Đổi tên Team Warp");
        map.put("homes.permission.warp.delete", "Xóa Team Warp");
        return map;
    }
}
