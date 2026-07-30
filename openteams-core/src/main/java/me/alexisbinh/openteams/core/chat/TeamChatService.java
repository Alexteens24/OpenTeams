package me.alexisbinh.openteams.core.chat;

import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import me.alexisbinh.openteams.api.TeamService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class TeamChatService implements AutoCloseable {
    private final Plugin plugin;
    private final TeamService teams;
    private final JdbcChatPreferenceStore store;
    private final String format;
    private final ConcurrentHashMap<UUID, ChatPreferences> preferences =
            new ConcurrentHashMap<>();
    private final ExecutorService executor = java.util.concurrent.Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("OpenTeams-ChatPreferences-", 0).factory());
    private final Semaphore databaseConcurrency = new Semaphore(2);

    public TeamChatService(
            Plugin plugin,
            TeamService teams,
            JdbcChatPreferenceStore store,
            String format
    ) {
        this.plugin = plugin;
        this.teams = teams;
        this.store = store;
        this.format = format;
    }

    public boolean teamChatEnabled(UUID playerId) {
        return preferences.getOrDefault(playerId, ChatPreferences.defaults()).teamChat();
    }

    public void load(UUID playerId) {
        CompletableFuture.runAsync(() -> {
            acquire();
            try {
                preferences.put(playerId, store.load(playerId));
            } catch (Exception exception) {
                plugin.getLogger().warning(
                        "Could not load chat preferences for " + playerId + ": "
                                + exception.getMessage());
            } finally {
                databaseConcurrency.release();
            }
        }, executor);
    }

    public void unload(UUID playerId) {
        preferences.remove(playerId);
    }

    public CompletionStage<Boolean> toggleTeamChat(UUID playerId) {
        var current = preferences.getOrDefault(playerId, ChatPreferences.defaults());
        return persist(playerId, new ChatPreferences(!current.teamChat(), current.staffSpy()))
                .thenApply(ignored -> !current.teamChat());
    }

    public CompletionStage<Boolean> toggleSpy(UUID playerId) {
        var current = preferences.getOrDefault(playerId, ChatPreferences.defaults());
        return persist(playerId, new ChatPreferences(current.teamChat(), !current.staffSpy()))
                .thenApply(ignored -> !current.staffSpy());
    }

    public void broadcast(Player sender, Component message) {
        var team = teams.findByPlayerCached(sender.getUniqueId());
        if (team.isEmpty()) {
            dispatch(sender, () -> sender.sendMessage(
                    Component.text("You are not in a team.")));
            return;
        }
        var rendered = MiniMessage.miniMessage().deserialize(
                format,
                Placeholder.unparsed("team", team.get().name()),
                Placeholder.unparsed("tag", team.get().tag() == null ? "" : team.get().tag()),
                Placeholder.unparsed("player", sender.getName()),
                Placeholder.component("message", message));
        var recipients = new HashSet<UUID>();
        team.get().members().forEach(member -> recipients.add(member.playerId()));
        preferences.forEach((playerId, value) -> {
            if (value.staffSpy()) {
                recipients.add(playerId);
            }
        });
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> recipients.forEach(playerId -> {
            var recipient = Bukkit.getPlayer(playerId);
            if (recipient != null) {
                dispatch(recipient, () -> recipient.sendMessage(rendered));
            }
        }));
    }

    private CompletionStage<Void> persist(UUID playerId, ChatPreferences updated) {
        return CompletableFuture.runAsync(() -> {
            acquire();
            try {
                store.save(playerId, updated);
                preferences.put(playerId, updated);
            } catch (Exception exception) {
                throw new PreferenceException(exception);
            } finally {
                databaseConcurrency.release();
            }
        }, executor);
    }

    private void acquire() {
        try {
            databaseConcurrency.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PreferenceException(exception);
        }
    }

    private void dispatch(Player player, Runnable action) {
        player.getScheduler().run(plugin, task -> action.run(), null);
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private static final class PreferenceException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private PreferenceException(Throwable cause) {
            super(cause);
        }
    }
}
