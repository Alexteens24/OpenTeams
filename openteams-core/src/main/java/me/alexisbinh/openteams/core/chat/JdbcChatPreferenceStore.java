package me.alexisbinh.openteams.core.chat;

import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcChatPreferenceStore {
    private final DataSource dataSource;
    private final String namespace;

    public JdbcChatPreferenceStore(DataSource dataSource, String namespace) {
        this.dataSource = dataSource;
        this.namespace = namespace;
    }

    public ChatPreferences load(UUID playerId) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT team_chat,staff_spy FROM player_preferences
                     WHERE namespace = ? AND player_id = ?
                     """)) {
            statement.setString(1, namespace);
            statement.setString(2, playerId.toString());
            try (var result = statement.executeQuery()) {
                return result.next()
                        ? new ChatPreferences(result.getInt(1) != 0, result.getInt(2) != 0)
                        : ChatPreferences.defaults();
            }
        }
    }

    public void save(UUID playerId, ChatPreferences preferences) throws SQLException {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int updated;
                try (var update = connection.prepareStatement("""
                        UPDATE player_preferences SET team_chat = ?, staff_spy = ?
                        WHERE namespace = ? AND player_id = ?
                        """)) {
                    update.setInt(1, preferences.teamChat() ? 1 : 0);
                    update.setInt(2, preferences.staffSpy() ? 1 : 0);
                    update.setString(3, namespace);
                    update.setString(4, playerId.toString());
                    updated = update.executeUpdate();
                }
                if (updated == 0) {
                    try (var insert = connection.prepareStatement("""
                            INSERT INTO player_preferences(
                                namespace,player_id,team_chat,staff_spy,locale_override
                            ) VALUES(?,?,?,?,NULL)
                            """)) {
                        insert.setString(1, namespace);
                        insert.setString(2, playerId.toString());
                        insert.setInt(3, preferences.teamChat() ? 1 : 0);
                        insert.setInt(4, preferences.staffSpy() ? 1 : 0);
                        insert.executeUpdate();
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }
}
