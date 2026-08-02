package me.alexisbinh.openteams.homes.teleport;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import me.alexisbinh.openteams.homes.config.HomesConfig;
import me.alexisbinh.openteams.homes.domain.PointType;
import org.junit.jupiter.api.Test;

class CooldownManagerTest {
    @Test
    void sharedCooldownOnlyStartsWhenAppliedAndExpires() {
        var clock = new MutableClock();
        var manager = new CooldownManager(new HomesConfig.Cooldown(true,
                Duration.ofSeconds(30), Duration.ofSeconds(30)), clock);
        var player = UUID.randomUUID();
        assertThat(manager.remaining(player, PointType.HOME)).isEmpty();
        manager.apply(player, PointType.HOME);
        assertThat(manager.remaining(player, PointType.WARP)).isPresent();
        clock.advance(Duration.ofSeconds(31));
        assertThat(manager.remaining(player, PointType.HOME)).isEmpty();
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.EPOCH;
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration duration) { now = now.plus(duration); }
    }
}
