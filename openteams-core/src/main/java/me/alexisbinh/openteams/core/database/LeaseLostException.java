package me.alexisbinh.openteams.core.database;

import java.sql.SQLException;

public final class LeaseLostException extends SQLException {
    private static final long serialVersionUID = 1L;

    public LeaseLostException(String message) {
        super(message);
    }
}
