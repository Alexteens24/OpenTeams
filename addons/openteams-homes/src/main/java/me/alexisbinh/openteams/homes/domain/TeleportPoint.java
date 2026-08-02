package me.alexisbinh.openteams.homes.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import me.alexisbinh.openteams.api.TeamId;

public record TeleportPoint(
        UUID id,
        TeamId teamId,
        PointType type,
        String displayName,
        String normalizedName,
        StoredLocation location,
        UUID creatorId,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static final String HOME_KEY = "__home__";

    public TeleportPoint {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(normalizedName, "normalizedName");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(creatorId, "creatorId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (version < 0 || (type == PointType.HOME && !HOME_KEY.equals(normalizedName))) {
            throw new IllegalArgumentException("Invalid teleport point");
        }
    }
}
