package me.alexisbinh.openteams.core.service;

import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import me.alexisbinh.openteams.api.OperationResult;
import me.alexisbinh.openteams.api.MembershipLookup;
import me.alexisbinh.openteams.api.PlayerDirectory;
import me.alexisbinh.openteams.api.TeamErrorCode;
import me.alexisbinh.openteams.api.TeamDirectory;
import me.alexisbinh.openteams.api.TeamId;
import me.alexisbinh.openteams.api.TeamRelation;
import me.alexisbinh.openteams.api.TeamRequests;
import me.alexisbinh.openteams.api.TeamService;
import me.alexisbinh.openteams.api.TeamSnapshot;
import me.alexisbinh.openteams.api.TeamState;
import me.alexisbinh.openteams.api.TeamVisibility;
import me.alexisbinh.openteams.api.event.TeamMutationCommittedEvent;
import me.alexisbinh.openteams.api.mutation.MutationIntent;
import me.alexisbinh.openteams.api.mutation.MutationType;
import me.alexisbinh.openteams.core.cache.TeamCache;
import me.alexisbinh.openteams.core.database.DomainFailure;
import me.alexisbinh.openteams.core.database.JdbcTeamStore;
import me.alexisbinh.openteams.core.database.LeaseLostException;
import me.alexisbinh.openteams.core.domain.TeamNames;
import me.alexisbinh.openteams.core.extension.ExtensionRegistries;
import me.alexisbinh.openteams.core.runtime.RuntimeController;

public final class TeamServiceImpl implements TeamService, PlayerDirectory, AutoCloseable {
    private final JdbcTeamStore store;
    private final TeamCache cache;
    private final ExecutorService executor;
    private final Semaphore inFlight;
    private final RuntimeController runtime;
    private final ExtensionRegistries registries;
    private final Consumer<TeamMutationCommittedEvent> eventPublisher;
    private final BooleanSupplier leaseHeld;
    private final java.util.concurrent.ConcurrentMap<UUID, CompletableFuture<MembershipLookup>>
            membershipLoads = new java.util.concurrent.ConcurrentHashMap<>();

    public TeamServiceImpl(
            JdbcTeamStore store,
            TeamCache cache,
            int maximumConcurrency,
            RuntimeController runtime,
            ExtensionRegistries registries,
            Consumer<TeamMutationCommittedEvent> eventPublisher,
            BooleanSupplier leaseHeld
    ) {
        this.store = store;
        this.cache = cache;
        this.runtime = runtime;
        this.registries = registries;
        this.eventPublisher = eventPublisher;
        this.leaseHeld = leaseHeld;
        this.inFlight = new Semaphore(Math.max(1, maximumConcurrency));
        this.executor = java.util.concurrent.Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("OpenTeams-Database-", 0).factory());
    }

    @Override
    public Optional<TeamSnapshot> findCached(TeamId id) {
        return cache.team(id);
    }

    @Override
    public MembershipLookup membershipCached(UUID playerId) {
        return cache.membership(playerId);
    }

    @Override
    public TeamRelation relationCached(UUID firstPlayerId, UUID secondPlayerId) {
        return cache.relation(firstPlayerId, secondPlayerId);
    }

    @Override
    public boolean hasPermissionCached(UUID playerId, String permission) {
        return cache.playerTeam(playerId)
                .flatMap(snapshot -> snapshot.members().stream()
                        .filter(member -> member.playerId().equals(playerId))
                        .findFirst())
                .map(member -> member.hasPermission(permission)
                        || registries.defaultPermissions(member.roleKey()).contains(permission))
                .orElse(false);
    }

    @Override
    public CompletionStage<Optional<TeamSnapshot>> find(TeamId id) {
        return query(() -> store.find(id));
    }

    @Override
    public CompletionStage<MembershipLookup> loadMembership(UUID playerId) {
        return membershipLoads.computeIfAbsent(playerId, id -> {
            var load = cache.beginMembershipLoad(id);
            var future = CompletableFuture.supplyAsync(() -> {
                acquirePermit();
                try {
                    var result = store.findByPlayer(id);
                    cache.completeMembershipLoad(load, result);
                    return result.map(MembershipLookup::present).orElseGet(MembershipLookup::absent);
                } catch (SQLException exception) {
                    cache.failMembershipLoad(load);
                    runtime.degrade();
                    throw new DatabaseOperationException(exception);
                } finally {
                    inFlight.release();
                }
            }, executor);
            future.whenComplete((ignored, failure) -> membershipLoads.remove(id, future));
            return future;
        });
    }

    @Override
    public CompletionStage<TeamDirectory.Page<TeamDirectory.TeamSummary>> searchPublicTeams(
            String query, int page, int pageSize) {
        return queryValue(() -> store.searchPublicTeams(query, page, pageSize));
    }

    @Override
    public CompletionStage<List<TeamDirectory.Invitation>> invitations(UUID playerId) {
        return queryValue(() -> store.invitations(playerId));
    }

    @Override
    public CompletionStage<List<TeamDirectory.JoinRequest>> joinRequests(TeamId teamId) {
        return queryValue(() -> store.joinRequests(teamId));
    }

    @Override
    public CompletionStage<List<TeamDirectory.OutgoingJoinRequest>> joinRequestsByPlayer(
            UUID playerId) {
        return queryValue(() -> store.joinRequestsByPlayer(playerId));
    }

    @Override
    public CompletionStage<List<TeamDirectory.OutgoingInvitation>> outgoingInvitations(
            TeamId teamId) {
        return queryValue(() -> store.outgoingInvitations(teamId));
    }

    @Override
    public CompletionStage<List<TeamDirectory.Ban>> bans(TeamId teamId) {
        return queryValue(() -> store.bans(teamId));
    }

    @Override
    public CompletionStage<List<TeamDirectory.Role>> roles() {
        return queryValue(store::roles);
    }

    @Override
    public CompletionStage<Map<UUID, TeamDirectory.PlayerSummary>> resolve(
            Collection<UUID> playerIds) {
        return queryValue(() -> store.resolvePlayers(playerIds));
    }

    @Override
    public CompletionStage<List<TeamDirectory.PlayerSummary>> findExact(String name) {
        return queryValue(() -> store.findPlayersExact(name));
    }

    @Override
    public CompletionStage<List<TeamDirectory.PlayerSummary>> search(String query, int limit) {
        if (limit < 1 || limit > 100) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Player search limit must be between 1 and 100"));
        }
        return queryValue(() -> store.searchPlayers(query, limit));
    }

    @Override
    public CompletionStage<Void> remember(UUID playerId, String currentName) {
        if (!runtime.writable()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            acquirePermit();
            try {
                store.rememberPlayer(playerId, currentName);
            } catch (SQLException | DomainFailure exception) {
                runtime.degrade();
                throw new DatabaseOperationException(exception);
            } finally {
                inFlight.release();
            }
        }, executor);
    }

    public CompletionStage<Void> resync(Collection<UUID> onlinePlayers) {
        var loads = cache.beginMembershipLoads(onlinePlayers);
        return CompletableFuture.runAsync(() -> {
            acquirePermit();
            try {
                var byPlayer = store.findByPlayers(onlinePlayers);
                var snapshots = new LinkedHashMap<TeamId, TeamSnapshot>();
                byPlayer.values().forEach(snapshot -> {
                    snapshots.put(snapshot.id(), snapshot);
                });
                var enrichedByPlayer = new LinkedHashMap<UUID, TeamSnapshot>();
                byPlayer.forEach((playerId, snapshot) ->
                        enrichedByPlayer.put(playerId, snapshots.get(snapshot.id())));
                cache.reconcileMembershipLoads(loads, enrichedByPlayer);
            } catch (SQLException exception) {
                throw new DatabaseOperationException(exception);
            } finally {
                inFlight.release();
            }
        }, executor);
    }

    public void pruneCache(Collection<UUID> onlinePlayers) {
        cache.pruneOffline(Set.copyOf(onlinePlayers));
    }

    @Override
    public CompletionStage<OperationResult<TeamSnapshot>> create(TeamRequests.Create request) {
        if (!TeamNames.validName(request.name())) {
            return completedFailure(TeamErrorCode.INVALID_ARGUMENT, "openteams.error.invalid-name");
        }
        if (!TeamNames.validTag(request.tag())) {
            return completedFailure(TeamErrorCode.INVALID_ARGUMENT, "openteams.error.invalid-tag");
        }
        return mutate(intent(MutationType.TEAM_CREATE, request.actorId(), null, null,
                        Map.of("name", request.name(), "tag", request.tag() == null ? "" : request.tag())),
                () -> store.create(request.actorId(), request.name(), request.tag()));
    }

    @Override
    public CompletionStage<OperationResult<TeamSnapshot>> disband(TeamRequests.TeamAction request) {
        return mutate(intent(MutationType.TEAM_DISBAND, request.actorId(), request.teamId(), null),
                () -> store.disband(request.teamId(), request.actorId()));
    }

    @Override
    public CompletionStage<OperationResult<TeamSnapshot>> invite(TeamRequests.TargetAction request) {
        return mutate(intent(MutationType.MEMBER_INVITE, request.actorId(), request.teamId(),
                        request.targetId()),
                () -> store.invite(request.teamId(), request.actorId(), request.targetId()));
    }

    @Override
    public CompletionStage<OperationResult<TeamSnapshot>> acceptInvitation(
            TeamRequests.TargetAction request
    ) {
        return mutate(intent(MutationType.INVITATION_ACCEPT, request.actorId(), request.teamId(), null),
                () -> store.acceptInvitation(request.teamId(), request.actorId()));
    }

    @Override
    public CompletionStage<OperationResult<TeamSnapshot>> declineInvitation(
            TeamRequests.TargetAction request) {
        return mutate(intent(MutationType.INVITATION_DECLINE, request.actorId(), request.teamId(),
                        request.actorId()),
                () -> store.declineInvitation(request.teamId(), request.actorId()));
    }

    @Override
    public CompletionStage<OperationResult<TeamSnapshot>> revokeInvitation(
            TeamRequests.TargetAction request) {
        return mutate(intent(MutationType.INVITATION_REVOKE, request.actorId(), request.teamId(),
                        request.targetId()),
                () -> store.revokeInvitation(request.teamId(), request.actorId(), request.targetId()));
    }

    @Override
    public CompletionStage<OperationResult<TeamSnapshot>> leave(TeamRequests.TeamAction request) {
        return mutate(intent(MutationType.MEMBER_LEAVE, request.actorId(), request.teamId(), null),
                () -> store.leave(request.teamId(), request.actorId()));
    }

    @Override
    public CompletionStage<OperationResult<TeamSnapshot>> kick(TeamRequests.TargetAction request) {
        return mutate(intent(MutationType.MEMBER_KICK, request.actorId(), request.teamId(),
                        request.targetId()),
                () -> store.kick(request.teamId(), request.actorId(), request.targetId()));
    }

    @Override
    public CompletionStage<OperationResult<TeamSnapshot>> transferOwnership(
            TeamRequests.TargetAction request
    ) {
        return mutate(intent(MutationType.OWNER_TRANSFER, request.actorId(), request.teamId(),
                        request.targetId()),
                () -> store.transfer(request.teamId(), request.actorId(), request.targetId()));
    }

    @Override
    public CompletionStage<OperationResult<TeamSnapshot>> rename(TeamRequests.Rename request) {
        if (!TeamNames.validName(request.name())) {
            return completedFailure(TeamErrorCode.INVALID_ARGUMENT, "openteams.error.invalid-name");
        }
        return mutate(intent(MutationType.TEAM_RENAME, request.actorId(), request.teamId(), null,
                        Map.of("name", request.name())),
                () -> store.rename(request.teamId(), request.actorId(), request.name()));
    }

    @Override
    public CompletionStage<OperationResult<TeamSnapshot>> setTag(TeamRequests.SetTag request) {
        if (!TeamNames.validTag(request.tag())) {
            return completedFailure(TeamErrorCode.INVALID_ARGUMENT, "openteams.error.invalid-tag");
        }
        return mutate(intent(MutationType.TEAM_TAG_CHANGE, request.actorId(), request.teamId(), null,
                        Map.of("tag", request.tag() == null ? "" : request.tag())),
                () -> store.setTag(request.teamId(), request.actorId(), request.tag()));
    }

    @Override
    public CompletionStage<OperationResult<TeamSnapshot>> requestJoin(
            TeamRequests.TeamAction request
    ) {
        return mutate(intent(MutationType.JOIN_REQUEST_CREATE, request.actorId(),
                        request.teamId(), null),
                () -> store.requestJoin(request.teamId(), request.actorId()));
    }

    @Override
    public CompletionStage<OperationResult<TeamSnapshot>> acceptJoinRequest(
            TeamRequests.TargetAction request
    ) {
        return mutate(intent(MutationType.JOIN_REQUEST_ACCEPT, request.actorId(),
                        request.teamId(), request.targetId()), () -> store.acceptJoinRequest(
                request.teamId(), request.actorId(), request.targetId()));
    }

    @Override
    public CompletionStage<OperationResult<TeamSnapshot>> rejectJoinRequest(
            TeamRequests.TargetAction request) {
        return mutate(intent(MutationType.JOIN_REQUEST_REJECT, request.actorId(), request.teamId(),
                        request.targetId()),
                () -> store.rejectJoinRequest(request.teamId(), request.actorId(), request.targetId()));
    }

    @Override
    public CompletionStage<OperationResult<TeamSnapshot>> cancelJoinRequest(
            TeamRequests.TeamAction request) {
        return mutate(intent(MutationType.JOIN_REQUEST_CANCEL, request.actorId(), request.teamId(),
                        request.actorId()),
                () -> store.cancelJoinRequest(request.teamId(), request.actorId()));
    }

    @Override
    public CompletionStage<OperationResult<TeamSnapshot>> ban(TeamRequests.Ban request) {
        return mutate(intent(MutationType.MEMBER_BAN, request.actorId(), request.teamId(),
                        request.targetId(), Map.of("reason",
                                request.reason() == null ? "" : request.reason())), () -> store.ban(
                request.teamId(), request.actorId(), request.targetId(), request.reason()));
    }

    @Override
    public CompletionStage<OperationResult<TeamSnapshot>> unban(
            TeamRequests.TargetAction request
    ) {
        return mutate(intent(MutationType.MEMBER_UNBAN, request.actorId(), request.teamId(),
                        request.targetId()), () -> store.unban(
                request.teamId(), request.actorId(), request.targetId()));
    }

    @Override
    public CompletionStage<OperationResult<TeamSnapshot>> changeRole(
            TeamRequests.ChangeRole request
    ) {
        return mutate(intent(MutationType.MEMBER_ROLE_CHANGE, request.actorId(),
                        request.teamId(), request.targetId(),
                        Map.of("role", request.roleKey())),
                () -> store.changeRole(request.teamId(), request.actorId(),
                        request.targetId(), request.roleKey()));
    }

    @Override
    public CompletionStage<OperationResult<TeamSnapshot>> setSetting(
            TeamRequests.SetSetting request
    ) {
        var validation = registries.validateSetting(request.key(), request.encodedValue());
        if (!validation.valid()) {
            return completedFailure(
                    TeamErrorCode.INVALID_ARGUMENT, "openteams.error.invalid-setting");
        }
        return mutate(intent(MutationType.TEAM_SETTING_CHANGE, request.actorId(),
                        request.teamId(), null,
                        Map.of("setting", request.key())),
                () -> store.setSetting(request.teamId(), request.actorId(), request.key(),
                        request.encodedValue(), validation.permission()));
    }

    @Override
    public CompletionStage<OperationResult<TeamSnapshot>> setVisibility(
            TeamRequests.SetVisibility request) {
        return mutate(intent(MutationType.TEAM_VISIBILITY_CHANGE, request.actorId(),
                        request.teamId(), null,
                        Map.of("visibility", request.visibility().name())),
                () -> store.setVisibility(request.teamId(), request.actorId(), request.visibility()));
    }

    private <T> CompletionStage<T> queryValue(SqlValue<T> query) {
        return CompletableFuture.supplyAsync(() -> {
            acquirePermit();
            try {
                return query.run();
            } catch (SQLException exception) {
                runtime.degrade();
                throw new DatabaseOperationException(exception);
            } finally {
                inFlight.release();
            }
        }, executor);
    }

    private CompletionStage<Optional<TeamSnapshot>> query(SqlQuery query) {
        return CompletableFuture.supplyAsync(() -> {
            acquirePermit();
            try {
                var result = query.run();
                result.ifPresent(cache::putFromQuery);
                return result;
            } catch (SQLException exception) {
                runtime.degrade();
                throw new DatabaseOperationException(exception);
            } finally {
                inFlight.release();
            }
        }, executor);
    }

    private CompletionStage<OperationResult<TeamSnapshot>> mutate(
            MutationIntent intent,
            SqlMutation mutation
    ) {
        if (!runtime.writable()) {
            return completedFailure(TeamErrorCode.READ_ONLY, "openteams.error.read-only",
                    intent.correlationId());
        }
        return CompletableFuture.supplyAsync(() -> {
            var permitAcquired = false;
            try {
                var policyDecision = registries.evaluatePolicies(intent);
                if (!policyDecision.allowed()) {
                    return OperationResult.<TeamSnapshot>failure(
                            TeamErrorCode.FORBIDDEN,
                            policyDecision.messageKey(),
                            intent.correlationId());
                }
                acquirePermit();
                permitAcquired = true;
                if (!runtime.writable() || !leaseHeld.getAsBoolean()) {
                    return OperationResult.<TeamSnapshot>failure(
                            TeamErrorCode.READ_ONLY,
                            "openteams.error.read-only",
                            intent.correlationId());
                }
                var before = intent.optionalTeamId().flatMap(cache::team).orElse(null);
                for (var attempt = 0; attempt < 3; attempt++) {
                    try {
                        var snapshot = store.correlated(intent.correlationId(), mutation::run);
                        if (snapshot.state() == TeamState.DISBANDED) {
                            cache.remove(snapshot.id());
                        } else {
                            cache.put(snapshot);
                        }
                        try {
                            eventPublisher.accept(new TeamMutationCommittedEvent(
                                    intent, before, snapshot));
                        } catch (RuntimeException exception) {
                            registries.reportWarning(
                                    "Post-commit listener failed for correlation "
                                            + intent.correlationId() + ": "
                                            + exception.getMessage());
                        }
                        return OperationResult.<TeamSnapshot>success(
                                snapshot, intent.correlationId());
                    } catch (DomainFailure failure) {
                        if (failure.code() == TeamErrorCode.CONFLICT && attempt < 2) {
                            Thread.onSpinWait();
                            continue;
                        }
                        return OperationResult.<TeamSnapshot>failure(
                                failure.code(),
                                failure.messageKey(),
                                failure.messageArguments(),
                                intent.correlationId());
                    }
                }
                return OperationResult.<TeamSnapshot>failure(
                        TeamErrorCode.CONFLICT, "openteams.error.conflict",
                        intent.correlationId());
            } catch (LeaseLostException exception) {
                runtime.degrade();
                return OperationResult.<TeamSnapshot>failure(
                        TeamErrorCode.READ_ONLY, "openteams.error.read-only",
                        intent.correlationId());
            } catch (SQLIntegrityConstraintViolationException exception) {
                return OperationResult.<TeamSnapshot>failure(
                        TeamErrorCode.CONFLICT, "openteams.error.conflict",
                        intent.correlationId());
            } catch (SQLException exception) {
                runtime.degrade();
                if (isConstraintViolation(exception)) {
                    return OperationResult.<TeamSnapshot>failure(
                            TeamErrorCode.CONFLICT, "openteams.error.conflict",
                            intent.correlationId());
                }
                return OperationResult.<TeamSnapshot>failure(
                        TeamErrorCode.DATABASE_UNAVAILABLE, "openteams.error.database_unavailable",
                        intent.correlationId());
            } finally {
                if (permitAcquired) {
                    inFlight.release();
                }
            }
        }, executor);
    }

    private static boolean isConstraintViolation(SQLException exception) {
        var state = exception.getSQLState();
        return state != null && state.startsWith("23")
                || exception.getMessage() != null
                && exception.getMessage().toLowerCase(java.util.Locale.ROOT).contains("unique constraint");
    }

    private void acquirePermit() {
        try {
            inFlight.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException(exception);
        }
    }

    private static CompletionStage<OperationResult<TeamSnapshot>> completedFailure(
            TeamErrorCode code,
            String message
    ) {
        return CompletableFuture.completedFuture(OperationResult.failure(code, message));
    }

    private static CompletionStage<OperationResult<TeamSnapshot>> completedFailure(
            TeamErrorCode code,
            String message,
            UUID correlationId
    ) {
        return CompletableFuture.completedFuture(
                OperationResult.failure(code, message, correlationId));
    }

    private static MutationIntent intent(
            MutationType type,
            UUID actorId,
            TeamId teamId,
            UUID targetId
    ) {
        return intent(type, actorId, teamId, targetId, Map.of());
    }

    private static MutationIntent intent(
            MutationType type,
            UUID actorId,
            TeamId teamId,
            UUID targetId,
            Map<String, String> metadata
    ) {
        return new MutationIntent(
                UUID.randomUUID(), type, actorId, teamId, targetId, metadata);
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface SqlQuery {
        Optional<TeamSnapshot> run() throws SQLException;
    }

    @FunctionalInterface
    private interface SqlValue<T> {
        T run() throws SQLException;
    }

    @FunctionalInterface
    private interface SqlMutation {
        TeamSnapshot run() throws SQLException, DomainFailure;
    }

    private static final class DatabaseOperationException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private DatabaseOperationException(Throwable cause) {
            super(cause);
        }
    }
}
