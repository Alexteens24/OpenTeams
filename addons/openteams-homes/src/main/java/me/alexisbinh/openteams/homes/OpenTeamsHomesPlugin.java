package me.alexisbinh.openteams.homes;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import me.alexisbinh.openteams.api.OpenTeams;
import me.alexisbinh.openteams.api.extension.CommandRegistry;
import me.alexisbinh.openteams.api.extension.Registration;
import me.alexisbinh.openteams.api.extension.TeamPermissionRegistry;
import me.alexisbinh.openteams.api.extension.TeamUiRegistry;
import me.alexisbinh.openteams.homes.command.HomesCommands;
import me.alexisbinh.openteams.homes.config.HomesConfig;
import me.alexisbinh.openteams.homes.lifecycle.CleanupService;
import me.alexisbinh.openteams.homes.lifecycle.TeamLifecycleListener;
import me.alexisbinh.openteams.homes.persistence.HomesDatabase;
import me.alexisbinh.openteams.homes.persistence.JdbcPointRepository;
import me.alexisbinh.openteams.homes.service.MembershipAccess;
import me.alexisbinh.openteams.homes.service.PointCache;
import me.alexisbinh.openteams.homes.service.PointService;
import me.alexisbinh.openteams.homes.teleport.CooldownManager;
import me.alexisbinh.openteams.homes.teleport.SafeLocationResolver;
import me.alexisbinh.openteams.homes.teleport.TeleportEngine;
import me.alexisbinh.openteams.homes.teleport.TeleportListener;
import me.alexisbinh.openteams.homes.teleport.WarmupManager;
import me.alexisbinh.openteams.homes.ui.HomesDialogs;
import me.alexisbinh.openteams.homes.ui.HomesMessages;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class OpenTeamsHomesPlugin extends JavaPlugin {
    private final List<Registration> registrations = new ArrayList<>();
    private final AtomicBoolean ready = new AtomicBoolean();
    private HomesDatabase database;
    private PointService points;
    private WarmupManager warmups;
    private CleanupService cleanup;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            var api = Bukkit.getServicesManager().load(OpenTeams.class);
            if (api == null) throw new IllegalStateException("OpenTeams API service is unavailable");
            requireCompatible(api.apiVersion());
            var config = HomesConfig.load(getConfig(), getDataFolder().toPath());
            database = new HomesDatabase(config.storage());
            database.start();
            var repository = new JdbcPointRepository(database.dataSource(),
                    config.storage().namespace(), Clock.systemUTC());
            var cache = new PointCache(config.cache().expiration(), config.cache().maximumTeams());
            points = new PointService(repository, cache, config,
                    () -> ready.get() && !api.readOnly());
            var access = new MembershipAccess(api.teams());
            warmups = new WarmupManager(this);
            var cooldowns = new CooldownManager(config.cooldown(), Clock.systemUTC());
            var teleports = new TeleportEngine(this, config, access, points, warmups, cooldowns,
                    new SafeLocationResolver(this, config.safety()),
                    () -> ready.get() && !api.readOnly());
            var messages = new HomesMessages();
            var commands = new HomesCommands(this, config, access, points, teleports,
                    warmups, messages);
            var dialogs = new HomesDialogs(this, api, config, access, points, messages, warmups);

            registerPermissions(api);
            registrations.add(api.translations().register(this, Locale.US,
                    messages.englishEntries()));
            registrations.add(api.translations().register(this,
                    Locale.forLanguageTag("vi-VN"), messages.vietnameseEntries()));
            registrations.add(api.commands().register(this,
                    new CommandRegistry.CommandContribution("home", List.of(),
                            "openteams.homes.use", "homes.command.home", commands::home)));
            registrations.add(api.commands().register(this,
                    new CommandRegistry.CommandContribution("warp", List.of("warps"),
                            "openteams.homes.use", "homes.command.warp", commands::warp)));
            if (config.homeEnabled()) registrations.add(api.userInterface().register(this,
                    new TeamUiRegistry.UiAction("home", TeamUiRegistry.Area.DASHBOARD, 80,
                            "homes.ui.home", "homes.ui.home.description",
                            "openteams-homes:home.teleport", context -> ready.get(), context -> {
                                var player = Bukkit.getPlayer(context.viewerId());
                                if (player != null) dialogs.openHome(player);
                                return CompletableFuture.completedFuture(
                                        TeamUiRegistry.ActionOutcome.CLOSE);
                            })));
            if (config.warpsEnabled()) registrations.add(api.userInterface().register(this,
                    new TeamUiRegistry.UiAction("warps", TeamUiRegistry.Area.DASHBOARD, 70,
                            "homes.ui.warps", "homes.ui.warps.description",
                            "openteams-homes:warp.view", context -> ready.get(), context -> {
                                var player = Bukkit.getPlayer(context.viewerId());
                                if (player != null) dialogs.openWarps(player, "", 0);
                                return CompletableFuture.completedFuture(
                                        TeamUiRegistry.ActionOutcome.CLOSE);
                            })));

            cleanup = new CleanupService(this, api, repository, cache,
                    getDataFolder().toPath());
            Bukkit.getPluginManager().registerEvents(new TeleportListener(warmups,
                    config.warmup()), this);
            Bukkit.getPluginManager().registerEvents(new TeamLifecycleListener(warmups,
                    cleanup), this);
            cleanup.start();
            ready.set(!api.readOnly());
            Bukkit.getAsyncScheduler().runAtFixedRate(this, task -> {
                var healthy = database.healthy() && !api.readOnly();
                var previous = ready.getAndSet(healthy);
                if (previous != healthy) getLogger().log(healthy ? Level.INFO : Level.SEVERE,
                        healthy ? "Homes database recovered; writes are enabled."
                                : "Homes entered degraded mode; writes and teleports are disabled.");
            }, 1, 30, TimeUnit.SECONDS);
            getLogger().info("OpenTeams-Homes 0.1.0 enabled for OpenTeams API "
                    + api.apiVersion() + " using " + config.storage().type() + ".");
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "OpenTeams-Homes failed to initialize", exception);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private void registerPermissions(OpenTeams api) {
        var everyone = Set.of("owner", "co_owner", "moderator", "member");
        var managers = Set.of("owner", "co_owner");
        var moderators = Set.of("owner", "co_owner", "moderator");
        registerPermission(api, "home.teleport", everyone);
        registerPermission(api, "home.set", managers);
        registerPermission(api, "home.delete", managers);
        registerPermission(api, "warp.view", everyone);
        registerPermission(api, "warp.teleport", everyone);
        registerPermission(api, "warp.create", moderators);
        registerPermission(api, "warp.update", moderators);
        registerPermission(api, "warp.rename", managers);
        registerPermission(api, "warp.delete", managers);
    }

    private void registerPermission(OpenTeams api, String key, Set<String> roles) {
        var canonical = "openteams-homes:" + key;
        registrations.add(api.permissions().register(this,
                new TeamPermissionRegistry.Permission(canonical,
                        "homes.permission." + key, roles)));
    }

    private static void requireCompatible(String version) {
        if (version == null || !version.matches("0\\.1\\.\\d+")) {
            throw new IllegalStateException("OpenTeams API " + version
                    + " is incompatible; expected >=0.1.0 and <0.2.0");
        }
    }

    @Override
    public void onDisable() {
        ready.set(false);
        if (warmups != null) warmups.cancelAll();
        registrations.forEach(registration -> {
            try { registration.close(); }
            catch (RuntimeException exception) {
                getLogger().log(Level.WARNING, "Could not close an addon registration", exception);
            }
        });
        registrations.clear();
        if (cleanup != null) cleanup.close();
        if (points != null) points.close();
        if (database != null) database.close();
    }
}
