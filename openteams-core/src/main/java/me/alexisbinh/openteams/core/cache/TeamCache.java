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

    public MembershipLoad beginMembershipLoad(UUID playerId) {
        var write = lock.writeLock();
        write.lock();
        try {
            var generation = state.generations.merge(playerId, 1L, Long::sum);
            state.memberships.put(playerId, MembershipLookup.loading());
            return new MembershipLoad(playerId, generation);
        } finally {
            write.unlock();
        }
    }

    public Map<UUID, MembershipLoad> beginMembershipLoads(Collection<UUID> playerIds) {
        var loads = new HashMap<UUID, MembershipLoad>();
        var write = lock.writeLock();
        write.lock();
        try {
            playerIds.forEach(playerId -> {
                var generation = state.generations.merge(playerId, 1L, Long::sum);
                state.memberships.put(playerId, MembershipLookup.loading());
                loads.put(playerId, new MembershipLoad(playerId, generation));
            });
            return Map.copyOf(loads);
        } finally {
            write.unlock();
        }
    }

    public void put(TeamSnapshot snapshot) {
        var write = lock.writeLock();
        write.lock();
        try {
            var previous = state.teams.get(snapshot.id());
            if (previous != null && previous.version() > snapshot.version()) return;
            affectedPlayers(previous, snapshot).forEach(this::advanceGeneration);
            putInternal(snapshot);
        } finally {
            write.unlock();
        }
    }

    public void putFromQuery(TeamSnapshot snapshot) {
        var write = lock.writeLock();
        write.lock();
        try {
            putInternal(snapshot);
        } finally {
            write.unlock();
        }
    }

    public boolean completeMembershipLoad(MembershipLoad load, Optional<TeamSnapshot> snapshot) {
        var write = lock.writeLock();
        write.lock();
        try {
            if (!current(load)) return false;
            if (snapshot.isPresent()) putInternal(snapshot.get());
            else state.memberships.put(load.playerId(), MembershipLookup.absent());
            return true;
        } finally {
            write.unlock();
        }
    }

    public void failMembershipLoad(MembershipLoad load) {
        var write = lock.writeLock();
        write.lock();
        try {
            if (current(load)) state.memberships.put(load.playerId(), MembershipLookup.failed());
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
                previous.members().forEach(member -> {
                    advanceGeneration(member.playerId());
                    state.memberships.put(
                            member.playerId(), MembershipLookup.absent());
                });
            }
        } finally {
            write.unlock();
        }
    }

    public void reconcileMembershipLoads(Map<UUID, MembershipLoad> loads,
                                         Map<UUID, TeamSnapshot> byPlayer) {
        var write = lock.writeLock();
        write.lock();
        try {
            byPlayer.values().stream().distinct().forEach(this::putInternal);
            loads.forEach((playerId, load) -> {
                if (!current(load)) return;
                var snapshot = byPlayer.get(playerId);
                state.memberships.put(playerId, snapshot == null
                        ? MembershipLookup.absent() : MembershipLookup.present(snapshot));
            });
        } finally {
            write.unlock();
        }
    }

    public void pruneOffline(Set<UUID> onlinePlayers) {
        var write = lock.writeLock();
        write.lock();
        try {
            state.memberships.keySet().removeIf(playerId -> !onlinePlayers.contains(playerId));
            state.generations.keySet().removeIf(playerId -> !onlinePlayers.contains(playerId));
            state.teams.entrySet().removeIf(entry -> entry.getValue().members().stream()
                    .noneMatch(member -> onlinePlayers.contains(member.playerId())));
        } finally {
            write.unlock();
        }
    }

    private boolean putInternal(TeamSnapshot snapshot) {
        var previous = state.teams.get(snapshot.id());
        if (previous != null && previous.version() > snapshot.version()) return false;
        if (previous != null) {
            var currentMembers = snapshot.members().stream().map(member -> member.playerId())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            previous.members().stream().filter(member -> !currentMembers.contains(member.playerId()))
                    .forEach(member -> state.memberships.put(member.playerId(), MembershipLookup.absent()));
        }
        state.teams.put(snapshot.id(), snapshot);
        snapshot.members().forEach(member -> state.memberships.put(
                member.playerId(), MembershipLookup.present(snapshot)));
        return true;
    }

    private Set<UUID> affectedPlayers(TeamSnapshot previous, TeamSnapshot current) {
        var players = new java.util.HashSet<UUID>();
        if (previous != null) previous.members().forEach(member -> players.add(member.playerId()));
        current.members().forEach(member -> players.add(member.playerId()));
        return players;
    }

    private void advanceGeneration(UUID playerId) {
        state.generations.merge(playerId, 1L, Long::sum);
    }

    private boolean current(MembershipLoad load) {
        return state.generations.getOrDefault(load.playerId(), 0L) == load.generation();
    }

    private static final class State {
        private final Map<TeamId, TeamSnapshot> teams = new HashMap<>();
        private final Map<UUID, MembershipLookup> memberships = new HashMap<>();
        private final Map<UUID, Long> generations = new HashMap<>();
    }

    public record MembershipLoad(UUID playerId, long generation) {
    }
}
