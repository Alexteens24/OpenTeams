package me.alexisbinh.openteams.homes.teleport;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import me.alexisbinh.openteams.api.TeamSnapshot;
import me.alexisbinh.openteams.homes.config.HomesConfig;
import me.alexisbinh.openteams.homes.domain.PointType;
import me.alexisbinh.openteams.homes.domain.TeleportPoint;
import me.alexisbinh.openteams.homes.service.HomesResult;
import me.alexisbinh.openteams.homes.service.MembershipAccess;
import me.alexisbinh.openteams.homes.service.PointService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class TeleportEngine {
    private final Plugin plugin;
    private final HomesConfig config;
    private final MembershipAccess access;
    private final PointService points;
    private final WarmupManager warmups;
    private final CooldownManager cooldowns;
    private final SafeLocationResolver safety;
    private final BooleanSupplier writable;

    public TeleportEngine(Plugin plugin, HomesConfig config, MembershipAccess access,
                          PointService points, WarmupManager warmups,
                          CooldownManager cooldowns, SafeLocationResolver safety,
                          BooleanSupplier writable) {
        this.plugin = plugin;
        this.config = config;
        this.access = access;
        this.points = points;
        this.warmups = warmups;
        this.cooldowns = cooldowns;
        this.safety = safety;
        this.writable = writable;
    }

    public CompletionStage<HomesResult<Void>> home(Player player) {
        if (!config.homeEnabled()) return failure(HomesResult.Code.FEATURE_DISABLED,
                "homes.error.feature-disabled");
        return resolve(player, PointType.HOME, "",
                "openteams-homes:home.teleport");
    }

    public CompletionStage<HomesResult<Void>> warp(Player player, String name) {
        if (!config.warpsEnabled()) return failure(HomesResult.Code.FEATURE_DISABLED,
                "homes.error.feature-disabled");
        return resolve(player, PointType.WARP, name,
                "openteams-homes:warp.teleport");
    }

    private CompletionStage<HomesResult<Void>> resolve(
            Player player, PointType type, String name, String permission) {
        if (!writable.getAsBoolean()) return failure(HomesResult.Code.NOT_READY,
                "homes.error.not-ready");
        return access.require(player.getUniqueId(), permission).thenCompose(accessResult -> {
            if (accessResult instanceof HomesResult.Failure<TeamSnapshot> failure) {
                return CompletableFuture.completedFuture(copyFailure(failure));
            }
            var team = ((HomesResult.Success<TeamSnapshot>) accessResult).value();
            var lookup = type == PointType.HOME ? points.home(team.id()) : points.warp(team.id(), name);
            return lookup.thenCompose(pointResult -> {
                if (pointResult instanceof HomesResult.Failure<Optional<TeleportPoint>> failure) {
                    return CompletableFuture.completedFuture(copyFailure(failure));
                }
                var optional = ((HomesResult.Success<Optional<TeleportPoint>>) pointResult).value();
                if (optional.isEmpty()) return failure(HomesResult.Code.NOT_FOUND,
                        type == PointType.HOME ? "homes.error.home-not-set" : "homes.error.warp-not-found");
                return begin(player, optional.get(), permission);
            });
        });
    }

    private CompletionStage<HomesResult<Void>> begin(
            Player player, TeleportPoint point, String permission) {
        if (!point.location().serverId().equals(config.serverId())) {
            return failure(HomesResult.Code.DIFFERENT_SERVER, "homes.error.different-server");
        }
        if (!player.hasPermission("openteams.homes.bypass.cooldown")) {
            var remaining = cooldowns.remaining(player.getUniqueId(), point.type());
            if (remaining.isPresent()) return CompletableFuture.completedFuture(
                    new HomesResult.Failure<>(HomesResult.Code.COOLDOWN, "homes.error.cooldown",
                            Map.of("seconds", Long.toString(Math.max(1, remaining.get().toSeconds())))));
        }
        var duration = player.hasPermission("openteams.homes.bypass.warmup")
                ? Duration.ZERO : point.type() == PointType.HOME
                ? config.warmup().home() : config.warmup().warp();
        var started = new CompletableFuture<HomesResult<Void>>();
        player.getScheduler().run(plugin, ignored -> {
            if (!player.isOnline()) {
                started.complete(new HomesResult.Failure<>(HomesResult.Code.CANCELLED,
                        "homes.error.cancelled"));
                return;
            }
            if (!duration.isZero()) {
                player.sendMessage(Component.text("Teleporting in " + duration.toSeconds()
                        + " seconds. Do not move.", NamedTextColor.YELLOW));
            }
            var future = warmups.begin(player, point, duration,
                    completion -> finish(player, point, permission, completion));
            future.whenComplete((result, failure) -> {
                if (failure != null) started.completeExceptionally(failure);
                else started.complete(result);
            });
        }, () -> started.complete(new HomesResult.Failure<>(HomesResult.Code.CANCELLED,
                "homes.error.cancelled")));
        return started;
    }

    private void finish(Player player, TeleportPoint original, String permission,
                        CompletableFuture<HomesResult<Void>> completion) {
        access.require(player.getUniqueId(), permission).thenCompose(accessResult -> {
            if (accessResult instanceof HomesResult.Failure<TeamSnapshot> failure) {
                return CompletableFuture.<HomesResult<Void>>completedFuture(copyFailure(failure));
            }
            var team = ((HomesResult.Success<TeamSnapshot>) accessResult).value();
            if (!team.id().equals(original.teamId())) {
                return failure(HomesResult.Code.NO_TEAM, "homes.error.team-changed");
            }
            return points.fresh(original.id()).thenCompose(freshResult -> {
                if (freshResult instanceof HomesResult.Failure<Optional<TeleportPoint>> failure) {
                    return CompletableFuture.completedFuture(copyFailure(failure));
                }
                var fresh = ((HomesResult.Success<Optional<TeleportPoint>>) freshResult).value();
                if (fresh.isEmpty() || fresh.get().version() != original.version()
                        || !fresh.get().teamId().equals(team.id())) {
                    return failure(HomesResult.Code.CONFLICT,
                            "homes.error.cancelled-point-changed");
                }
                return teleport(player, fresh.get());
            });
        }).whenComplete((result, failure) -> {
            if (failure != null) completion.completeExceptionally(failure);
            else completion.complete(result);
        });
    }

    private CompletionStage<HomesResult<Void>> teleport(Player player, TeleportPoint point) {
        var answer = new CompletableFuture<HomesResult<Void>>();
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            var world = Bukkit.getWorld(point.location().worldId());
            if (world == null) {
                answer.complete(new HomesResult.Failure<>(HomesResult.Code.UNSAFE,
                        "homes.error.world-missing"));
                return;
            }
            safety.resolve(world, point.location(),
                    player.hasPermission("openteams.homes.bypass.safety"))
                    .whenComplete((safe, failure) -> {
                        if (failure != null || safe.isEmpty()) {
                            answer.complete(new HomesResult.Failure<>(HomesResult.Code.UNSAFE,
                                    "homes.error.unsafe"));
                            return;
                        }
                        teleportPlayer(player, point, safe.get(), answer);
                    });
        });
        return answer;
    }

    private void teleportPlayer(Player player, TeleportPoint point, Location destination,
                                CompletableFuture<HomesResult<Void>> answer) {
        player.getScheduler().run(plugin, ignored -> player.teleportAsync(destination,
                        PlayerTeleportEvent.TeleportCause.PLUGIN)
                .whenComplete((success, failure) -> {
                    if (failure != null || !Boolean.TRUE.equals(success)) {
                        answer.complete(new HomesResult.Failure<>(HomesResult.Code.TELEPORT_FAILED,
                                "homes.error.teleport-failed"));
                    } else {
                        if (!player.hasPermission("openteams.homes.bypass.cooldown")) {
                            cooldowns.apply(player.getUniqueId(), point.type());
                        }
                        answer.complete(new HomesResult.Success<>(null));
                    }
                }), () -> answer.complete(new HomesResult.Failure<>(HomesResult.Code.CANCELLED,
                        "homes.error.cancelled")));
    }

    private static <T> CompletionStage<HomesResult<T>> failure(
            HomesResult.Code code, String key) {
        return CompletableFuture.completedFuture(new HomesResult.Failure<>(code, key));
    }

    private static <T, U> HomesResult.Failure<U> copyFailure(HomesResult.Failure<T> failure) {
        return new HomesResult.Failure<>(failure.code(), failure.messageKey(), failure.arguments());
    }
}
