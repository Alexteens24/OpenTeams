package me.alexisbinh.openteams.homes.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoredLocationTest {
    @Test
    void rejectsNonFiniteCoordinates() {
        assertThatThrownBy(() -> new StoredLocation("local", UUID.randomUUID(), "world",
                Double.NaN, 64, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
