package me.alexisbinh.openteams.homes.ui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import me.alexisbinh.openteams.api.OpenTeams;
import me.alexisbinh.openteams.api.TeamSnapshot;
import me.alexisbinh.openteams.homes.config.HomesConfig;
import me.alexisbinh.openteams.homes.domain.PointPage;
import me.alexisbinh.openteams.homes.domain.TeleportPoint;
import me.alexisbinh.openteams.homes.domain.PointType;
import me.alexisbinh.openteams.homes.service.HomesResult;
import me.alexisbinh.openteams.homes.service.MembershipAccess;
import me.alexisbinh.openteams.homes.service.PointService;
import me.alexisbinh.openteams.homes.domain.StoredLocation;
import me.alexisbinh.openteams.homes.teleport.WarmupManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class HomesDialogs {
    private final Plugin plugin;
    private final OpenTeams api;
    private final HomesConfig config;
    private final MembershipAccess access;
    private final PointService points;
    private final HomesMessages messages;
    private final WarmupManager warmups;

    public HomesDialogs(Plugin plugin, OpenTeams api, HomesConfig config,
                        MembershipAccess access, PointService points, HomesMessages messages,
                        WarmupManager warmups) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
        this.access = access;
        this.points = points;
        this.messages = messages;
        this.warmups = warmups;
    }

    public void openHome(Player player) {
        show(player, loading(player));
        access.require(player.getUniqueId(), "openteams-homes:home.teleport")
                .thenCompose(result -> {
                    if (result instanceof HomesResult.Failure<TeamSnapshot> failure) {
                        return java.util.concurrent.CompletableFuture
                                .completedFuture(HomesResult.copyFailure(failure));
                    }
                    return points.home(((HomesResult.Success<TeamSnapshot>) result).value().id());
                }).whenComplete((result, failure) -> dispatch(player, () -> {
                    if (failure != null) show(player, error(player, "homes.error.database",
                            () -> openHome(player)));
                    else if (result instanceof HomesResult.Failure<Optional<TeleportPoint>> failed) {
                        show(player, error(player, failed.messageKey(), () -> openHome(player)));
                    } else show(player, home(player,
                            ((HomesResult.Success<Optional<TeleportPoint>>) result).value()));
                }));
    }

    public void openWarps(Player player, String query, int page) {
        show(player, loading(player));
        access.require(player.getUniqueId(), "openteams-homes:warp.view")
                .thenCompose(result -> {
                    if (result instanceof HomesResult.Failure<TeamSnapshot> failure) {
                        return java.util.concurrent.CompletableFuture
                                .completedFuture(HomesResult.copyFailure(failure));
                    }
                    return points.warps(((HomesResult.Success<TeamSnapshot>) result).value().id(),
                            query, page);
                }).whenComplete((result, failure) -> dispatch(player, () -> {
                    if (failure != null) show(player, error(player, "homes.error.database",
                            () -> openWarps(player, query, page)));
                    else if (result instanceof HomesResult.Failure<PointPage> failed) {
                        show(player, error(player, failed.messageKey(),
                                () -> openWarps(player, query, page)));
                    } else show(player, warps(player,
                            ((HomesResult.Success<PointPage>) result).value(), query));
                }));
    }

    private Dialog home(Player player, Optional<TeleportPoint> point) {
        var actions = new ArrayList<ActionButton>();
        Component body;
        if (point.isPresent()) {
            var value = point.get();
            body = pointDescription(value);
            actions.add(command(player, "homes.ui.teleport", "/team home"));
            if (allowed(player, "openteams-homes:home.set")) {
                actions.add(confirm(player, "homes.ui.set", body,
                        () -> setHome(player, point), () -> openHome(player)));
            }
            if (allowed(player, "openteams-homes:home.delete")) {
                actions.add(confirm(player, "homes.ui.delete", body,
                        () -> deletePoint(player, point.get(), () -> openHome(player)),
                        () -> openHome(player)));
            }
        } else {
            body = messages.component(player, "homes.ui.home-empty");
            if (allowed(player, "openteams-homes:home.set")) {
                actions.add(raw(messages.component(player, "homes.ui.set"), Component.empty(),
                        response -> setHome(player, Optional.empty())));
            }
        }
        actions.add(back(player));
        return multi(player, "homes.ui.home", body, List.of(), actions, 2);
    }

    private Dialog warps(Player player, PointPage page, String query) {
        var actions = new ArrayList<ActionButton>();
        page.entries().forEach(point -> actions.add(raw(point.displayName(), pointDescription(point),
                response -> show(player, detail(player, point, query, page.page())))));
        if (allowed(player, "openteams-homes:warp.create")) {
            actions.add(raw(messages.component(player, "homes.ui.create"), Component.empty(),
                    response -> show(player, createForm(player, query, page.page(), "", null))));
        }
        if (page.page() > 0) actions.add(raw(messages.component(player, "homes.ui.previous"),
                Component.empty(), response -> openWarps(player, query, page.page() - 1)));
        if (page.page() + 1 < page.pages()) actions.add(raw(
                messages.component(player, "homes.ui.next"), Component.empty(),
                response -> openWarps(player, query, page.page() + 1)));
        actions.add(back(player));
        var search = DialogInput.text("query", messages.component(player, "homes.ui.search"))
                .width(300).maxLength(24).initial(query).build();
        actions.add(raw(messages.component(player, "homes.ui.search"), Component.empty(),
                response -> openWarps(player, value(response.getText("query")), 0)));
        var body = Component.text(page.total() + "/" + config.maximumWarps()
                + " · " + (page.page() + 1) + "/" + page.pages());
        if (page.entries().isEmpty()) body = body.append(Component.newline())
                .append(messages.component(player, "homes.ui.warps-empty"));
        return multi(player, "homes.ui.warps", body, List.of(search), actions, 2);
    }

    private Dialog detail(Player player, TeleportPoint point, String query, int page) {
        var actions = new ArrayList<ActionButton>();
        actions.add(command(player, "homes.ui.teleport",
                "/team warp teleport " + point.displayName()));
        if (allowed(player, "openteams-homes:warp.update")) {
            actions.add(confirm(player, "homes.ui.set", pointDescription(point),
                    () -> updateWarp(player, point, query, page),
                    () -> openWarps(player, query, page)));
        }
        if (allowed(player, "openteams-homes:warp.rename")) {
            actions.add(raw(messages.component(player, "homes.ui.rename"), Component.empty(),
                    response -> show(player, renameForm(player, point, query, page,
                            point.displayName(), null))));
        }
        if (allowed(player, "openteams-homes:warp.delete")) {
            actions.add(confirm(player, "homes.ui.delete", pointDescription(point),
                    () -> deletePoint(player, point, () -> openWarps(player, query, page)),
                    () -> openWarps(player, query, page)));
        }
        actions.add(raw(messages.component(player, "homes.ui.back"), Component.empty(),
                response -> openWarps(player, query, page)));
        return multi(player, point.displayName(), pointDescription(point), List.of(), actions, 2);
    }

    private Dialog createForm(Player player, String query, int page, String initial,
                              String errorKey) {
        var input = DialogInput.text("name", Component.text("Warp name"))
                .width(300).maxLength(config.maximumNameLength()).initial(initial).build();
        return Dialog.create(factory -> factory.empty()
                .base(base(player, "homes.ui.create", formBody(player, errorKey),
                        List.of(input)))
                .type(DialogType.confirmation(
                        raw(messages.component(player, "homes.ui.create"), Component.empty(),
                                response -> {
                                    var name = value(response.getText("name"));
                                    if (name.isBlank()) {
                                        show(player, createForm(player, query, page, name,
                                                "homes.error.invalid-name"));
                                    } else {
                                        createWarp(player, name, query, page);
                                    }
                                }),
                        raw(messages.component(player, "homes.ui.back"), Component.empty(),
                                response -> openWarps(player, query, page)))));
    }

    private Dialog renameForm(Player player, TeleportPoint point, String query, int page,
                              String initial, String errorKey) {
        var input = DialogInput.text("name", Component.text("New warp name"))
                .width(300).maxLength(config.maximumNameLength()).initial(initial).build();
        return Dialog.create(factory -> factory.empty()
                .base(base(player, "homes.ui.rename", formBody(player, errorKey),
                        List.of(input)))
                .type(DialogType.confirmation(
                        raw(messages.component(player, "homes.ui.rename"), Component.empty(),
                                response -> {
                                    var name = value(response.getText("name"));
                                    if (name.isBlank()) {
                                        show(player, renameForm(player, point, query, page, name,
                                                "homes.error.invalid-name"));
                                    } else {
                                        renameWarp(player, point, name, query, page);
                                    }
                                }),
                        raw(messages.component(player, "homes.ui.back"), Component.empty(),
                                response -> show(player, detail(player, point, query, page))))));
    }

    private void setHome(Player player, Optional<TeleportPoint> current) {
        show(player, loading(player));
        access.require(player.getUniqueId(), "openteams-homes:home.set")
                .thenCompose(result -> {
                    if (result instanceof HomesResult.Failure<TeamSnapshot> failure) {
                        return CompletableFuture.<HomesResult<TeleportPoint>>completedFuture(
                                HomesResult.copyFailure(failure));
                    }
                    var team = ((HomesResult.Success<TeamSnapshot>) result).value();
                    return capture(player).thenCompose(location -> points.setHome(team.id(), location,
                            player.getUniqueId(), current.isPresent()
                                    ? OptionalLong.of(current.get().version())
                                    : OptionalLong.empty()));
                }).whenComplete((result, failure) -> dispatch(player, () -> {
                    if (failure != null) {
                        show(player, error(player, "homes.error.database",
                                () -> setHome(player, current))); return;
                    }
                    if (result instanceof HomesResult.Failure<TeleportPoint> failed) {
                        show(player, error(player, failed.messageKey(), () -> openHome(player)));
                        return;
                    }
                    var saved = ((HomesResult.Success<TeleportPoint>) result).value();
                    warmups.cancelPoint(saved.id());
                    player.sendMessage(messages.component(player, "homes.success.home-set"));
                    openHome(player);
                }));
    }

    private void createWarp(Player player, String name, String query, int page) {
        show(player, loading(player));
        access.require(player.getUniqueId(), "openteams-homes:warp.create")
                .thenCompose(result -> {
                    if (result instanceof HomesResult.Failure<TeamSnapshot> failure) {
                        return CompletableFuture.<HomesResult<TeleportPoint>>completedFuture(
                                HomesResult.copyFailure(failure));
                    }
                    var team = ((HomesResult.Success<TeamSnapshot>) result).value();
                    return capture(player).thenCompose(location -> points.createWarp(team.id(), name,
                            location, player.getUniqueId()));
                }).whenComplete((result, failure) -> dispatch(player, () -> {
                    if (failure != null) {
                        show(player, error(player, "homes.error.database",
                                () -> show(player, createForm(player, query, page, name, null))));
                        return;
                    }
                    if (result instanceof HomesResult.Failure<TeleportPoint> failed) {
                        if (failed.code() == HomesResult.Code.INVALID_NAME
                                || failed.code() == HomesResult.Code.RESERVED_NAME
                                || failed.code() == HomesResult.Code.DUPLICATE_NAME
                                || failed.code() == HomesResult.Code.LIMIT_REACHED) {
                            show(player, createForm(player, query, page, name, failed.messageKey()));
                        } else show(player, error(player, failed.messageKey(),
                                () -> show(player, createForm(player, query, page, name, null))));
                        return;
                    }
                    var created = ((HomesResult.Success<TeleportPoint>) result).value();
                    player.sendMessage(messages.component(player, "homes.success.warp-created",
                            Map.of("name", created.displayName())));
                    openWarps(player, query, page);
                }));
    }

    private void updateWarp(Player player, TeleportPoint point, String query, int page) {
        show(player, loading(player));
        access.require(player.getUniqueId(), "openteams-homes:warp.update")
                .thenCompose(result -> {
                    if (result instanceof HomesResult.Failure<TeamSnapshot> failure) {
                        return CompletableFuture.<HomesResult<TeleportPoint>>completedFuture(
                                HomesResult.copyFailure(failure));
                    }
                    var team = ((HomesResult.Success<TeamSnapshot>) result).value();
                    if (!team.id().equals(point.teamId())) return CompletableFuture.completedFuture(
                            new HomesResult.Failure<>(HomesResult.Code.CONFLICT,
                                    "homes.error.conflict"));
                    return capture(player).thenCompose(location -> points.updateLocation(point,
                            location));
                }).whenComplete((result, failure) -> dispatch(player, () -> {
                    if (failure != null) {
                        show(player, error(player, "homes.error.database",
                                () -> openWarps(player, query, page))); return;
                    }
                    if (result instanceof HomesResult.Failure<TeleportPoint> failed) {
                        show(player, error(player, failed.messageKey(),
                                () -> openWarps(player, query, page))); return;
                    }
                    warmups.cancelPoint(point.id());
                    player.sendMessage(messages.component(player, "homes.success.warp-updated"));
                    openWarps(player, query, page);
                }));
    }

    private void renameWarp(Player player, TeleportPoint point, String name,
                            String query, int page) {
        show(player, loading(player));
        access.require(player.getUniqueId(), "openteams-homes:warp.rename")
                .thenCompose(result -> {
                    if (result instanceof HomesResult.Failure<TeamSnapshot> failure) {
                        return CompletableFuture.<HomesResult<TeleportPoint>>completedFuture(
                                HomesResult.copyFailure(failure));
                    }
                    var team = ((HomesResult.Success<TeamSnapshot>) result).value();
                    if (!team.id().equals(point.teamId())) return CompletableFuture.completedFuture(
                            new HomesResult.Failure<>(HomesResult.Code.CONFLICT,
                                    "homes.error.conflict"));
                    return points.rename(point, name);
                }).whenComplete((result, failure) -> dispatch(player, () -> {
                    if (failure != null) {
                        show(player, error(player, "homes.error.database", () -> show(player,
                                renameForm(player, point, query, page, name, null)))); return;
                    }
                    if (result instanceof HomesResult.Failure<TeleportPoint> failed) {
                        if (failed.code() == HomesResult.Code.INVALID_NAME
                                || failed.code() == HomesResult.Code.RESERVED_NAME
                                || failed.code() == HomesResult.Code.DUPLICATE_NAME) {
                            show(player, renameForm(player, point, query, page, name,
                                    failed.messageKey()));
                        } else show(player, error(player, failed.messageKey(),
                                () -> openWarps(player, query, page)));
                        return;
                    }
                    var renamed = ((HomesResult.Success<TeleportPoint>) result).value();
                    warmups.cancelPoint(point.id());
                    player.sendMessage(messages.component(player, "homes.success.warp-renamed",
                            Map.of("name", renamed.displayName())));
                    openWarps(player, query, page);
                }));
    }

    private void deletePoint(Player player, TeleportPoint point, Runnable success) {
        show(player, loading(player));
        var permission = point.type() == PointType.HOME
                ? "openteams-homes:home.delete" : "openteams-homes:warp.delete";
        access.require(player.getUniqueId(), permission).thenCompose(result -> {
            if (result instanceof HomesResult.Failure<TeamSnapshot> failure) {
                return CompletableFuture.<HomesResult<Void>>completedFuture(
                        HomesResult.copyFailure(failure));
            }
            var team = ((HomesResult.Success<TeamSnapshot>) result).value();
            if (!team.id().equals(point.teamId())) return CompletableFuture.completedFuture(
                    new HomesResult.Failure<>(HomesResult.Code.CONFLICT, "homes.error.conflict"));
            return points.delete(point);
        }).whenComplete((result, failure) -> dispatch(player, () -> {
            if (failure != null) {
                show(player, error(player, "homes.error.database", success)); return;
            }
            if (result instanceof HomesResult.Failure<Void> failed) {
                show(player, error(player, failed.messageKey(), success)); return;
            }
            warmups.cancelPoint(point.id());
            player.sendMessage(messages.component(player, "homes.success.deleted"));
            success.run();
        }));
    }

    private CompletableFuture<StoredLocation> capture(Player player) {
        var result = new CompletableFuture<StoredLocation>();
        player.getScheduler().run(plugin, ignored -> {
            var location = player.getLocation();
            result.complete(new StoredLocation(config.serverId(), location.getWorld().getUID(),
                    location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch()));
        }, () -> result.completeExceptionally(new IllegalStateException("Player retired")));
        return result;
    }

    private Component formBody(Player player, String errorKey) {
        var body = Component.text("A-Z, 0-9, _ or -");
        return errorKey == null ? body : messages.component(player, errorKey)
                .append(Component.newline()).append(body);
    }

    private ActionButton confirm(Player player, String labelKey, Component body,
                                 Runnable confirmed, Runnable back) {
        return raw(messages.component(player, labelKey), Component.empty(), response -> {
            var dialog = Dialog.create(factory -> factory.empty()
                    .base(base(player, labelKey, body, List.of()))
                    .type(DialogType.confirmation(
                            raw(messages.component(player, labelKey), Component.empty(), ignored -> {
                                confirmed.run();
                            }), raw(messages.component(player, "homes.ui.back"), Component.empty(),
                                    ignored -> back.run()))));
            show(player, dialog);
        });
    }

    private Dialog loading(Player player) {
        return Dialog.create(factory -> factory.empty()
                .base(base(player, "homes.ui.loading", messages.component(player,
                        "homes.ui.loading"), List.of()))
                .type(DialogType.notice(back(player))));
    }

    private Dialog error(Player player, String key, Runnable retry) {
        return multi(player, "Error", messages.component(player, key), List.of(), List.of(
                raw("Retry", Component.empty(), response -> retry.run()), back(player)), 2);
    }

    private Dialog multi(Player player, String title, Component body,
                         List<? extends DialogInput> inputs, List<ActionButton> actions, int columns) {
        return Dialog.create(factory -> factory.empty().base(base(player, title, body, inputs))
                .type(DialogType.multiAction(actions).columns(columns).build()));
    }

    private DialogBase base(Player player, String title, Component body,
                            List<? extends DialogInput> inputs) {
        return DialogBase.builder(messages.component(player, title))
                .body(List.of(DialogBody.plainMessage(body, 360))).inputs(inputs)
                .canCloseWithEscape(true).build();
    }

    private ActionButton back(Player player) {
        return raw(messages.component(player, "homes.ui.back"), Component.empty(), response -> {
            player.closeDialog();
            dispatchCommand(player, "/team");
        });
    }

    private ActionButton command(Player player, String labelKey, String command) {
        return raw(messages.component(player, labelKey), Component.empty(), response -> {
            player.closeDialog(); dispatchCommand(player, command);
        });
    }

    private static ActionButton raw(String label, Component tooltip,
                                    java.util.function.Consumer<io.papermc.paper.dialog.DialogResponseView> handler) {
        return raw(Component.text(label), tooltip, handler);
    }

    private static ActionButton raw(Component label, Component tooltip,
                                    java.util.function.Consumer<io.papermc.paper.dialog.DialogResponseView> handler) {
        return ActionButton.builder(label).tooltip(tooltip).width(150)
                .action(DialogAction.customClick((response, audience) -> handler.accept(response),
                        ClickCallback.Options.builder().uses(1).build())).build();
    }

    private Component pointDescription(TeleportPoint point) {
        var location = point.location();
        return Component.text(location.worldName() + " · " + decimal(location.x()) + ", "
                + decimal(location.y()) + ", " + decimal(location.z()) + " · v" + point.version()
                + "\nCreator: " + point.creatorId() + "\nCreated: " + point.createdAt()
                + "\nUpdated: " + point.updatedAt());
    }

    private boolean allowed(Player player, String permission) {
        return api.teams().hasPermissionCached(player.getUniqueId(), permission);
    }

    private void show(Player player, Dialog dialog) {
        dispatch(player, () -> player.showDialog(dialog));
    }

    private void dispatch(Player player, Runnable action) {
        player.getScheduler().run(plugin, ignored -> action.run(), null);
    }

    private static void dispatchCommand(Player player, String command) {
        Bukkit.dispatchCommand(player, command.startsWith("/") ? command.substring(1) : command);
    }

    private static String value(String value) { return value == null ? "" : value.strip(); }
    private static String decimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
