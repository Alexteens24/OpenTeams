package me.alexisbinh.openteams.homes.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiPredicate;
import java.util.function.Function;
import me.alexisbinh.openteams.api.MembershipLookup;
import me.alexisbinh.openteams.api.TeamService;
import me.alexisbinh.openteams.api.TeamSnapshot;

public final class MembershipAccess {
    private final Function<UUID, CompletionStage<MembershipLookup>> loader;
    private final BiPredicate<UUID, String> permissionCheck;

    public MembershipAccess(TeamService teams) {
        this(teams::loadMembership, teams::hasPermissionCached);
    }

    MembershipAccess(Function<UUID, CompletionStage<MembershipLookup>> loader,
                     BiPredicate<UUID, String> permissionCheck) {
        this.loader = loader;
        this.permissionCheck = permissionCheck;
    }

    public CompletionStage<HomesResult<TeamSnapshot>> require(UUID playerId, String permission) {
        return load(playerId, permission, true);
    }

    private CompletionStage<HomesResult<TeamSnapshot>> load(
            UUID playerId, String permission, boolean retry) {
        return loader.apply(playerId).handle((lookup, failure) -> {
            if (failure != null || lookup == null || lookup.status() == MembershipLookup.Status.FAILED
                    || lookup.status() == MembershipLookup.Status.LOADING) {
                if (retry) return load(playerId, permission, false);
                return CompletableFuture.<HomesResult<TeamSnapshot>>completedFuture(
                        new HomesResult.Failure<>(HomesResult.Code.LOAD_FAILED,
                                "homes.error.membership-load"));
            }
            if (lookup.status() == MembershipLookup.Status.ABSENT) {
                return CompletableFuture.<HomesResult<TeamSnapshot>>completedFuture(
                        new HomesResult.Failure<>(HomesResult.Code.NO_TEAM,
                                "homes.error.no-team"));
            }
            if (!permissionCheck.test(playerId, permission)) {
                return CompletableFuture.<HomesResult<TeamSnapshot>>completedFuture(
                        new HomesResult.Failure<>(HomesResult.Code.FORBIDDEN,
                                "homes.error.forbidden", Map.of("permission", permission)));
            }
            return CompletableFuture.<HomesResult<TeamSnapshot>>completedFuture(
                    new HomesResult.Success<>(lookup.team()));
        }).thenCompose(stage -> stage);
    }
}
