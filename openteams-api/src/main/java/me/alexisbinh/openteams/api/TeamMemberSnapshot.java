package me.alexisbinh.openteams.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;

public record TeamMemberSnapshot(
        UUID playerId,
        String roleKey,
        Set<String> permissions,
        Instant joinedAt,
        Instant lastActiveAt
) {
    public TeamMemberSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(roleKey, "roleKey");
        permissions = Set.copyOf(permissions);
        Objects.requireNonNull(joinedAt, "joinedAt");
        Objects.requireNonNull(lastActiveAt, "lastActiveAt");
    }

    public boolean hasPermission(String permission) {
        return permissions.contains("*") || permissions.contains(permission);
    }
}
