package me.alexisbinh.openteams.api.mutation;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import me.alexisbinh.openteams.api.TeamId;

public record MutationIntent(
        UUID correlationId,
        MutationType type,
        UUID actorId,
        TeamId teamId,
        UUID targetId,
        Map<String, String> metadata
) {
    public MutationIntent {
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(actorId, "actorId");
        metadata = Map.copyOf(metadata);
    }

    public Optional<TeamId> optionalTeamId() {
        return Optional.ofNullable(teamId);
    }

    public Optional<UUID> optionalTargetId() {
        return Optional.ofNullable(targetId);
    }
}
