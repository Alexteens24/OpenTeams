package me.alexisbinh.openteams.homes.command;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import me.alexisbinh.openteams.api.TeamSnapshot;
import me.alexisbinh.openteams.homes.config.HomesConfig;
import me.alexisbinh.openteams.homes.domain.PointPage;
import me.alexisbinh.openteams.homes.domain.StoredLocation;
import me.alexisbinh.openteams.homes.domain.TeleportPoint;
import me.alexisbinh.openteams.homes.service.HomesResult;
import me.alexisbinh.openteams.homes.service.MembershipAccess;
import me.alexisbinh.openteams.homes.service.PointService;
import me.alexisbinh.openteams.homes.teleport.TeleportEngine;
import me.alexisbinh.openteams.homes.teleport.WarmupManager;
import me.alexisbinh.openteams.homes.ui.HomesMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class HomesCommands {
    private static final String HOME_TELEPORT = "openteams-homes:home.teleport";
    private static final String HOME_SET = "openteams-homes:home.set";
    private static final String HOME_DELETE = "openteams-homes:home.delete";
    private static final String WARP_VIEW = "openteams-homes:warp.view";
    private static final String WARP_CREATE = "openteams-homes:warp.create";
    private static final String WARP_UPDATE = "openteams-homes:warp.update";
    private static final String WARP_RENAME = "openteams-homes:warp.rename";
    private static final String WARP_DELETE = "openteams-homes:warp.delete";

    private final Plugin plugin;
    private final HomesConfig config;
    private final MembershipAccess access;
    private final PointService points;
    private final TeleportEngine teleports;
    private final WarmupManager warmups;
    private final HomesMessages messages;

    public HomesCommands(Plugin plugin, HomesConfig config, MembershipAccess access,
                         PointService points, TeleportEngine teleports,
                         WarmupManager warmups, HomesMessages messages) {
        this.plugin = plugin;
        this.config = config;
        this.access = access;
        this.points = points;
        this.teleports = teleports;
        this.warmups = warmups;
        this.messages = messages;
    }

    public CompletionStage<Integer> home(CommandSender sender, String[] arguments) {
        var player = player(sender);
        if (player == null) return done(0);
        if (arguments.length == 0) {
            render(player, teleports.home(player), ignored ->
                    player.sendMessage(messages.component(player, "homes.success.teleported")));
            return done(1);
        }
        switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
            case "info" -> withTeam(player, HOME_TELEPORT, team -> points.home(team.id())
                    .thenAccept(result -> dispatch(player, () -> showPoint(player, result))));
            case "set" -> setHome(player, arguments.length > 1
                    && arguments[1].equalsIgnoreCase("confirm"));
            case "delete" -> deleteHome(player, arguments.length > 1
                    && arguments[1].equalsIgnoreCase("confirm"));
            default -> player.sendMessage(messages.component(player, "homes.command.home"));
        }
        return done(1);
    }

    public CompletionStage<Integer> warp(CommandSender sender, String[] arguments) {
        var player = player(sender);
        if (player == null) return done(0);
        if (arguments.length == 0 || arguments[0].equalsIgnoreCase("list")) {
            list(player, arguments);
            return done(1);
        }
        var action = arguments[0].toLowerCase(java.util.Locale.ROOT);
        switch (action) {
            case "teleport" -> {
                if (arguments.length < 2) usage(player);
                else render(player, teleports.warp(player, arguments[1]), ignored ->
                        player.sendMessage(messages.component(player, "homes.success.teleported")));
            }
            case "info" -> findWarp(player, WARP_VIEW, arguments, point -> showPoint(player,
                    new HomesResult.Success<>(Optional.of(point))));
            case "create" -> createWarp(player, arguments);
            case "update" -> updateWarp(player, arguments);
            case "rename" -> renameWarp(player, arguments);
            case "delete" -> deleteWarp(player, arguments);
            default -> usage(player);
        }
        return done(1);
    }

    private void setHome(Player player, boolean confirmed) {
        withTeam(player, HOME_SET, team -> capture(player).thenAccept(location ->
                points.home(team.id()).thenAccept(lookup -> {
                    if (lookup instanceof HomesResult.Failure<Optional<TeleportPoint>> failure) {
                        dispatch(player, () -> failure(player, failure));
                        return;
                    }
                    var existing = ((HomesResult.Success<Optional<TeleportPoint>>) lookup).value();
                    if (existing.isPresent() && !confirmed) {
                        dispatch(player, () -> player.sendMessage(messages.component(player,
                                "homes.confirm.home-overwrite")));
                        return;
                    }
                    var version = existing.isPresent()
                            ? OptionalLong.of(existing.get().version()) : OptionalLong.empty();
                    render(player, points.setHome(team.id(), location, player.getUniqueId(), version),
                            point -> {
                                warmups.cancelPoint(point.id());
                                player.sendMessage(messages.component(player,
                                        "homes.success.home-set"));
                            });
                })));
    }

    private void deleteHome(Player player, boolean confirmed) {
        withTeam(player, HOME_DELETE, team -> points.home(team.id()).thenAccept(result -> {
            if (result instanceof HomesResult.Failure<Optional<TeleportPoint>> failure) {
                dispatch(player, () -> failure(player, failure)); return;
            }
            var point = ((HomesResult.Success<Optional<TeleportPoint>>) result).value();
            if (point.isEmpty()) { dispatch(player, () -> player.sendMessage(
                    messages.component(player, "homes.error.home-not-set"))); return; }
            if (!confirmed) { dispatch(player, () -> player.sendMessage(
                    messages.component(player, "homes.confirm.home-delete"))); return; }
            render(player, points.delete(point.get()), ignored -> {
                warmups.cancelPoint(point.get().id());
                player.sendMessage(messages.component(player, "homes.success.deleted"));
            });
        }));
    }

    private void list(Player player, String[] arguments) {
        var query = arguments.length > 1 && !arguments[1].equals("*") ? arguments[1] : "";
        var page = arguments.length > 2 ? parsePage(arguments[2]) : 0;
        withTeam(player, WARP_VIEW, team -> points.warps(team.id(), query, page)
                .thenAccept(result -> dispatch(player, () -> showPage(player, result, query))));
    }

    private void createWarp(Player player, String[] arguments) {
        if (arguments.length < 2) { usage(player); return; }
        withTeam(player, WARP_CREATE, team -> capture(player).thenAccept(location ->
                render(player, points.createWarp(team.id(), arguments[1], location,
                        player.getUniqueId()), point -> player.sendMessage(messages.component(player,
                        "homes.success.warp-created", Map.of("name", point.displayName()))))));
    }

    private void updateWarp(Player player, String[] arguments) {
        if (arguments.length < 2) { usage(player); return; }
        var confirmed = arguments.length > 2 && arguments[2].equalsIgnoreCase("confirm");
        findWarp(player, WARP_UPDATE, arguments, point -> {
            if (!confirmed) {
                player.sendMessage(messages.component(player, "homes.confirm.warp-update",
                        Map.of("name", point.displayName()))); return;
            }
            capture(player).thenAccept(location -> render(player,
                    points.updateLocation(point, location), updated -> {
                        warmups.cancelPoint(point.id());
                        player.sendMessage(messages.component(player,
                                "homes.success.warp-updated"));
                    }));
        });
    }

    private void renameWarp(Player player, String[] arguments) {
        if (arguments.length < 3) { usage(player); return; }
        findWarp(player, WARP_RENAME, arguments, point -> render(player,
                points.rename(point, arguments[2]), renamed -> {
                    warmups.cancelPoint(point.id());
                    player.sendMessage(messages.component(player, "homes.success.warp-renamed",
                            Map.of("name", renamed.displayName())));
                }));
    }

    private void deleteWarp(Player player, String[] arguments) {
        if (arguments.length < 2) { usage(player); return; }
        var confirmed = arguments.length > 2 && arguments[2].equalsIgnoreCase("confirm");
        findWarp(player, WARP_DELETE, arguments, point -> {
            if (!confirmed) {
                player.sendMessage(messages.component(player, "homes.confirm.warp-delete",
                        Map.of("name", point.displayName()))); return;
            }
            render(player, points.delete(point), ignored -> {
                warmups.cancelPoint(point.id());
                player.sendMessage(messages.component(player, "homes.success.deleted"));
            });
        });
    }

    private void findWarp(Player player, String permission, String[] arguments,
                          Consumer<TeleportPoint> consumer) {
        if (arguments.length < 2) { usage(player); return; }
        withTeam(player, permission, team -> points.warp(team.id(), arguments[1])
                .thenAccept(result -> dispatch(player, () -> {
                    if (result instanceof HomesResult.Failure<Optional<TeleportPoint>> failure) {
                        failure(player, failure); return;
                    }
                    var point = ((HomesResult.Success<Optional<TeleportPoint>>) result).value();
                    if (point.isEmpty()) player.sendMessage(messages.component(player,
                            "homes.error.warp-not-found"));
                    else consumer.accept(point.get());
                })));
    }

    private void withTeam(Player player, String permission, Consumer<TeamSnapshot> consumer) {
        access.require(player.getUniqueId(), permission).whenComplete((result, exception) -> {
            if (exception != null) { dispatch(player, () -> player.sendMessage(
                    messages.component(player, "homes.error.database"))); return; }
            if (result instanceof HomesResult.Failure<TeamSnapshot> denied) {
                dispatch(player, () -> failure(player, denied)); return;
            }
            consumer.accept(((HomesResult.Success<TeamSnapshot>) result).value());
        });
    }

    private CompletableFuture<StoredLocation> capture(Player player) {
        var future = new CompletableFuture<StoredLocation>();
        player.getScheduler().run(plugin, ignored -> {
            var location = player.getLocation();
            future.complete(new StoredLocation(config.serverId(), location.getWorld().getUID(),
                    location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch()));
        }, () -> future.completeExceptionally(new IllegalStateException("Player retired")));
        return future;
    }

    private <T> void render(Player player, CompletionStage<HomesResult<T>> stage,
                            Consumer<T> success) {
        stage.whenComplete((result, exception) -> dispatch(player, () -> {
            if (exception != null) player.sendMessage(messages.component(player,
                    "homes.error.database"));
            else if (result instanceof HomesResult.Failure<T> failed) failure(player, failed);
            else success.accept(((HomesResult.Success<T>) result).value());
        }));
    }

    private void showPoint(Player player, HomesResult<Optional<TeleportPoint>> result) {
        if (result instanceof HomesResult.Failure<Optional<TeleportPoint>> failed) {
            failure(player, failed); return;
        }
        var optional = ((HomesResult.Success<Optional<TeleportPoint>>) result).value();
        if (optional.isEmpty()) {
            player.sendMessage(messages.component(player, "homes.error.home-not-set")); return;
        }
        var point = optional.get();
        var location = point.location();
        player.sendMessage(Component.text(point.displayName(), NamedTextColor.AQUA)
                .append(Component.text(" · " + location.worldName() + " · "
                        + format(location.x()) + ", " + format(location.y()) + ", "
                        + format(location.z()) + " · v" + point.version(), NamedTextColor.GRAY)));
    }

    private void showPage(Player player, HomesResult<PointPage> result, String query) {
        if (result instanceof HomesResult.Failure<PointPage> failed) { failure(player, failed); return; }
        var page = ((HomesResult.Success<PointPage>) result).value();
        player.sendMessage(Component.text("Team Warps · " + page.total() + "/"
                + config.maximumWarps() + " · " + (page.page() + 1) + "/" + page.pages(),
                NamedTextColor.AQUA));
        if (page.entries().isEmpty()) player.sendMessage(messages.component(player,
                "homes.ui.warps-empty"));
        page.entries().forEach(point -> player.sendMessage(Component.text("• "
                        + point.displayName(), NamedTextColor.WHITE)
                .clickEvent(ClickEvent.runCommand("/team warp teleport " + point.displayName()))));
        var navigation = Component.empty();
        if (page.page() > 0) navigation = navigation.append(Component.text("← Previous ",
                        NamedTextColor.YELLOW).clickEvent(ClickEvent.runCommand("/team warp list "
                        + (query.isBlank() ? "*" : query) + " " + page.page())));
        if (page.page() + 1 < page.pages()) navigation = navigation.append(Component.text("Next →",
                        NamedTextColor.YELLOW).clickEvent(ClickEvent.runCommand("/team warp list "
                        + (query.isBlank() ? "*" : query) + " " + (page.page() + 2))));
        if (!navigation.equals(Component.empty())) player.sendMessage(navigation);
    }

    private void failure(Player player, HomesResult.Failure<?> failure) {
        player.sendMessage(messages.component(player, failure.messageKey(), failure.arguments())
                .color(NamedTextColor.RED));
    }

    private void dispatch(Player player, Runnable action) {
        player.getScheduler().run(plugin, ignored -> action.run(), null);
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) return player;
        sender.sendMessage(Component.text("This command requires a player.", NamedTextColor.RED));
        return null;
    }

    private void usage(Player player) { player.sendMessage(messages.component(player,
            "homes.command.warp")); }
    private static int parsePage(String value) {
        try { return Math.max(0, Integer.parseInt(value) - 1); }
        catch (NumberFormatException ignored) { return 0; }
    }
    private static String format(double value) { return String.format(java.util.Locale.ROOT,
            "%.1f", value); }
    private static CompletionStage<Integer> done(int value) {
        return CompletableFuture.completedFuture(value);
    }
}
