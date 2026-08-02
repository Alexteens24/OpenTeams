package me.alexisbinh.openteams.homes.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import me.alexisbinh.openteams.api.TeamId;
import org.junit.jupiter.api.Test;

class PointCacheTest {
    @Test
    void cachesNegativeLookupsAndInvalidatesWholeTeam() {
        var cache = new PointCache(Duration.ofMinutes(5), 10);
        var team = TeamId.random();
        cache.putHome(team, Optional.empty());
        cache.putWarp(team, "spawn", Optional.empty());
        assertThat(cache.home(team)).contains(Optional.empty());
        assertThat(cache.warp(team, "spawn")).contains(Optional.empty());

        cache.invalidate(team);
        assertThat(cache.home(team)).isEmpty();
        assertThat(cache.warp(team, "spawn")).isEmpty();
    }
}
