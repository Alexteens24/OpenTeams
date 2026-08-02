package me.alexisbinh.openteams.homes.teleport;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import me.alexisbinh.openteams.api.TeamId;
import me.alexisbinh.openteams.homes.domain.TeleportPoint;
import me.alexisbinh.openteams.homes.service.HomesResult;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class WarmupManager {
    private final Plugin plugin;
    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();

    public WarmupManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<HomesResult<Void>> begin(
            Player player, TeleportPoint point, Duration duration,
            Consumer<CompletableFuture<HomesResult<Void>>> completion) {
        cancel(player.getUniqueId(), "homes.error.cancelled-new-teleport");
        var future = new CompletableFuture<HomesResult<Void>>();
        var token = UUID.randomUUID();
        var session = new Session(token, player.getUniqueId(), point.teamId(), point.id(),
                point.version(), player.getLocation().clone(), future, null);
        sessions.put(player.getUniqueId(), session);
        var ticks = Math.max(1L, duration.toMillis() / 50L);
        var task = player.getScheduler().runDelayed(plugin, ignored -> {
            var current = sessions.get(player.getUniqueId());
            if (current == null || !current.token().equals(token)
                    || !sessions.remove(player.getUniqueId(), current)) return;
            completion.accept(future);
        }, () -> cancel(player.getUniqueId(), "homes.error.cancelled"), ticks);
        sessions.computeIfPresent(player.getUniqueId(), (ignored, current) ->
                current.token().equals(token) ? current.withTask(task) : current);
        return future;
    }

    public void succeed(UUID playerId) {
        // The scheduled task removes the session before the teleport pipeline continues.
    }

    public boolean moving(Player player, Location current, double threshold) {
        var session = sessions.get(player.getUniqueId());
        if (session == null) return false;
        var start = session.start();
        if (start.getWorld() != current.getWorld()) return true;
        var dx = start.getX() - current.getX();
        var dy = start.getY() - current.getY();
        var dz = start.getZ() - current.getZ();
        return dx * dx + dy * dy + dz * dz > threshold * threshold;
    }

    public boolean active(UUID playerId) { return sessions.containsKey(playerId); }

    public void cancel(UUID playerId, String messageKey) {
        var session = sessions.remove(playerId);
        if (session == null) return;
        if (session.task() != null) session.task().cancel();
        session.future().complete(new HomesResult.Failure<>(HomesResult.Code.CANCELLED, messageKey));
    }

    public void cancelPoint(UUID pointId) {
        sessions.values().stream().filter(session -> session.pointId().equals(pointId))
                .map(Session::playerId).toList()
                .forEach(id -> cancel(id, "homes.error.cancelled-point-changed"));
    }

    public void cancelTeam(TeamId teamId) {
        sessions.values().stream().filter(session -> session.teamId().equals(teamId))
                .map(Session::playerId).toList()
                .forEach(id -> cancel(id, "homes.error.cancelled-team-changed"));
    }

    public void cancelAll() {
        sessions.keySet().forEach(id -> cancel(id, "homes.error.cancelled-disable"));
    }

    private record Session(UUID token, UUID playerId, TeamId teamId, UUID pointId, long version,
                           Location start, CompletableFuture<HomesResult<Void>> future,
                           ScheduledTask task) {
        Session withTask(ScheduledTask newTask) {
            return new Session(token, playerId, teamId, pointId, version, start, future, newTask);
        }
    }
}
