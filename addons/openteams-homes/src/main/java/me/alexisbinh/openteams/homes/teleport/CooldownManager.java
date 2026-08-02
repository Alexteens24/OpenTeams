package me.alexisbinh.openteams.homes.teleport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.alexisbinh.openteams.homes.config.HomesConfig;
import me.alexisbinh.openteams.homes.domain.PointType;

public final class CooldownManager {
    private final ConcurrentHashMap<Key, Instant> deadlines = new ConcurrentHashMap<>();
    private final HomesConfig.Cooldown config;
    private final Clock clock;

    public CooldownManager(HomesConfig.Cooldown config, Clock clock) {
        this.config = config;
        this.clock = clock;
    }

    public Optional<Duration> remaining(UUID playerId, PointType type) {
        var key = key(playerId, type);
        var deadline = deadlines.get(key);
        if (deadline == null) return Optional.empty();
        var remaining = Duration.between(clock.instant(), deadline);
        if (remaining.isNegative() || remaining.isZero()) {
            deadlines.remove(key, deadline);
            return Optional.empty();
        }
        return Optional.of(remaining);
    }

    public void apply(UUID playerId, PointType type) {
        var duration = type == PointType.HOME ? config.home() : config.warp();
        deadlines.put(key(playerId, type), clock.instant().plus(duration));
    }

    private Key key(UUID playerId, PointType type) {
        return new Key(playerId, config.shared() ? null : type);
    }

    private record Key(UUID playerId, PointType type) { }
}
