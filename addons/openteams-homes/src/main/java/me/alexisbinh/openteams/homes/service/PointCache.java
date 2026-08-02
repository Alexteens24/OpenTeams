package me.alexisbinh.openteams.homes.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;
import me.alexisbinh.openteams.api.TeamId;
import me.alexisbinh.openteams.homes.domain.TeleportPoint;

public final class PointCache {
    private final Cache<TeamId, Optional<TeleportPoint>> homes;
    private final Cache<WarpKey, Optional<TeleportPoint>> warps;

    public PointCache(Duration expiration, long maximumTeams) {
        homes = Caffeine.newBuilder().expireAfterAccess(expiration)
                .maximumSize(maximumTeams).build();
        warps = Caffeine.newBuilder().expireAfterAccess(expiration)
                .maximumSize(Math.max(maximumTeams, maximumTeams * 4)).build();
    }

    public Optional<Optional<TeleportPoint>> home(TeamId teamId) {
        return Optional.ofNullable(homes.getIfPresent(teamId));
    }

    public Optional<Optional<TeleportPoint>> warp(TeamId teamId, String name) {
        return Optional.ofNullable(warps.getIfPresent(new WarpKey(teamId, name)));
    }

    public void putHome(TeamId teamId, Optional<TeleportPoint> point) {
        homes.put(teamId, point);
    }

    public void putWarp(TeamId teamId, String name, Optional<TeleportPoint> point) {
        warps.put(new WarpKey(teamId, name), point);
    }

    public void invalidate(TeamId teamId) {
        homes.invalidate(teamId);
        warps.asMap().keySet().removeIf(key -> key.teamId().equals(teamId));
    }

    public void clear() {
        homes.invalidateAll();
        warps.invalidateAll();
    }

    private record WarpKey(TeamId teamId, String name) { }
}
