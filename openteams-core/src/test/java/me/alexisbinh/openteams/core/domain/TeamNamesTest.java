package me.alexisbinh.openteams.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TeamNamesTest {
    @Test
    void normalizesUnicodeAndCaseForIdentity() {
        assertThat(TeamNames.normalize("  Ｏｐｅｎ Teams  ")).isEqualTo("open teams");
    }

    @Test
    void validatesNamesAndTagsIndependently() {
        assertThat(TeamNames.validName("Open Teams")).isTrue();
        assertThat(TeamNames.validName("<red>Injected")).isFalse();
        assertThat(TeamNames.validTag("OT26")).isTrue();
        assertThat(TeamNames.validTag("A")).isTrue();
        assertThat(TeamNames.validTag("too-long-tag")).isFalse();
        assertThat(TeamNames.validName(null)).isFalse();
    }
}
