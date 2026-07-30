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
        if (!chat.teamChatEnabled(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        chat.broadcast(event.getPlayer(), event.message());
    }
}
