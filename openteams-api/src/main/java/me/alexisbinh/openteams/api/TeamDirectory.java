package me.alexisbinh.openteams.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Immutable read models used by player-facing team discovery and management. */
public final class TeamDirectory {
    private TeamDirectory() {
    }

    public record Page<T>(List<T> items, int page, int pageSize, boolean hasNext) {
        public Page {
            items = List.copyOf(items);
            if (page < 0 || pageSize < 1) {
                throw new IllegalArgumentException("Invalid page");
            }
        }
    }

    public record TeamSummary(
            TeamId id, String name, String tag, int memberCount, int memberLimit
    ) {
    }

    public record PlayerSummary(UUID playerId, String lastKnownName, Instant updatedAt) {
    }

    public record Invitation(
            TeamSummary team, PlayerSummary inviter, Instant createdAt, Instant expiresAt
    ) {
    }

    public record JoinRequest(PlayerSummary player, Instant createdAt, Instant expiresAt) {
    }

    public record OutgoingInvitation(PlayerSummary player, Instant createdAt, Instant expiresAt) {
    }

    public record OutgoingJoinRequest(TeamSummary team, Instant createdAt, Instant expiresAt) {
    }

    public record Ban(PlayerSummary player, String reason, Instant createdAt, Instant expiresAt) {
    }

    public record Role(
            String key, String displayName, int priority, Integer memberLimit,
            boolean protectedRole, java.util.Set<String> permissions
    ) {
        public Role {
            permissions = java.util.Set.copyOf(permissions);
        }
    }

    public static Map<UUID, PlayerSummary> copyPlayers(Map<UUID, PlayerSummary> players) {
        return Map.copyOf(players);
    }
}
