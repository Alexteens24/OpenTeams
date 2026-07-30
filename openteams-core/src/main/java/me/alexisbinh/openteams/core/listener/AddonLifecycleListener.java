package me.alexisbinh.openteams.core.listener;

import me.alexisbinh.openteams.core.extension.ExtensionRegistries;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;

public final class AddonLifecycleListener implements Listener {
    private final ExtensionRegistries registries;

    public AddonLifecycleListener(ExtensionRegistries registries) {
        this.registries = registries;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        registries.unregisterOwner(event.getPlugin());
    }
}
