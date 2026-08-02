package me.alexisbinh.openteams.core.chat;

import java.util.HashSet;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import me.alexisbinh.openteams.api.TeamService;
import me.alexisbinh.openteams.ui.LocalizedMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class TeamChatService implements AutoCloseable {
    private final Plugin plugin;
    private final TeamService teams;
    private final ChatPreferenceStore store;
    private final String format;
    private final LocalizedMessages messages;
    private final ConcurrentHashMap<UUID, ChatPreferences> preferences =
            new ConcurrentHashMap<>();
    private final Object preferenceLock = new Object();
    private final HashMap<UUID, Long> preferenceGenerations = new HashMap<>();
    private final HashMap<UUID, CompletableFuture<ChatPreferences>> preferenceChains =
            new HashMap<>();
    private final ExecutorService executor = java.util.concurrent.Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("OpenTeams-ChatPreferences-", 0).factory());
    private final Semaphore databaseConcurrency = new Semaphore(2);

    public TeamChatService(
            Plugin plugin,
            TeamService teams,
            ChatPreferenceStore store,
            String format,
            LocalizedMessages messages
    ) {
        this.plugin = plugin;
        this.teams = teams;
        this.store = store;
        this.format = format;
        this.messages = messages;
    }

    public boolean teamChatEnabled(UUID playerId) {
        return chatMode(playerId) == ChatMode.TEAM;
    }

    public ChatMode chatMode(UUID playerId) {
        var current = preferences.get(playerId);
        if (current != null) return current.teamChat() ? ChatMode.TEAM : ChatMode.GLOBAL;
        synchronized (preferenceLock) {
            return preferenceChains.containsKey(playerId) ? ChatMode.LOADING : ChatMode.GLOBAL;
        }
    }

    public void notifyLoading(Player player) {
        dispatch(player, () -> player.sendMessage(
                messages.component(player, "chat.preferences-loading")));
    }

    public CompletionStage<ChatPreferences> load(UUID playerId) {
        final long generation;
        final CompletableFuture<ChatPreferences> load;
        synchronized (preferenceLock) {
            generation = preferenceGenerations.merge(playerId, 1L, Long::sum);
            preferences.remove(playerId);
            load = CompletableFuture.supplyAsync(() -> loadPreference(playerId), executor);
            preferenceChains.put(playerId, load);
        }
        publish(playerId, generation, load, true);
        return load;
    }

    public void unload(UUID playerId) {
        synchronized (preferenceLock) {
            preferenceGenerations.merge(playerId, 1L, Long::sum);
            preferenceChains.remove(playerId);
            preferences.remove(playerId);
        }
    }

    public CompletionStage<Boolean> toggleTeamChat(UUID playerId) {
        return update(playerId, current ->
                new ChatPreferences(!current.teamChat(), current.staffSpy()))
                .thenApply(ChatPreferences::teamChat);
    }

    public CompletionStage<Boolean> toggleSpy(UUID playerId) {
        return update(playerId, current ->
                new ChatPreferences(current.teamChat(), !current.staffSpy()))
                .thenApply(ChatPreferences::staffSpy);
    }

    public void broadcast(Player sender, Component message) {
        var team = teams.membershipCached(sender.getUniqueId()).optionalTeam();
        if (team.isEmpty()) {
            dispatch(sender, () -> sender.sendMessage(
                    messages.component(sender, "error.not-in-team")));
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

    private CompletionStage<ChatPreferences> update(
            UUID playerId,
            java.util.function.UnaryOperator<ChatPreferences> operation
    ) {
        final long generation;
        final CompletableFuture<ChatPreferences> next;
        synchronized (preferenceLock) {
            generation = preferenceGenerations.computeIfAbsent(playerId, ignored -> 1L);
            var base = preferenceChains.get(playerId);
            if (base == null) {
                base = CompletableFuture.completedFuture(
                        preferences.getOrDefault(playerId, ChatPreferences.defaults()));
            }
            next = base.thenApplyAsync(current -> {
                var updated = operation.apply(current);
                savePreference(playerId, updated);
                return updated;
            }, executor);
            preferenceChains.put(playerId, next);
        }
        publish(playerId, generation, next, false);
        return next;
    }

    private void publish(UUID playerId, long generation,
                         CompletableFuture<ChatPreferences> operation,
                         boolean reportLoadFailure) {
        operation.whenComplete((updated, failure) -> {
            synchronized (preferenceLock) {
                if (preferenceGenerations.getOrDefault(playerId, 0L) != generation) return;
                if (failure == null) preferences.put(playerId, updated);
                if (preferenceChains.get(playerId) == operation) {
                    preferenceChains.remove(playerId);
                }
            }
            if (failure != null && reportLoadFailure) {
                plugin.getLogger().warning("Could not load chat preferences for " + playerId
                        + ": " + failure.getMessage());
            }
        });
    }

    private ChatPreferences loadPreference(UUID playerId) {
        acquire();
        try {
            return store.load(playerId);
        } catch (Exception exception) {
            throw new PreferenceException(exception);
        } finally {
            databaseConcurrency.release();
        }
    }

    private void savePreference(UUID playerId, ChatPreferences updated) {
        acquire();
        try {
            store.save(playerId, updated);
        } catch (Exception exception) {
            throw new PreferenceException(exception);
        } finally {
            databaseConcurrency.release();
        }
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

    public enum ChatMode {
        LOADING,
        GLOBAL,
        TEAM
    }
}
