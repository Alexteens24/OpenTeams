package me.alexisbinh.openteams.homes.config;

import java.nio.file.Path;
import java.time.Duration;
import org.bukkit.configuration.file.FileConfiguration;

public record HomesConfig(
        boolean homeEnabled,
        boolean warpsEnabled,
        String serverId,
        Storage storage,
        int maximumWarps,
        int pageSize,
        int minimumNameLength,
        int maximumNameLength,
        Warmup warmup,
        Cooldown cooldown,
        Safety safety,
        Cache cache
) {
    public static HomesConfig load(FileConfiguration config, Path dataDirectory) {
        var type = StorageType.valueOf(config.getString("storage.type", "sqlite")
                .toUpperCase(java.util.Locale.ROOT));
        var storage = new Storage(
                type,
                config.getString("storage.namespace", "default"),
                dataDirectory.resolve(config.getString("storage.sqlite-file", "homes.db")),
                config.getString("storage.host", "localhost"),
                config.getInt("storage.port", 3306),
                config.getString("storage.database", "openteams_homes"),
                config.getString("storage.username", "openteams"),
                config.getString("storage.password", "change-me"),
                config.getInt("storage.pool-size", 8),
                Duration.ofMillis(config.getLong("storage.connection-timeout-ms", 3000)));
        var warmup = new Warmup(
                Duration.ofSeconds(config.getLong("warmup.home-seconds", 5)),
                Duration.ofSeconds(config.getLong("warmup.warp-seconds", 5)),
                config.getDouble("warmup.movement-threshold", 0.2),
                config.getBoolean("warmup.cancel-on-move", true),
                config.getBoolean("warmup.cancel-on-damage", true),
                config.getBoolean("warmup.cancel-on-teleport", true));
        var cooldown = new Cooldown(
                config.getBoolean("cooldown.shared", true),
                Duration.ofSeconds(config.getLong("cooldown.home-seconds", 30)),
                Duration.ofSeconds(config.getLong("cooldown.warp-seconds", 30)));
        var safety = new Safety(
                config.getBoolean("safety.enabled", true),
                config.getInt("safety.search-radius", 3),
                config.getInt("safety.vertical-search", 4),
                config.getBoolean("safety.allow-water", true),
                config.getBoolean("safety.allow-lava", false),
                config.getBoolean("safety.allow-fire", false),
                config.getBoolean("safety.allow-portal", false),
                config.getBoolean("safety.respect-world-border", true),
                Duration.ofMillis(config.getLong("safety.chunk-load-timeout-ms", 5000)));
        return new HomesConfig(
                config.getBoolean("features.home", true),
                config.getBoolean("features.warps", true),
                required(config.getString("server-id", "local"), "server-id"), storage,
                positive(config.getInt("warps.maximum-per-team", 20), "maximum warps"),
                positive(config.getInt("warps.page-size", 8), "page size"),
                positive(config.getInt("warps.name-min-length", 1), "minimum name length"),
                positive(config.getInt("warps.name-max-length", 24), "maximum name length"),
                warmup, cooldown, safety,
                new Cache(Duration.ofSeconds(config.getLong("cache.expiration-seconds", 300)),
                        positive(config.getInt("cache.maximum-teams", 10000), "cache maximum")));
    }

    private static int positive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    public enum StorageType { SQLITE, MYSQL, MARIADB }

    public record Storage(StorageType type, String namespace, Path sqliteFile, String host,
                          int port, String database, String username, String password,
                          int poolSize, Duration connectionTimeout) {
        public Storage {
            required(namespace, "storage namespace");
            if (poolSize <= 0) throw new IllegalArgumentException("pool size must be positive");
        }
    }

    public record Warmup(Duration home, Duration warp, double movementThreshold,
                         boolean cancelOnMove, boolean cancelOnDamage,
                         boolean cancelOnTeleport) {
        public Warmup {
            if (home.isNegative() || warp.isNegative() || movementThreshold < 0) {
                throw new IllegalArgumentException("Invalid warmup configuration");
            }
        }
    }

    public record Cooldown(boolean shared, Duration home, Duration warp) { }

    public record Safety(boolean enabled, int radius, int vertical, boolean allowWater,
                         boolean allowLava, boolean allowFire, boolean allowPortal,
                         boolean respectWorldBorder, Duration chunkTimeout) { }

    public record Cache(Duration expiration, long maximumTeams) { }
}
