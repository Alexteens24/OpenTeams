package me.alexisbinh.openteams.api;

import java.util.Objects;
import java.util.UUID;

public record TeamId(UUID value) {
    public TeamId {
        Objects.requireNonNull(value, "value");
    }

    public static TeamId random() {
        return new TeamId(UUID.randomUUID());
    }

    public static TeamId parse(String value) {
        return new TeamId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
