package me.alexisbinh.openteams.core.listener;

import me.alexisbinh.openteams.core.chat.TeamChatService;
import me.alexisbinh.openteams.core.service.TeamServiceImpl;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerStateListener implements Listener {
    private final TeamServiceImpl teams;
    private final TeamChatService chat;

    public PlayerStateListener(TeamServiceImpl teams, TeamChatService chat) {
        this.teams = teams;
        this.chat = chat;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        teams.remember(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        teams.loadMembership(event.getPlayer().getUniqueId());
        chat.load(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        chat.unload(event.getPlayer().getUniqueId());
        var onlinePlayers = new java.util.HashSet<java.util.UUID>();
        Bukkit.getOnlinePlayers().forEach(player -> onlinePlayers.add(player.getUniqueId()));
        onlinePlayers.remove(event.getPlayer().getUniqueId());
        teams.pruneCache(onlinePlayers);
    }
}
