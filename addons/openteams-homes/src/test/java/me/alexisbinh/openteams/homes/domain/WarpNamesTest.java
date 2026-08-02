package me.alexisbinh.openteams.homes.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WarpNamesTest {
    @Test
    void normalizesNamesCaseInsensitively() {
        assertThat(WarpNames.validateAndNormalize("Spawn_One", 1, 24))
                .isEqualTo("spawn_one");
    }

    @Test
    void rejectsInvalidAndReservedNames() {
        assertThatThrownBy(() -> WarpNames.validateAndNormalize("hello world", 1, 24))
                .hasMessage("invalid_name");
        assertThatThrownBy(() -> WarpNames.validateAndNormalize("delete", 1, 24))
                .hasMessage("reserved_name");
    }
}
