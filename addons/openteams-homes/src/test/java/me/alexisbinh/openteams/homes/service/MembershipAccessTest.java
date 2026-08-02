package me.alexisbinh.openteams.homes.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import me.alexisbinh.openteams.api.MembershipLookup;
import me.alexisbinh.openteams.api.TeamId;
import me.alexisbinh.openteams.api.TeamSnapshot;
import me.alexisbinh.openteams.api.TeamState;
import me.alexisbinh.openteams.api.TeamVisibility;
import org.junit.jupiter.api.Test;

class MembershipAccessTest {
    private final UUID player = UUID.randomUUID();

    @Test
    void distinguishesPresentAbsentAndForbidden() {
        var team = team();
        var allowed = new MembershipAccess(id -> completed(MembershipLookup.present(team)),
                (id, permission) -> true);
        assertThat(allowed.require(player, "permission").toCompletableFuture().join())
                .isInstanceOf(HomesResult.Success.class);

        var absent = new MembershipAccess(id -> completed(MembershipLookup.absent()),
                (id, permission) -> true);
        assertFailure(absent, HomesResult.Code.NO_TEAM);

        var forbidden = new MembershipAccess(id -> completed(MembershipLookup.present(team)),
                (id, permission) -> false);
        assertFailure(forbidden, HomesResult.Code.FORBIDDEN);
    }

    @Test
    void retriesLoadingOrFailedOnce() {
        var calls = new AtomicInteger();
        var retry = new MembershipAccess(id -> completed(calls.getAndIncrement() == 0
                        ? MembershipLookup.loading() : MembershipLookup.present(team())),
                (id, permission) -> true);
        assertThat(retry.require(player, "permission").toCompletableFuture().join())
                .isInstanceOf(HomesResult.Success.class);
        assertThat(calls).hasValue(2);

        var failed = new MembershipAccess(id -> completed(MembershipLookup.failed()),
                (id, permission) -> true);
        assertFailure(failed, HomesResult.Code.LOAD_FAILED);
    }

    private void assertFailure(MembershipAccess access, HomesResult.Code code) {
        var result = access.require(player, "permission").toCompletableFuture().join();
        assertThat(result).isInstanceOfSatisfying(HomesResult.Failure.class,
                failure -> assertThat(failure.code()).isEqualTo(code));
    }

    private static CompletableFuture<MembershipLookup> completed(MembershipLookup lookup) {
        return CompletableFuture.completedFuture(lookup);
    }

    private TeamSnapshot team() {
        return new TeamSnapshot(TeamId.random(), "Team", "team", null, player,
                TeamState.ACTIVE, TeamVisibility.PUBLIC, 20, 0, Instant.EPOCH, Instant.EPOCH,
                Map.of(), List.of());
    }
}
