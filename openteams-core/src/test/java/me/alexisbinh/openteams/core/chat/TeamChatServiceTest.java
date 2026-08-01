package me.alexisbinh.openteams.core.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import me.alexisbinh.openteams.ui.LocalizedMessages;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TeamChatServiceTest {
    private TeamChatService service;

    @AfterEach
    void tearDown() {
        if (service != null) service.close();
    }

    @Test
    void toggleWaitsForLoadAndUsesPersistedPreference() throws Exception {
        var playerId = UUID.randomUUID();
        var store = new BlockingStore(new ChatPreferences(true, false));
        service = service(store);

        service.load(playerId);
        assertThat(store.loadStarted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(service.chatMode(playerId)).isEqualTo(TeamChatService.ChatMode.LOADING);
        var toggled = service.toggleTeamChat(playerId).toCompletableFuture();
        assertThat(toggled).isNotDone();

        store.releaseLoad.countDown();

        assertThat(toggled.join()).isFalse();
        assertThat(store.preferences.teamChat()).isFalse();
        assertThat(service.chatMode(playerId)).isEqualTo(TeamChatService.ChatMode.GLOBAL);
    }

    @Test
    void completionAfterUnloadCannotRestoreCachedPreference() throws Exception {
        var playerId = UUID.randomUUID();
        var store = new BlockingStore(new ChatPreferences(true, false));
        service = service(store);
        var loading = service.load(playerId).toCompletableFuture();
        assertThat(store.loadStarted.await(1, TimeUnit.SECONDS)).isTrue();

        service.unload(playerId);
        store.releaseLoad.countDown();
        loading.join();

        assertThat(service.chatMode(playerId)).isEqualTo(TeamChatService.ChatMode.GLOBAL);
        assertThat(service.toggleTeamChat(playerId).toCompletableFuture().join()).isTrue();
    }

    @Test
    void concurrentTogglesAreSerialized() {
        var playerId = UUID.randomUUID();
        var store = new BlockingStore(ChatPreferences.defaults());
        store.releaseLoad.countDown();
        service = service(store);
        service.load(playerId).toCompletableFuture().join();

        var first = service.toggleTeamChat(playerId).toCompletableFuture();
        var second = service.toggleTeamChat(playerId).toCompletableFuture();

        assertThat(first.join()).isTrue();
        assertThat(second.join()).isFalse();
        assertThat(store.preferences.teamChat()).isFalse();
    }

    private static TeamChatService service(ChatPreferenceStore store) {
        return new TeamChatService(plugin(), null, store, "<message>",
                new LocalizedMessages(Locale.US, (locale, key) -> null, false));
    }

    private static Plugin plugin() {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getLogger")) return Logger.getLogger("test");
                    if (method.getName().equals("getName")) return "OpenTeamsTest";
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                });
    }

    private static final class BlockingStore implements ChatPreferenceStore {
        private final CountDownLatch loadStarted = new CountDownLatch(1);
        private final CountDownLatch releaseLoad = new CountDownLatch(1);
        private volatile ChatPreferences preferences;

        private BlockingStore(ChatPreferences preferences) {
            this.preferences = preferences;
        }

        @Override
        public ChatPreferences load(UUID playerId) throws SQLException {
            loadStarted.countDown();
            try {
                if (!releaseLoad.await(2, TimeUnit.SECONDS)) {
                    throw new SQLException("Timed out waiting for test load release");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new SQLException(exception);
            }
            return preferences;
        }

        @Override
        public synchronized void save(UUID playerId, ChatPreferences updated) {
            preferences = updated;
        }
    }
}
