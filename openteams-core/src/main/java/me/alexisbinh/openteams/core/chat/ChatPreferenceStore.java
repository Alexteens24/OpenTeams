package me.alexisbinh.openteams.core.chat;

import java.sql.SQLException;
import java.util.UUID;

interface ChatPreferenceStore {
    ChatPreferences load(UUID playerId) throws SQLException;

    void save(UUID playerId, ChatPreferences preferences) throws SQLException;
}
