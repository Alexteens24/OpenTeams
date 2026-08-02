package me.alexisbinh.openteams.homes.domain;

import java.util.Objects;
import java.util.UUID;

public record StoredLocation(
        String serverId,
        UUID worldId,
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {
    public StoredLocation {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(worldName, "worldName");
        if (serverId.isBlank() || worldName.isBlank()
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Invalid stored location");
        }
    }
}
