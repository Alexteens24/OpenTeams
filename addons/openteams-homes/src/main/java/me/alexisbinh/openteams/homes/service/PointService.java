package me.alexisbinh.openteams.homes.service;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import me.alexisbinh.openteams.api.TeamId;
import me.alexisbinh.openteams.homes.config.HomesConfig;
import me.alexisbinh.openteams.homes.domain.PointPage;
import me.alexisbinh.openteams.homes.domain.StoredLocation;
import me.alexisbinh.openteams.homes.domain.TeleportPoint;
import me.alexisbinh.openteams.homes.domain.WarpNames;
import me.alexisbinh.openteams.homes.persistence.PointRepository;

public final class PointService implements AutoCloseable {
    private final PointRepository repository;
    private final PointCache cache;
    private final HomesConfig config;
    private final ExecutorService executor;
    private final BooleanSupplier writable;

    public PointService(PointRepository repository, PointCache cache, HomesConfig config,
                        BooleanSupplier writable) {
        this.repository = repository;
        this.cache = cache;
        this.config = config;
        this.writable = writable;
        this.executor = java.util.concurrent.Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("OpenTeams-Homes-Database-", 0).factory());
    }

    public CompletionStage<HomesResult<Optional<TeleportPoint>>> home(TeamId teamId) {
        var cached = cache.home(teamId);
        if (cached.isPresent()) return completed(cached.get());
        return query(() -> {
            var point = repository.findHome(teamId);
            cache.putHome(teamId, point);
            return point;
        });
    }

    public CompletionStage<HomesResult<Optional<TeleportPoint>>> warp(
            TeamId teamId, String name) {
        final String normalized;
        try {
            normalized = WarpNames.validateAndNormalize(name, config.minimumNameLength(),
                    config.maximumNameLength());
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.completedFuture(nameFailure(exception));
        }
        var cached = cache.warp(teamId, normalized);
        if (cached.isPresent()) return completed(cached.get());
        return query(() -> {
            var point = repository.findWarp(teamId, normalized);
            cache.putWarp(teamId, normalized, point);
            return point;
        });
    }

    public CompletionStage<HomesResult<Optional<TeleportPoint>>> fresh(UUID id) {
        return query(() -> repository.findById(id));
    }

    public CompletionStage<HomesResult<PointPage>> warps(TeamId teamId, String query, int page) {
        return query(() -> repository.searchWarps(teamId, WarpNames.normalizeSearch(query),
                Math.max(0, page), config.pageSize()));
    }

    public CompletionStage<HomesResult<TeleportPoint>> setHome(
            TeamId teamId, StoredLocation location, UUID actor, OptionalLong version) {
        if (!config.homeEnabled()) return featureDisabled();
        if (!writable.getAsBoolean()) return notReady();
        return mutation(teamId, () -> repository.setHome(teamId, location, actor, version));
    }

    public CompletionStage<HomesResult<TeleportPoint>> createWarp(
            TeamId teamId, String name, StoredLocation location, UUID actor) {
        if (!config.warpsEnabled()) return featureDisabled();
        if (!writable.getAsBoolean()) return notReady();
        final String normalized;
        try {
            normalized = WarpNames.validateAndNormalize(name, config.minimumNameLength(),
                    config.maximumNameLength());
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.completedFuture(nameFailure(exception));
        }
        return mutation(teamId, () -> repository.createWarp(teamId, name, normalized, location,
                actor, config.maximumWarps()));
    }

    public CompletionStage<HomesResult<TeleportPoint>> updateLocation(
            TeleportPoint point, StoredLocation location) {
        if (!writable.getAsBoolean()) return notReady();
        return mutation(point.teamId(), () -> repository.updateLocation(point.id(), point.teamId(),
                point.version(), location));
    }

    public CompletionStage<HomesResult<TeleportPoint>> rename(
            TeleportPoint point, String name) {
        if (!writable.getAsBoolean()) return notReady();
        final String normalized;
        try {
            normalized = WarpNames.validateAndNormalize(name, config.minimumNameLength(),
                    config.maximumNameLength());
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.completedFuture(nameFailure(exception));
        }
        return mutation(point.teamId(), () -> repository.renameWarp(point.id(), point.teamId(),
                point.version(), name, normalized));
    }

    public CompletionStage<HomesResult<Void>> delete(TeleportPoint point) {
        if (!writable.getAsBoolean()) return notReady();
        return CompletableFuture.supplyAsync(() -> {
            try {
                repository.delete(point.id(), point.teamId(), point.version());
                cache.invalidate(point.teamId());
                return new HomesResult.Success<Void>(null);
            } catch (PointRepository.Conflict conflict) {
                cache.invalidate(point.teamId());
                return new HomesResult.Failure<Void>(HomesResult.Code.CONFLICT,
                        "homes.error.conflict");
            } catch (SQLException exception) {
                return databaseFailure();
            }
        }, executor);
    }

    public CompletionStage<HomesResult<Void>> deleteTeam(TeamId teamId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                repository.deleteTeam(teamId);
                cache.invalidate(teamId);
                return new HomesResult.Success<Void>(null);
            } catch (SQLException exception) {
                return databaseFailure();
            }
        }, executor);
    }

    public void invalidate(TeamId teamId) { cache.invalidate(teamId); }

    private <T> CompletionStage<HomesResult<T>> query(SqlSupplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new HomesResult.Success<>(supplier.get());
            } catch (SQLException exception) {
                return databaseFailure();
            }
        }, executor);
    }

    private CompletionStage<HomesResult<TeleportPoint>> mutation(
            TeamId teamId, SqlSupplier<TeleportPoint> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var result = supplier.get();
                cache.invalidate(teamId);
                return new HomesResult.Success<TeleportPoint>(result);
            } catch (PointRepository.Conflict exception) {
                cache.invalidate(teamId);
                return new HomesResult.Failure<TeleportPoint>(HomesResult.Code.CONFLICT,
                        "homes.error.conflict");
            } catch (PointRepository.DuplicateName exception) {
                return new HomesResult.Failure<TeleportPoint>(HomesResult.Code.DUPLICATE_NAME,
                        "homes.error.duplicate-name");
            } catch (PointRepository.LimitReached exception) {
                return new HomesResult.Failure<TeleportPoint>(HomesResult.Code.LIMIT_REACHED,
                        "homes.error.limit", Map.of("limit", Integer.toString(config.maximumWarps())));
            } catch (SQLException exception) {
                return databaseFailure();
            }
        }, executor);
    }

    private <T> CompletionStage<HomesResult<T>> completed(T value) {
        return CompletableFuture.completedFuture(new HomesResult.Success<>(value));
    }

    private <T> CompletionStage<HomesResult<T>> featureDisabled() {
        return CompletableFuture.completedFuture(new HomesResult.Failure<>(
                HomesResult.Code.FEATURE_DISABLED, "homes.error.feature-disabled"));
    }

    private <T> CompletionStage<HomesResult<T>> notReady() {
        return CompletableFuture.completedFuture(new HomesResult.Failure<>(
                HomesResult.Code.NOT_READY, "homes.error.not-ready"));
    }

    private static <T> HomesResult.Failure<T> nameFailure(IllegalArgumentException exception) {
        return "reserved_name".equals(exception.getMessage())
                ? new HomesResult.Failure<>(HomesResult.Code.RESERVED_NAME,
                        "homes.error.reserved-name")
                : new HomesResult.Failure<>(HomesResult.Code.INVALID_NAME,
                        "homes.error.invalid-name");
    }

    private static <T> HomesResult.Failure<T> databaseFailure() {
        return new HomesResult.Failure<>(HomesResult.Code.DATABASE_ERROR,
                "homes.error.database");
    }

    @Override
    public void close() {
        executor.shutdownNow();
        cache.clear();
    }

    @FunctionalInterface
    private interface SqlSupplier<T> { T get() throws SQLException; }
}
