package me.alexisbinh.openteams.core.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.alexisbinh.openteams.core.chat.TeamChatService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class TeamChatListener implements Listener {
    private final TeamChatService chat;

    public TeamChatListener(TeamChatService chat) {
        this.chat = chat;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        var mode = chat.chatMode(event.getPlayer().getUniqueId());
        if (mode == TeamChatService.ChatMode.GLOBAL) return;
        event.setCancelled(true);
        if (mode == TeamChatService.ChatMode.LOADING) {
            chat.notifyLoading(event.getPlayer());
        } else {
            chat.broadcast(event.getPlayer(), event.message());
        }
    }
}
