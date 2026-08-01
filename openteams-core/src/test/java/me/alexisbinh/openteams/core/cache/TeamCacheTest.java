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
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;

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

    @Test
    void concurrentPublicationNeverLetsOlderVersionOverwriteNewerVersion()
            throws Exception {
        var cache = new TeamCache();
        var owner = UUID.randomUUID();
        var id = TeamId.random();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (var thread = 0; thread < 8; thread++) {
                final var offset = thread;
                futures.add(executor.submit(() -> {
                    start.await();
                    for (var version = offset + 1; version <= 8_000; version += 8) {
                        cache.put(snapshot(id, version, owner));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) {
                future.get();
            }
        }

        assertThat(cache.team(id)).get()
                .extracting(TeamSnapshot::version)
                .isEqualTo(8_000L);
    }

    @Test
    void staleMembershipLoadCannotOverwriteCommittedMutation() {
        var cache = new TeamCache();
        var player = UUID.randomUUID();
        var team = snapshot(TeamId.random(), 1, player, player);
        var load = cache.beginMembershipLoad(player);

        cache.put(team);
        var published = cache.completeMembershipLoad(load, java.util.Optional.empty());

        assertThat(published).isFalse();
        assertThat(cache.playerTeam(player)).contains(team);
    }

    @Test
    void staleResyncCannotReplaceNewerPlayerLookup() {
        var cache = new TeamCache();
        var player = UUID.randomUUID();
        var oldLoads = cache.beginMembershipLoads(java.util.List.of(player));
        var currentLoad = cache.beginMembershipLoad(player);
        var team = snapshot(TeamId.random(), 1, player, player);
        cache.completeMembershipLoad(currentLoad, java.util.Optional.of(team));

        cache.reconcileMembershipLoads(oldLoads, java.util.Map.of());

        assertThat(cache.playerTeam(player)).contains(team);
    }

    @Test
    void pruningRemovesTeamsWithoutOnlineMembers() {
        var cache = new TeamCache();
        var player = UUID.randomUUID();
        var team = snapshot(TeamId.random(), 1, player, player);
        cache.put(team);

        cache.pruneOffline(java.util.Set.of());

        assertThat(cache.team(team.id())).isEmpty();
        assertThat(cache.playerTeam(player)).isEmpty();
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
