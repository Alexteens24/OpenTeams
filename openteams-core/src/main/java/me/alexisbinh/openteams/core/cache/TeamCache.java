package me.alexisbinh.openteams.core.cache;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import me.alexisbinh.openteams.api.MembershipLookup;
import me.alexisbinh.openteams.api.TeamId;
import me.alexisbinh.openteams.api.TeamSnapshot;

public final class TeamCache {
    private final AtomicReference<State> state = new AtomicReference<>(new State());

    public Optional<TeamSnapshot> team(TeamId id) {
        return Optional.ofNullable(state.get().teams.get(id));
    }

    public Optional<TeamSnapshot> playerTeam(UUID playerId) {
        return membership(playerId).optionalTeam();
    }

    public MembershipLookup membership(UUID playerId) {
        return state.get().memberships.getOrDefault(playerId, MembershipLookup.loading());
    }

    public void markLoading(UUID playerId) {
        state.get().memberships.put(playerId, MembershipLookup.loading());
    }

    public void markAbsent(UUID playerId) {
        state.get().memberships.put(playerId, MembershipLookup.absent());
    }

    public void markFailed(UUID playerId) {
        state.get().memberships.put(playerId, MembershipLookup.failed());
    }

    public void put(TeamSnapshot snapshot) {
        var current = state.get();
        var previous = current.teams.get(snapshot.id());
        if (previous != null && previous.version() > snapshot.version()) {
            return;
        }
        if (previous != null) {
            var currentMembers = snapshot.members().stream()
                    .map(member -> member.playerId())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            previous.members().stream()
                    .filter(member -> !currentMembers.contains(member.playerId()))
                    .forEach(member -> current.memberships.put(
                            member.playerId(), MembershipLookup.absent()));
        }
        current.teams.put(snapshot.id(), snapshot);
        snapshot.members().forEach(member ->
                current.memberships.put(member.playerId(), MembershipLookup.present(snapshot)));
    }

    public void remove(TeamId id) {
        var current = state.get();
        var previous = current.teams.remove(id);
        if (previous != null) {
            previous.members().forEach(member ->
                    current.memberships.put(member.playerId(), MembershipLookup.absent()));
        }
    }

    public void replaceOnline(
            Collection<TeamSnapshot> snapshots,
            Set<UUID> absentPlayers
    ) {
        var replacement = new State();
        snapshots.forEach(snapshot -> {
            replacement.teams.put(snapshot.id(), snapshot);
            snapshot.members().forEach(member -> replacement.memberships.put(
                    member.playerId(), MembershipLookup.present(snapshot)));
        });
        absentPlayers.forEach(player ->
                replacement.memberships.put(player, MembershipLookup.absent()));
        state.set(replacement);
    }

    private static final class State {
        private final ConcurrentHashMap<TeamId, TeamSnapshot> teams = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<UUID, MembershipLookup> memberships =
                new ConcurrentHashMap<>();
    }
}
