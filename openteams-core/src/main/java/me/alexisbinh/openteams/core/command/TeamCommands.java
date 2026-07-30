package me.alexisbinh.openteams.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.UUID;
import me.alexisbinh.openteams.api.OperationResult;
import me.alexisbinh.openteams.api.TeamId;
import me.alexisbinh.openteams.api.TeamRequests;
import me.alexisbinh.openteams.api.TeamService;
import me.alexisbinh.openteams.api.TeamSnapshot;
import me.alexisbinh.openteams.ui.TeamUserInterface;
import me.alexisbinh.openteams.core.extension.ExtensionRegistries;
import me.alexisbinh.openteams.core.database.DatabaseManager;
import me.alexisbinh.openteams.core.chat.TeamChatService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class TeamCommands {
    private final Plugin plugin;
    private final TeamService teams;
    private final TeamUserInterface userInterface;
    private final ExtensionRegistries extensions;
    private final DatabaseManager database;
    private final long auditRetentionMillis;
    private final TeamChatService chat;

    public TeamCommands(
            Plugin plugin,
            TeamService teams,
            TeamUserInterface userInterface,
            ExtensionRegistries extensions,
            DatabaseManager database,
            long auditRetentionMillis,
            TeamChatService chat
    ) {
        this.plugin = plugin;
        this.teams = teams;
        this.userInterface = userInterface;
        this.extensions = extensions;
        this.database = database;
        this.auditRetentionMillis = auditRetentionMillis;
        this.chat = chat;
    }

    public com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> createTree() {
        return Commands.literal("team")
                .requires(source -> source.getSender().hasPermission("openteams.command.team"))
                .executes(context -> dashboard(context.getSource()))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> create(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("info")
                        .executes(context -> info(context.getSource())))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(context -> invite(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("accept")
                        .then(Commands.argument("team-id", StringArgumentType.word())
                                .executes(context -> accept(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "team-id")))))
                .then(Commands.literal("request")
                        .then(Commands.argument("team-id", StringArgumentType.word())
                                .executes(context -> requestJoin(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "team-id")))))
                .then(Commands.literal("approve")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(context -> targetAction(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        TargetOperation.APPROVE))))
                .then(Commands.literal("leave")
                        .executes(context -> leave(context.getSource())))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(context -> targetAction(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        TargetOperation.KICK))))
                .then(Commands.literal("transfer")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(context -> targetAction(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        TargetOperation.TRANSFER))))
                .then(Commands.literal("ban")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(context -> ban(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        ""))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(context -> ban(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "reason"))))))
                .then(Commands.literal("unban")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(context -> targetAction(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        TargetOperation.UNBAN))))
                .then(Commands.literal("role")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .executes(context -> changeRole(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "role"))))))
                .then(Commands.literal("setting")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(context -> setting(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "key"),
                                                StringArgumentType.getString(context, "value"))))))
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> rename(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("tag")
                        .then(Commands.argument("tag", StringArgumentType.word())
                                .executes(context -> tag(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "tag")))))
                .then(Commands.literal("disband")
                        .then(Commands.literal("confirm")
                                .executes(context -> disband(context.getSource()))))
                .then(Commands.literal("chat")
                        .executes(context -> toggleChat(context.getSource()))
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(context -> chat(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "message")))))
                .then(Commands.argument("extension", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            extensions.commandContributions().keySet().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> extension(
                                context.getSource(),
                                StringArgumentType.getString(context, "extension"),
                                ""))
                        .then(Commands.argument("arguments", StringArgumentType.greedyString())
                                .executes(context -> extension(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "extension"),
                                        StringArgumentType.getString(context, "arguments")))))
                .build();
    }

    public com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> createAdminTree(
            java.util.function.BooleanSupplier readOnly
    ) {
        return Commands.literal("teamadmin")
                .requires(source -> source.getSender().hasPermission("openteams.admin"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(Component.text(
                            "OpenTeams · database mode: " + (readOnly.getAsBoolean() ? "READ_ONLY" : "WRITABLE")
                                    + " · UI: " + userInterface.mode()
                                    + " · addons: " + extensions.commandContributions().size(),
                            readOnly.getAsBoolean() ? NamedTextColor.RED : NamedTextColor.GREEN));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("doctor")
                        .executes(context -> doctor(context.getSource().getSender())))
                .then(Commands.literal("cleanup")
                        .then(Commands.literal("confirm")
                                .executes(context -> cleanup(
                                        context.getSource().getSender()))))
                .then(Commands.literal("spy")
                        .requires(source -> source.getSender().hasPermission(
                                "openteams.admin.spy"))
                        .executes(context -> toggleSpy(context.getSource())))
                .build();
    }

    private int dashboard(CommandSourceStack source) {
        if (source.getSender() instanceof Player player) {
            userInterface.openDashboard(player);
        } else {
            source.getSender().sendMessage(Component.text("Use /team info from a player."));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int doctor(CommandSender sender) {
        sender.sendMessage(Component.text("OpenTeams doctor is running…",
                NamedTextColor.GRAY));
        Thread.startVirtualThread(() -> {
            try {
                var report = database.doctor();
                dispatch(sender, () -> sender.sendMessage(Component.text(
                        "Doctor " + (report.healthy() ? "OK" : "FAILED")
                                + " · missing owner=" + report.activeTeamsWithoutOwnerMember()
                                + " · wrong owner role=" + report.ownersWithWrongRole()
                                + " · dangling members=" + report.danglingMembers()
                                + " · expired invite/request/ban="
                                + report.expiredInvitations() + "/"
                                + report.expiredJoinRequests() + "/"
                                + report.expiredBans()
                                + " · audit rows=" + report.auditRows(),
                        report.healthy() ? NamedTextColor.GREEN : NamedTextColor.RED)));
            } catch (Exception exception) {
                dispatch(sender, () -> sender.sendMessage(error(
                        "Doctor failed: " + exception.getMessage())));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private int cleanup(CommandSender sender) {
        sender.sendMessage(Component.text("OpenTeams cleanup is running…",
                NamedTextColor.GRAY));
        Thread.startVirtualThread(() -> {
            try {
                var report = database.cleanupExpired(auditRetentionMillis);
                dispatch(sender, () -> sender.sendMessage(Component.text(
                        "Cleanup complete · invitations=" + report.invitations()
                                + " · requests=" + report.joinRequests()
                                + " · bans=" + report.bans()
                                + " · audit=" + report.auditRows(),
                        NamedTextColor.GREEN)));
            } catch (Exception exception) {
                dispatch(sender, () -> sender.sendMessage(error(
                        "Cleanup failed: " + exception.getMessage())));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private int create(CommandSourceStack source, String name) {
        var player = player(source);
        if (player == null) {
            return 0;
        }
        respond(player, teams.create(new TeamRequests.Create(player.getUniqueId(), name, null)));
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandSourceStack source) {
        var player = player(source);
        if (player == null) {
            return 0;
        }
        teams.findByPlayer(player.getUniqueId()).thenAccept(result ->
                dispatch(player, () -> {
                    if (result.isEmpty()) {
                        player.sendMessage(error("You are not in a team."));
                    } else {
                        sendInfo(player, result.get());
                    }
                }));
        return Command.SINGLE_SUCCESS;
    }

    private int invite(CommandSourceStack source, String playerName) {
        var actor = player(source);
        if (actor == null) {
            return 0;
        }
        var target = Bukkit.getPlayerExact(playerName);
        var team = teams.findByPlayerCached(actor.getUniqueId());
        if (target == null || team.isEmpty()) {
            actor.sendMessage(error(target == null ? "Player is not online." : "You are not in a team."));
            return 0;
        }
        respond(actor, teams.invite(new TeamRequests.TargetAction(
                actor.getUniqueId(), team.get().id(), target.getUniqueId())));
        return Command.SINGLE_SUCCESS;
    }

    private int accept(CommandSourceStack source, String teamId) {
        var player = player(source);
        if (player == null) {
            return 0;
        }
        try {
            var id = TeamId.parse(teamId);
            respond(player, teams.acceptInvitation(new TeamRequests.TargetAction(
                    player.getUniqueId(), id, player.getUniqueId())));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(error("Invalid team ID."));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private int requestJoin(CommandSourceStack source, String teamId) {
        var player = player(source);
        if (player == null) {
            return 0;
        }
        try {
            respond(player, teams.requestJoin(new TeamRequests.TeamAction(
                    player.getUniqueId(), TeamId.parse(teamId))));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(error("Invalid team ID."));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private int leave(CommandSourceStack source) {
        var player = player(source);
        if (player == null) {
            return 0;
        }
        var team = teams.findByPlayerCached(player.getUniqueId());
        if (team.isEmpty()) {
            player.sendMessage(error("You are not in a team."));
            return 0;
        }
        respond(player, teams.leave(new TeamRequests.TeamAction(
                player.getUniqueId(), team.get().id())));
        return Command.SINGLE_SUCCESS;
    }

    private int targetAction(CommandSourceStack source, String playerName, TargetOperation operation) {
        var actor = player(source);
        if (actor == null) {
            return 0;
        }
        var target = Bukkit.getPlayerExact(playerName);
        var team = teams.findByPlayerCached(actor.getUniqueId());
        if (target == null || team.isEmpty()) {
            actor.sendMessage(error(target == null ? "Player is not online." : "You are not in a team."));
            return 0;
        }
        var request = new TeamRequests.TargetAction(
                actor.getUniqueId(), team.get().id(), target.getUniqueId());
        respond(actor, switch (operation) {
            case KICK -> teams.kick(request);
            case TRANSFER -> teams.transferOwnership(request);
            case APPROVE -> teams.acceptJoinRequest(request);
            case UNBAN -> teams.unban(request);
        });
        return Command.SINGLE_SUCCESS;
    }

    private int ban(CommandSourceStack source, String playerName, String reason) {
        var actor = player(source);
        if (actor == null) {
            return 0;
        }
        var target = Bukkit.getPlayerExact(playerName);
        var team = teams.findByPlayerCached(actor.getUniqueId());
        if (target == null || team.isEmpty()) {
            actor.sendMessage(error(target == null ? "Player is not online." : "You are not in a team."));
            return 0;
        }
        respond(actor, teams.ban(new TeamRequests.Ban(
                actor.getUniqueId(), team.get().id(), target.getUniqueId(), reason)));
        return Command.SINGLE_SUCCESS;
    }

    private int changeRole(CommandSourceStack source, String playerName, String role) {
        var actor = player(source);
        if (actor == null) {
            return 0;
        }
        var target = Bukkit.getPlayerExact(playerName);
        var team = teams.findByPlayerCached(actor.getUniqueId());
        if (target == null || team.isEmpty()) {
            actor.sendMessage(error(target == null ? "Player is not online." : "You are not in a team."));
            return 0;
        }
        respond(actor, teams.changeRole(new TeamRequests.ChangeRole(
                actor.getUniqueId(), team.get().id(), target.getUniqueId(),
                role.toLowerCase(java.util.Locale.ROOT))));
        return Command.SINGLE_SUCCESS;
    }

    private int setting(CommandSourceStack source, String key, String value) {
        var actor = player(source);
        var team = actor == null ? java.util.Optional.<TeamSnapshot>empty()
                : teams.findByPlayerCached(actor.getUniqueId());
        if (actor == null || team.isEmpty()) {
            if (actor != null) {
                actor.sendMessage(error("You are not in a team."));
            }
            return 0;
        }
        var namespacedKey = key.contains(":") ? key : "openteams:" + key;
        respond(actor, teams.setSetting(new TeamRequests.SetSetting(
                actor.getUniqueId(), team.get().id(), namespacedKey, value)));
        return Command.SINGLE_SUCCESS;
    }

    private int rename(CommandSourceStack source, String name) {
        var actor = player(source);
        var team = actor == null ? java.util.Optional.<TeamSnapshot>empty()
                : teams.findByPlayerCached(actor.getUniqueId());
        if (actor == null || team.isEmpty()) {
            if (actor != null) {
                actor.sendMessage(error("You are not in a team."));
            }
            return 0;
        }
        respond(actor, teams.rename(new TeamRequests.Rename(
                actor.getUniqueId(), team.get().id(), name)));
        return Command.SINGLE_SUCCESS;
    }

    private int tag(CommandSourceStack source, String tag) {
        var actor = player(source);
        var team = actor == null ? java.util.Optional.<TeamSnapshot>empty()
                : teams.findByPlayerCached(actor.getUniqueId());
        if (actor == null || team.isEmpty()) {
            if (actor != null) {
                actor.sendMessage(error("You are not in a team."));
            }
            return 0;
        }
        respond(actor, teams.setTag(new TeamRequests.SetTag(
                actor.getUniqueId(), team.get().id(), tag)));
        return Command.SINGLE_SUCCESS;
    }

    private int disband(CommandSourceStack source) {
        var actor = player(source);
        var team = actor == null ? java.util.Optional.<TeamSnapshot>empty()
                : teams.findByPlayerCached(actor.getUniqueId());
        if (actor == null || team.isEmpty()) {
            if (actor != null) {
                actor.sendMessage(error("You are not in a team."));
            }
            return 0;
        }
        respond(actor, teams.disband(new TeamRequests.TeamAction(
                actor.getUniqueId(), team.get().id())));
        return Command.SINGLE_SUCCESS;
    }

    private int chat(CommandSourceStack source, String message) {
        var player = player(source);
        if (player == null) {
            return 0;
        }
        chat.broadcast(player, Component.text(message));
        return Command.SINGLE_SUCCESS;
    }

    private int toggleChat(CommandSourceStack source) {
        var player = player(source);
        if (player == null) {
            return 0;
        }
        chat.toggleTeamChat(player.getUniqueId()).whenComplete((enabled, failure) ->
                dispatch(player, () -> player.sendMessage(failure == null
                        ? Component.text("Team chat " + (enabled ? "enabled." : "disabled."),
                                NamedTextColor.GREEN)
                        : error("Could not persist team chat preference."))));
        return Command.SINGLE_SUCCESS;
    }

    private int toggleSpy(CommandSourceStack source) {
        var player = player(source);
        if (player == null) {
            return 0;
        }
        chat.toggleSpy(player.getUniqueId()).whenComplete((enabled, failure) ->
                dispatch(player, () -> player.sendMessage(failure == null
                        ? Component.text("Team chat spy " + (enabled ? "enabled." : "disabled."),
                                NamedTextColor.GREEN)
                        : error("Could not persist spy preference."))));
        return Command.SINGLE_SUCCESS;
    }

    private int extension(CommandSourceStack source, String name, String arguments) {
        var owned = extensions.commandContributions().get(name.toLowerCase(java.util.Locale.ROOT));
        if (owned == null) {
            source.getSender().sendMessage(error("Unknown team subcommand: " + name));
            return 0;
        }
        var contribution = owned.contribution();
        if (!source.getSender().hasPermission(contribution.permission())) {
            source.getSender().sendMessage(error("You do not have permission."));
            return 0;
        }
        var parsed = arguments.isBlank() ? new String[0] : arguments.split("\\s+");
        contribution.handler().execute(source.getSender(), parsed)
                .exceptionally(error -> {
                    dispatch(source.getSender(), () -> source.getSender().sendMessage(
                            TeamCommands.error("Addon command failed.")));
                    plugin.getLogger().warning("Addon command '" + name + "' failed: " + error);
                    return 0;
                });
        return Command.SINGLE_SUCCESS;
    }

    private void respond(
            Player player,
            java.util.concurrent.CompletionStage<OperationResult<TeamSnapshot>> stage
    ) {
        stage.thenAccept(result -> dispatch(player, () -> {
            if (result instanceof OperationResult.Success<TeamSnapshot> success) {
                player.sendMessage(Component.text("Team updated: " + success.value().name(),
                        NamedTextColor.GREEN));
            } else if (result instanceof OperationResult.Failure<TeamSnapshot> failure) {
                player.sendMessage(error("Operation failed: " + failure.code().name()
                        + " · correlation " + failure.correlationId()));
            }
        }));
    }

    private void sendInfo(CommandSender sender, TeamSnapshot snapshot) {
        sender.sendMessage(Component.text(snapshot.name(), NamedTextColor.AQUA)
                .append(Component.text(" [" + (snapshot.tag() == null ? "—" : snapshot.tag()) + "]",
                        NamedTextColor.GRAY)));
        sender.sendMessage(Component.text(
                "Members: " + snapshot.members().size() + "/" + snapshot.memberLimit()
                        + " · ID: " + snapshot.id(),
                NamedTextColor.GRAY));
    }

    private Player player(CommandSourceStack source) {
        if (source.getSender() instanceof Player player) {
            return player;
        }
        source.getSender().sendMessage(error("This command requires a player."));
        return null;
    }

    private void dispatch(Player player, Runnable action) {
        player.getScheduler().run(plugin, task -> action.run(), null);
    }

    private void dispatch(CommandSender sender, Runnable action) {
        if (sender instanceof Player player) {
            dispatch(player, action);
        } else {
            Bukkit.getGlobalRegionScheduler().execute(plugin, action);
        }
    }

    private static Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }

    private enum TargetOperation {
        KICK,
        TRANSFER,
        APPROVE,
        UNBAN
    }
}
