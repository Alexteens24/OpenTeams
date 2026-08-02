package me.alexisbinh.openteams.homes.service;

import java.util.Map;

public sealed interface HomesResult<T> permits HomesResult.Success, HomesResult.Failure {
    static <T, U> Failure<U> copyFailure(Failure<T> failure) {
        return new Failure<>(failure.code(), failure.messageKey(), failure.arguments());
    }

    record Success<T>(T value) implements HomesResult<T> { }

    record Failure<T>(Code code, String messageKey, Map<String, String> arguments)
            implements HomesResult<T> {
        public Failure {
            arguments = Map.copyOf(arguments);
        }

        public Failure(Code code, String messageKey) {
            this(code, messageKey, Map.of());
        }
    }

    enum Code {
        NO_TEAM,
        LOAD_FAILED,
        FORBIDDEN,
        FEATURE_DISABLED,
        NOT_FOUND,
        INVALID_NAME,
        RESERVED_NAME,
        DUPLICATE_NAME,
        LIMIT_REACHED,
        CONFLICT,
        DATABASE_ERROR,
        DIFFERENT_SERVER,
        COOLDOWN,
        CANCELLED,
        UNSAFE,
        TELEPORT_FAILED,
        NOT_READY
    }
}
