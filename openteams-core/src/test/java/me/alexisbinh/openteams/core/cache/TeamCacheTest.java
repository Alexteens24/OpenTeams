package me.alexisbinh.openteams.core.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import me.alexisbinh.openteams.api.MembershipLookup;
import me.alexisbinh.openteams.api.TeamId;
import me.alexisbinh.openteams.api.TeamMemberSnapshot;
import me.alexisbinh.openteams.api.TeamSnapshot;
import me.alexisbinh.openteams.api.TeamState;
import me.alexisbinh.openteams.api.TeamVisibility;
import org.junit.jupiter.api.Test;

class TeamCacheTest {
    @Test
    void removedMemberBecomesKnownAbsentAndStaleSnapshotIsIgnored() {
        var cache = new TeamCache();
        var owner = UUID.randomUUID();
        var member = UUID.randomUUID();
        var id = TeamId.random();
        var joined = snapshot(id, 2, owner, member);
        var kicked = snapshot(id, 3, owner);

        cache.put(joined);
        cache.put(kicked);
        cache.put(joined);

        assertThat(cache.membership(member).status())
                .isEqualTo(MembershipLookup.Status.ABSENT);
        assertThat(cache.team(id)).contains(kicked);
    }

    private static TeamSnapshot snapshot(
            TeamId id,
            long version,
            UUID owner,
            UUID... members
    ) {
        var snapshots = java.util.Arrays.stream(members)
                .map(player -> new TeamMemberSnapshot(
                        player,
                        player.equals(owner) ? "owner" : "member",
                        player.equals(owner) ? java.util.Set.of("*") : java.util.Set.of(),
                        Instant.EPOCH,
                        Instant.EPOCH))
                .toList();
        return new TeamSnapshot(
                id, "Team", "team", "T", owner, TeamState.ACTIVE, TeamVisibility.PRIVATE,
                20, version, Instant.EPOCH, Instant.EPOCH, java.util.Map.of(), snapshots);
    }
}
