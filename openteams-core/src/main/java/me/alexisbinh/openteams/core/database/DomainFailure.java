package me.alexisbinh.openteams.core.database;

import me.alexisbinh.openteams.api.TeamErrorCode;
import java.util.Map;

public final class DomainFailure extends Exception {
    private static final long serialVersionUID = 1L;

    private final TeamErrorCode code;
    private final String messageKey;
    private final transient Map<String, String> messageArguments;

    public DomainFailure(TeamErrorCode code, String messageKey, Map<String, String> messageArguments) {
        super(messageKey);
        this.code = code;
        this.messageKey = messageKey;
        this.messageArguments = Map.copyOf(messageArguments);
    }

    public TeamErrorCode code() {
        return code;
    }

    public String messageKey() {
        return messageKey;
    }

    public Map<String, String> messageArguments() {
        return messageArguments;
    }
}
