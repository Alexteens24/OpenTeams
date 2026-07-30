package me.alexisbinh.openteams.api;

import java.util.Objects;
import java.util.Optional;

public record MembershipLookup(Status status, TeamSnapshot team) {
    public MembershipLookup {
        Objects.requireNonNull(status, "status");
        if (status == Status.PRESENT) {
            Objects.requireNonNull(team, "team");
        } else if (team != null) {
            throw new IllegalArgumentException("Only PRESENT may contain a team snapshot");
        }
    }

    public static MembershipLookup loading() {
        return new MembershipLookup(Status.LOADING, null);
    }

    public static MembershipLookup present(TeamSnapshot team) {
        return new MembershipLookup(Status.PRESENT, team);
    }

    public static MembershipLookup absent() {
        return new MembershipLookup(Status.ABSENT, null);
    }

    public static MembershipLookup failed() {
        return new MembershipLookup(Status.FAILED, null);
    }

    public Optional<TeamSnapshot> optionalTeam() {
        return Optional.ofNullable(team);
    }

    public enum Status {
        LOADING,
        PRESENT,
        ABSENT,
        FAILED
    }
}
