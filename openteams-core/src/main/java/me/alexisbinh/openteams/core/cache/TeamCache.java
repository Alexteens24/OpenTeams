package me.alexisbinh.openteams.core.cache;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import me.alexisbinh.openteams.api.MembershipLookup;
import me.alexisbinh.openteams.api.TeamId;
import me.alexisbinh.openteams.api.TeamRelation;
import me.alexisbinh.openteams.api.TeamSnapshot;

public final class TeamCache {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private State state = new State();

    public Optional<TeamSnapshot> team(TeamId id) {
        var read = lock.readLock();
        read.lock();
        try {
            return Optional.ofNullable(state.teams.get(id));
        } finally {
            read.unlock();
        }
    }

    public Optional<TeamSnapshot> playerTeam(UUID playerId) {
        return membership(playerId).optionalTeam();
    }

    public TeamRelation relation(UUID firstPlayerId, UUID secondPlayerId) {
        var read = lock.readLock();
        read.lock();
        try {
            var first = state.memberships.get(firstPlayerId);
            var second = state.memberships.get(secondPlayerId);
            if (first == null || second == null
                    || first.status() != MembershipLookup.Status.PRESENT
                    || second.status() != MembershipLookup.Status.PRESENT) {
                return TeamRelation.UNKNOWN;
            }
            return first.optionalTeam().orElseThrow().id().equals(
                    second.optionalTeam().orElseThrow().id())
                    ? TeamRelation.SAME : TeamRelation.DIFFERENT;
        } finally {
            read.unlock();
        }
    }

    public MembershipLookup membership(UUID playerId) {
        var read = lock.readLock();
        read.lock();
        try {
            return state.memberships.getOrDefault(playerId, MembershipLookup.loading());
        } finally {
            read.unlock();
        }
    }

    public void markLoading(UUID playerId) {
        setMembership(playerId, MembershipLookup.loading());
    }

    public void markAbsent(UUID playerId) {
        setMembership(playerId, MembershipLookup.absent());
    }

    public void markFailed(UUID playerId) {
        setMembership(playerId, MembershipLookup.failed());
    }

    public void put(TeamSnapshot snapshot) {
        var write = lock.writeLock();
        write.lock();
        try {
            var previous = state.teams.get(snapshot.id());
            if (previous != null && previous.version() > snapshot.version()) {
                return;
            }
            if (previous != null) {
                var currentMembers = snapshot.members().stream()
                        .map(member -> member.playerId())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
                previous.members().stream()
                        .filter(member -> !currentMembers.contains(member.playerId()))
                        .forEach(member -> state.memberships.put(
                                member.playerId(), MembershipLookup.absent()));
            }
            state.teams.put(snapshot.id(), snapshot);
            snapshot.members().forEach(member ->
                    state.memberships.put(
                            member.playerId(), MembershipLookup.present(snapshot)));
        } finally {
            write.unlock();
        }
    }

    public void remove(TeamId id) {
        var write = lock.writeLock();
        write.lock();
        try {
            var previous = state.teams.remove(id);
            if (previous != null) {
                previous.members().forEach(member ->
                        state.memberships.put(
                                member.playerId(), MembershipLookup.absent()));
            }
        } finally {
            write.unlock();
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
        var write = lock.writeLock();
        write.lock();
        try {
            state = replacement;
        } finally {
            write.unlock();
        }
    }

    private void setMembership(UUID playerId, MembershipLookup lookup) {
        var write = lock.writeLock();
        write.lock();
        try {
            state.memberships.put(playerId, lookup);
        } finally {
            write.unlock();
        }
    }

    private static final class State {
        private final Map<TeamId, TeamSnapshot> teams = new HashMap<>();
        private final Map<UUID, MembershipLookup> memberships = new HashMap<>();
    }
}
