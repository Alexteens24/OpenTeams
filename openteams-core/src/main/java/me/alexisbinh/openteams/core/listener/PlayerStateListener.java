package me.alexisbinh.openteams.core.listener;

import me.alexisbinh.openteams.api.TeamService;
import me.alexisbinh.openteams.core.chat.TeamChatService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerStateListener implements Listener {
    private final TeamService teams;
    private final TeamChatService chat;

    public PlayerStateListener(TeamService teams, TeamChatService chat) {
        this.teams = teams;
        this.chat = chat;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        teams.findByPlayer(event.getPlayer().getUniqueId());
        chat.load(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        chat.unload(event.getPlayer().getUniqueId());
    }
}
