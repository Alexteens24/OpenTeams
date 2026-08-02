package me.alexisbinh.openteams.homes.lifecycle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import me.alexisbinh.openteams.api.OpenTeams;
import me.alexisbinh.openteams.api.TeamId;
import me.alexisbinh.openteams.api.TeamState;
import me.alexisbinh.openteams.homes.persistence.PointRepository;
import me.alexisbinh.openteams.homes.service.PointCache;
import org.bukkit.plugin.Plugin;

public final class CleanupService implements AutoCloseable {
    private final Plugin plugin;
    private final OpenTeams api;
    private final PointRepository repository;
    private final PointCache cache;
    private final Path spool;
    private final ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("OpenTeams-Homes-Cleanup", 0).factory());
    private final Set<TeamId> pending = new LinkedHashSet<>();

    public CleanupService(Plugin plugin, OpenTeams api, PointRepository repository,
                          PointCache cache, Path dataDirectory) throws IOException {
        this.plugin = plugin;
        this.api = api;
        this.repository = repository;
        this.cache = cache;
        this.spool = dataDirectory.resolve("pending-cleanups.txt");
        Files.createDirectories(dataDirectory);
        if (Files.exists(spool)) {
            for (var line : Files.readAllLines(spool, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    try { pending.add(TeamId.parse(line.strip())); }
                    catch (IllegalArgumentException ignored) { }
                }
            }
        }
    }

    public void start() {
        retry();
        plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin,
                task -> retry(), 1, 1, java.util.concurrent.TimeUnit.MINUTES);
        plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin,
                task -> scanOrphans(), 1, 24, java.util.concurrent.TimeUnit.HOURS);
    }

    public void enqueue(TeamId teamId) {
        worker.execute(() -> {
            synchronized (pending) { pending.add(teamId); persist(); }
            clean(teamId);
        });
    }

    public void retry() {
        worker.execute(() -> {
            TeamId[] snapshot;
            synchronized (pending) { snapshot = pending.toArray(TeamId[]::new); }
            for (var id : snapshot) clean(id);
        });
    }

    private void clean(TeamId teamId) {
        try {
            repository.deleteTeam(teamId);
            cache.invalidate(teamId);
            synchronized (pending) { pending.remove(teamId); persist(); }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Will retry cleanup for team " + teamId, exception);
        }
    }

    private void scanOrphans() {
        worker.execute(() -> {
            try {
                for (var teamId : repository.teamIds(100)) {
                    api.teams().find(teamId).whenComplete((team, failure) -> {
                        if (failure == null && (team.isEmpty()
                                || team.get().state() == TeamState.DISBANDED)) enqueue(teamId);
                    });
                }
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Homes orphan scan failed", exception);
            }
        });
    }

    private void persist() {
        try {
            var temporary = spool.resolveSibling(spool.getFileName() + ".tmp");
            var lines = pending.stream().map(TeamId::toString).toList();
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, spool, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, spool, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not persist Homes cleanup spool", exception);
        }
    }

    @Override
    public void close() {
        worker.shutdownNow();
        synchronized (pending) { persist(); }
    }
}
