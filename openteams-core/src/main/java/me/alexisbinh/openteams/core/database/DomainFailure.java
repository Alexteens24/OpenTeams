package me.alexisbinh.openteams.core.database;

import me.alexisbinh.openteams.api.TeamErrorCode;

public final class DomainFailure extends Exception {
    private static final long serialVersionUID = 1L;

    private final TeamErrorCode code;

    public DomainFailure(TeamErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public TeamErrorCode code() {
        return code;
    }
}
