package me.alexisbinh.openteams.core;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.time.Clock;
import java.util.List;
import java.util.logging.Level;
import me.alexisbinh.openteams.api.OpenTeams;
import me.alexisbinh.openteams.api.extension.TeamSettingRegistry;
import me.alexisbinh.openteams.core.cache.TeamCache;
import me.alexisbinh.openteams.core.chat.JdbcChatPreferenceStore;
import me.alexisbinh.openteams.core.chat.TeamChatService;
import me.alexisbinh.openteams.core.command.TeamCommands;
import me.alexisbinh.openteams.core.database.DatabaseConfig;
import me.alexisbinh.openteams.core.database.DatabaseManager;
import me.alexisbinh.openteams.core.database.JdbcTeamStore;
import me.alexisbinh.openteams.core.extension.ExtensionRegistries;
import me.alexisbinh.openteams.core.listener.FriendlyFireListener;
import me.alexisbinh.openteams.core.listener.AddonLifecycleListener;
import me.alexisbinh.openteams.core.listener.PlayerStateListener;
import me.alexisbinh.openteams.core.listener.TeamChatListener;
import me.alexisbinh.openteams.core.service.TeamServiceImpl;
import me.alexisbinh.openteams.core.runtime.RuntimeController;
import me.alexisbinh.openteams.ui.ChatTeamUserInterface;
import me.alexisbinh.openteams.ui.DialogTeamUserInterface;
import me.alexisbinh.openteams.ui.LocalizedMessages;
import me.alexisbinh.openteams.ui.TeamUserInterface;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class OpenTeamsPlugin extends JavaPlugin {
    private final RuntimeController runtime = new RuntimeController();
    private DatabaseManager database;
    private TeamServiceImpl teamService;
    private TeamChatService teamChat;
    private final AtomicBoolean heartbeatRunning = new AtomicBoolean();
    private final ExecutorService heartbeatExecutor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("OpenTeams-Heartbeat-", 0).factory());

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            var databaseSection = getConfig().getConfigurationSection("database");
            if (databaseSection == null) {
                throw new IllegalStateException("Missing database configuration");
            }
            var databaseConfig = DatabaseConfig.from(databaseSection, getDataFolder().toPath());
            database = new DatabaseManager(databaseConfig, Clock.systemUTC());
            database.start();

            var registries = new ExtensionRegistries(
                    message -> getLogger().warning(message),
                    java.time.Duration.ofMillis(getConfig().getLong(
                            "addons.policy-global-timeout-ms", 500)));

            var store = new JdbcTeamStore(
                    database.dataSource(),
                    database.namespace(),
                    Clock.systemUTC(),
                    getConfig().getInt("team.default-member-limit", 20),
                    getConfig().getLong("team.invitation-expiry-seconds", 604_800) * 1000,
                    database,
                    registries::hasDefaultPermission
            );
            registries.settings().register(this, new TeamSettingRegistry.Setting<>(
                    "friendly-fire",
                    Boolean.class,
                    getConfig().getString("friendly-fire.mode", "deny")
                            .equalsIgnoreCase("allow"),
                    new TeamSettingRegistry.Codec<>() {
                        @Override
                        public String encode(Boolean value) {
                            return value.toString();
                        }

                        @Override
                        public Boolean decode(String value) {
                            if (!value.equalsIgnoreCase("true")
                                    && !value.equalsIgnoreCase("false")) {
                                throw new IllegalArgumentException("Expected true or false");
                            }
                            return Boolean.parseBoolean(value);
                        }
                    },
                    value -> true,
                    "team.settings.manage"
            ));
            teamService = new TeamServiceImpl(
                    store,
                    new TeamCache(),
                    databaseConfig.type() == DatabaseConfig.Type.SQLITE ? 1 : databaseConfig.poolSize(),
                    runtime,
                    registries,
                    event -> Bukkit.getPluginManager().callEvent(event),
                    database::leaseHeld
            );
            var api = new OpenTeamsImpl(teamService, teamService, registries, runtime);
            var localizedMessages = new LocalizedMessages(Locale.forLanguageTag(
                    getConfig().getString("ui.default-locale", "vi_VN").replace('_', '-')),
                    registries::translation,
                    getConfig().getBoolean("ui.follow-player-locale", false));
            teamChat = new TeamChatService(
                    this,
                    teamService,
                    new JdbcChatPreferenceStore(
                            database.dataSource(), database.namespace(), database),
                    getConfig().getString("chat.format",
                            "<aqua>[<tag>]</aqua> <white><player>:</white> <gray><message></gray>"),
                    localizedMessages);
            var chatInterface = new ChatTeamUserInterface(
                    this, teamService, localizedMessages);
            TeamUserInterface userInterface = switch (
                    getConfig().getString("ui.mode", "auto").toLowerCase(Locale.ROOT)) {
                case "chat" -> chatInterface;
                case "auto", "dialog" -> new DialogTeamUserInterface(
                        this,
                        teamService,
                        teamService,
                        chatInterface,
                        localizedMessages,
                        () -> registries.uiContributions().values().stream()
                                .map(ExtensionRegistries.OwnedUiAction::action)
                                .toList()
                );
                default -> throw new IllegalArgumentException("Unknown ui.mode");
            };
            var commands = new TeamCommands(
                    this,
                    teamService,
                    teamService,
                    userInterface,
                    registries,
                    database,
                    getConfig().getLong("audit.retention-days", 90) * 86_400_000L,
                    teamChat,
                    localizedMessages);

            getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
            {
                event.registrar().register(
                        commands.createTree(),
                        "OpenTeams team command",
                        List.of("teams")
                );
                event.registrar().register(
                        commands.createAdminTree(() -> !runtime.writable()),
                        "OpenTeams administration command",
                        List.of()
                );
            });

            Bukkit.getPluginManager().registerEvents(
                    new PlayerStateListener(teamService, teamChat), this);
            Bukkit.getPluginManager().registerEvents(new TeamChatListener(teamChat), this);
            Bukkit.getPluginManager().registerEvents(new FriendlyFireListener(
                    teamService,
                    getConfig().getString("friendly-fire.mode", "deny")
                            .equalsIgnoreCase("allow")), this);
            Bukkit.getPluginManager().registerEvents(new AddonLifecycleListener(registries), this);
            Bukkit.getServicesManager().register(OpenTeams.class, api, this, ServicePriority.Normal);

            Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, task -> {
                if (heartbeatExecutor.isShutdown()
                        || !heartbeatRunning.compareAndSet(false, true)) {
                    return;
                }
                var onlineIdsForRecovery = Bukkit.getOnlinePlayers().stream()
                        .map(org.bukkit.entity.Player::getUniqueId)
                        .toList();
                try {
                    heartbeatExecutor.execute(() -> {
                        try {
                            var healthy = database.heartbeat();
                            if (!healthy) {
                                var wasWritable = runtime.writable();
                                runtime.degrade();
                                if (wasWritable) {
                                    getLogger().severe(
                                            "Database lease lost; OpenTeams entered read-only mode.");
                                }
                            } else if (runtime.beginRecovery()) {
                                recoverOnlineCache(onlineIdsForRecovery);
                            }
                        } finally {
                            heartbeatRunning.set(false);
                        }
                    });
                } catch (java.util.concurrent.RejectedExecutionException exception) {
                    heartbeatRunning.set(false);
                }
            }, 300, 300);

            var onlineIds = Bukkit.getOnlinePlayers().stream()
                    .map(org.bukkit.entity.Player::getUniqueId)
                    .toList();
            onlineIds.forEach(teamChat::load);
            teamService.resync(onlineIds).whenComplete((ignored, error) -> {
                if (error == null) {
                    runtime.writableAfterStartup();
                } else {
                    runtime.degrade();
                    getLogger().log(Level.SEVERE, "Initial cache load failed", error);
                }
            });
            getLogger().info("OpenTeams enabled with API " + api.apiVersion()
                    + " and database namespace '" + database.namespace() + "'.");
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "OpenTeams failed to initialize safely", exception);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        runtime.stopping();
        Bukkit.getServicesManager().unregisterAll(this);
        heartbeatExecutor.shutdown();
        try {
            if (!heartbeatExecutor.awaitTermination(
                    5, java.util.concurrent.TimeUnit.SECONDS)) {
                heartbeatExecutor.shutdownNow();
                heartbeatExecutor.awaitTermination(
                        5, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            heartbeatExecutor.shutdownNow();
        }
        if (teamChat != null) {
            teamChat.close();
        }
        if (teamService != null) {
            teamService.close();
        }
        if (database != null) {
            database.close();
        }
    }

    private void recoverOnlineCache(java.util.List<java.util.UUID> onlineIds) {
        teamService.resync(onlineIds).whenComplete((ignored, error) -> {
            if (error == null && database.leaseHeld()) {
                runtime.recoverySucceeded();
                getLogger().info("Database lease and cache recovered; mutations are enabled.");
            } else {
                runtime.recoveryFailed();
                getLogger().log(Level.SEVERE, "Database recovery failed", error);
            }
        });
    }
}
