package me.alexisbinh.openteams.testkit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import me.alexisbinh.openteams.api.TeamId;
import me.alexisbinh.openteams.api.TeamMemberSnapshot;
import me.alexisbinh.openteams.api.TeamSnapshot;
import me.alexisbinh.openteams.api.TeamState;
import me.alexisbinh.openteams.api.TeamVisibility;

public final class TeamSnapshots {
    private TeamSnapshots() {
    }

    public static TeamSnapshot ownedBy(UUID ownerId) {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        return new TeamSnapshot(
                TeamId.random(),
                "Test Team",
                "test team",
                "TEST",
                ownerId,
                TeamState.ACTIVE,
                TeamVisibility.PRIVATE,
                20,
                1,
                now,
                now,
                java.util.Map.of(),
                List.of(new TeamMemberSnapshot(ownerId, "owner", java.util.Set.of("*"), now, now))
        );
    }
}
