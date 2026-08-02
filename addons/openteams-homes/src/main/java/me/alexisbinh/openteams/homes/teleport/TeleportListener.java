package me.alexisbinh.openteams.homes.teleport;

import me.alexisbinh.openteams.homes.config.HomesConfig;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class TeleportListener implements Listener {
    private final WarmupManager warmups;
    private final HomesConfig.Warmup config;

    public TeleportListener(WarmupManager warmups, HomesConfig.Warmup config) {
        this.warmups = warmups;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (config.cancelOnMove() && event.getTo() != null
                && warmups.moving(event.getPlayer(), event.getTo(), config.movementThreshold())) {
            warmups.cancel(event.getPlayer().getUniqueId(), "homes.error.cancelled-move");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (config.cancelOnDamage() && event.getEntity() instanceof org.bukkit.entity.Player player) {
            warmups.cancel(player.getUniqueId(), "homes.error.cancelled-damage");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        warmups.cancel(event.getPlayer().getUniqueId(), "homes.error.cancelled-quit");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        warmups.cancel(event.getPlayer().getUniqueId(), "homes.error.cancelled-death");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (config.cancelOnTeleport() && warmups.active(event.getPlayer().getUniqueId())) {
            warmups.cancel(event.getPlayer().getUniqueId(), "homes.error.cancelled-teleport");
        }
    }
}
