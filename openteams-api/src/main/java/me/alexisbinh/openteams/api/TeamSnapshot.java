package me.alexisbinh.openteams.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Map;

public record TeamSnapshot(
        TeamId id,
        String name,
        String normalizedName,
        String tag,
        UUID ownerId,
        TeamState state,
        TeamVisibility visibility,
        int memberLimit,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Map<String, String> settings,
        List<TeamMemberSnapshot> members
) {
    public TeamSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(normalizedName, "normalizedName");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        settings = Map.copyOf(settings);
        members = List.copyOf(members);
    }

    public String settingOrDefault(String key, String defaultValue) {
        return settings.getOrDefault(key, defaultValue);
    }
}
