package me.alexisbinh.openteams.core.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import me.alexisbinh.openteams.core.database.DatabaseConfig;
import me.alexisbinh.openteams.core.database.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcChatPreferenceStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void preferencesSurviveStoreRecreation() throws Exception {
        var config = new DatabaseConfig(
                DatabaseConfig.Type.SQLITE,
                "chat-test",
                "jdbc:sqlite:" + temporaryDirectory.resolve("chat.db"),
                "", "", 1, 3000);
        try (var database = new DatabaseManager(config, Clock.systemUTC())) {
            database.start();
            var playerId = UUID.randomUUID();
            var store = new JdbcChatPreferenceStore(
                    database.dataSource(), config.namespace());

            assertThat(store.load(playerId)).isEqualTo(ChatPreferences.defaults());
            store.save(playerId, new ChatPreferences(true, true));

            var reopened = new JdbcChatPreferenceStore(
                    database.dataSource(), config.namespace());
            assertThat(reopened.load(playerId))
                    .isEqualTo(new ChatPreferences(true, true));
        }
    }
}
