package me.alexisbinh.openteams.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;

public sealed interface OperationResult<T>
        permits OperationResult.Success, OperationResult.Failure {

    UUID correlationId();

    record Success<T>(T value, UUID correlationId) implements OperationResult<T> {
        public Success {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(correlationId, "correlationId");
        }
    }

    record Failure<T>(
            TeamErrorCode code,
            String messageKey,
            Map<String, String> messageArguments,
            UUID correlationId
    ) implements OperationResult<T> {
        public Failure {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(messageKey, "messageKey");
            messageArguments = Map.copyOf(messageArguments);
            Objects.requireNonNull(correlationId, "correlationId");
        }
    }

    static <T> OperationResult<T> success(T value) {
        return success(value, UUID.randomUUID());
    }

    static <T> OperationResult<T> success(T value, UUID correlationId) {
        return new Success<>(value, correlationId);
    }

    static <T> OperationResult<T> failure(TeamErrorCode code, String messageKey) {
        return failure(code, messageKey, Map.of(), UUID.randomUUID());
    }

    static <T> OperationResult<T> failure(
            TeamErrorCode code,
            String messageKey,
            UUID correlationId
    ) {
        return failure(code, messageKey, Map.of(), correlationId);
    }

    static <T> OperationResult<T> failure(
            TeamErrorCode code,
            String messageKey,
            Map<String, String> messageArguments,
            UUID correlationId
    ) {
        return new Failure<>(code, messageKey, messageArguments, correlationId);
    }

    default boolean isSuccess() {
        return this instanceof Success<T>;
    }

    default Optional<T> optionalValue() {
        return this instanceof Success<T> success ? Optional.of(success.value()) : Optional.empty();
    }
}
