package me.alexisbinh.openteams.api.mutation;

import java.util.Objects;

public record PolicyDecision(boolean allowed, String messageKey) {
    public PolicyDecision {
        Objects.requireNonNull(messageKey, "messageKey");
    }

    public static PolicyDecision allow() {
        return new PolicyDecision(true, "");
    }

    public static PolicyDecision deny(String messageKey) {
        return new PolicyDecision(false, messageKey);
    }
}
