package me.alexisbinh.openteams.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import me.alexisbinh.openteams.api.OpenTeams;
import me.alexisbinh.openteams.api.extension.CommandRegistry;
import me.alexisbinh.openteams.api.extension.Registration;
import me.alexisbinh.openteams.api.extension.TeamPermissionRegistry;
import me.alexisbinh.openteams.api.extension.TeamSettingRegistry;
import me.alexisbinh.openteams.api.extension.TeamUiRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExampleAddonPlugin extends JavaPlugin {
    private final List<Registration> registrations = new ArrayList<>();

    @Override
    public void onEnable() {
        var api = Bukkit.getServicesManager().load(OpenTeams.class);
        if (api == null) {
            throw new IllegalStateException("OpenTeams API service is unavailable");
        }

        registrations.add(api.permissions().register(this,
                new TeamPermissionRegistry.Permission(
                        "example.use", "example.permission.use",
                        Set.of("owner", "co_owner", "moderator"))));

        registrations.add(api.settings().register(this,
                new TeamSettingRegistry.Setting<>(
                        "example.enabled",
                        Boolean.class,
                        true,
                        new TeamSettingRegistry.Codec<>() {
                            @Override
                            public String encode(Boolean value) {
                                return value.toString();
                            }

                            @Override
                            public Boolean decode(String value) {
                                return Boolean.parseBoolean(value);
                            }
                        },
                        value -> true,
                        "example.use"
                )));

        registrations.add(api.commands().register(this,
                new CommandRegistry.CommandContribution(
                        "example",
                        List.of(),
                        "openteams.command.team",
                        "example.command.help",
                        (sender, arguments) -> {
                            sender.sendMessage(Component.text(
                                    "Example addon is connected through OpenTeams API.",
                                    NamedTextColor.GREEN));
                            return CompletableFuture.completedFuture(1);
                        }
                )));

        registrations.add(api.userInterface().register(this,
                new TeamUiRegistry.UiAction(
                        "dashboard",
                        "modules",
                        100,
                        "example.ui.label",
                        "example.use",
                        (viewer, team) -> true,
                        (viewer, team) -> {
                            var player = Bukkit.getPlayer(viewer);
                            if (player != null) {
                                player.getScheduler().run(this, task -> player.sendMessage(
                                        Component.text("Example module for " + team.name(),
                                                NamedTextColor.AQUA)), null);
                            }
                            return CompletableFuture.completedFuture(null);
                        }
                )));
    }

    @Override
    public void onDisable() {
        registrations.forEach(Registration::close);
        registrations.clear();
    }
}
