package me.alexisbinh.openteams.homes.teleport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import me.alexisbinh.openteams.homes.config.HomesConfig;
import me.alexisbinh.openteams.homes.domain.StoredLocation;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

public final class SafeLocationResolver {
    private final Plugin plugin;
    private final HomesConfig.Safety config;

    public SafeLocationResolver(Plugin plugin, HomesConfig.Safety config) {
        this.plugin = plugin;
        this.config = config;
    }

    public CompletionStage<Optional<Location>> resolve(
            World world, StoredLocation stored, boolean bypass) {
        var target = new Location(world, stored.x(), stored.y(), stored.z(),
                stored.yaw(), stored.pitch());
        if (stored.y() < world.getMinHeight() || stored.y() >= world.getMaxHeight()
                || config.respectWorldBorder() && !world.getWorldBorder().isInside(target)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        var chunks = requiredChunks(target);
        var loads = chunks.stream().map(key -> world.getChunkAtAsync(key.x(), key.z(), true))
                .toList();
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new))
                .orTimeout(config.chunkTimeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .thenCompose(ignored -> {
                    var answer = new CompletableFuture<Optional<Location>>();
                    plugin.getServer().getRegionScheduler().execute(plugin, target, () -> {
                        try {
                            answer.complete(bypass || !config.enabled()
                                    ? Optional.of(target) : find(target));
                        } catch (RuntimeException exception) {
                            answer.completeExceptionally(exception);
                        }
                    });
                    return answer;
                }).exceptionally(ignored -> Optional.empty());
    }

    private Optional<Location> find(Location origin) {
        for (var candidate : candidates(origin)) {
            if (safe(candidate)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    private ArrayList<Location> candidates(Location origin) {
        var result = new ArrayList<Location>();
        result.add(origin.clone());
        for (var vertical = 1; vertical <= config.vertical(); vertical++) {
            result.add(origin.clone().add(0, vertical, 0));
            result.add(origin.clone().add(0, -vertical, 0));
        }
        for (var radius = 1; radius <= config.radius(); radius++) {
            for (var x = -radius; x <= radius; x++) {
                for (var z = -radius; z <= radius; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                    for (var y = -config.vertical(); y <= config.vertical(); y++) {
                        result.add(origin.clone().add(x, y, z));
                    }
                }
            }
        }
        return result;
    }

    private boolean safe(Location location) {
        var world = location.getWorld();
        if (location.getY() < world.getMinHeight() + 1
                || location.getY() >= world.getMaxHeight() - 1
                || config.respectWorldBorder() && !world.getWorldBorder().isInside(location)) {
            return false;
        }
        var feet = location.getBlock();
        var head = location.clone().add(0, 1, 0).getBlock();
        var floor = location.clone().add(0, -1, 0).getBlock();
        return passable(feet.getType()) && passable(head.getType())
                && floor.getType().isSolid() && allowed(floor.getType());
    }

    private boolean passable(Material material) {
        if (material.isAir()) return true;
        if (material == Material.WATER) return config.allowWater();
        return !material.isSolid() && allowed(material);
    }

    private boolean allowed(Material material) {
        if ((material == Material.LAVA) && !config.allowLava()) return false;
        if ((material == Material.FIRE || material == Material.SOUL_FIRE
                || material == Material.CAMPFIRE || material == Material.SOUL_CAMPFIRE)
                && !config.allowFire()) return false;
        return (material != Material.NETHER_PORTAL && material != Material.END_PORTAL
                && material != Material.END_GATEWAY) || config.allowPortal();
    }

    private Set<ChunkKey> requiredChunks(Location origin) {
        var keys = new HashSet<ChunkKey>();
        var radius = config.enabled() ? config.radius() : 0;
        for (var x : new int[] {-radius, radius}) {
            for (var z : new int[] {-radius, radius}) {
                keys.add(new ChunkKey((origin.getBlockX() + x) >> 4,
                        (origin.getBlockZ() + z) >> 4));
            }
        }
        return keys;
    }

    private record ChunkKey(int x, int z) { }
}
