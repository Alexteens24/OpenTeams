package me.alexisbinh.openteams.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.UUID;
import me.alexisbinh.openteams.api.OperationResult;
import me.alexisbinh.openteams.api.PlayerDirectory;
import me.alexisbinh.openteams.api.TeamId;
import me.alexisbinh.openteams.api.TeamRequests;
import me.alexisbinh.openteams.api.TeamService;
import me.alexisbinh.openteams.api.TeamSnapshot;
import me.alexisbinh.openteams.ui.TeamUserInterface;
import me.alexisbinh.openteams.ui.LocalizedMessages;
import me.alexisbinh.openteams.core.extension.ExtensionRegistries;
import me.alexisbinh.openteams.core.database.DatabaseManager;
import me.alexisbinh.openteams.core.chat.TeamChatService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class TeamCommands {
    private final Plugin plugin;
    private final TeamService teams;
    private final PlayerDirectory players;
    private final TeamUserInterface userInterface;
    private final ExtensionRegistries extensions;
    private final DatabaseManager database;
    private final long auditRetentionMillis;
    private final TeamChatService chat;
    private final LocalizedMessages messages;

    public TeamCommands(
            Plugin plugin,
            TeamService teams,
            PlayerDirectory players,
            TeamUserInterface userInterface,
            ExtensionRegistries extensions,
            DatabaseManager database,
            long auditRetentionMillis,
            TeamChatService chat,
            LocalizedMessages messages
    ) {
        this.plugin = plugin;
        this.teams = teams;
        this.players = players;
        this.userInterface = userInterface;
        this.extensions = extensions;
        this.database = database;
        this.auditRetentionMillis = auditRetentionMillis;
        this.chat = chat;
        this.messages = messages;
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
                .then(Commands.literal("members")
                        .executes(context -> listMembers(context.getSource())))
                .then(Commands.literal("requests")
                        .executes(context -> listRequests(context.getSource())))
                .then(Commands.literal("sent")
                        .executes(context -> listSentInvitations(context.getSource())))
                .then(Commands.literal("bans")
                        .executes(context -> listBans(context.getSource())))
                .then(Commands.literal("settings")
                        .executes(context -> listSettings(context.getSource())))
                .then(Commands.literal("explore")
                        .executes(context -> explore(context.getSource(), ""))
                        .then(Commands.argument("query", StringArgumentType.greedyString())
                                .executes(context -> explore(context.getSource(),
                                        StringArgumentType.getString(context, "query")))))
                .then(Commands.literal("invitations")
                        .executes(context -> invitations(context.getSource())))
                .then(Commands.literal("myrequests")
                        .executes(context -> listMyRequests(context.getSource())))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> suggestDirectory(builder))
                                .executes(context -> invite(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("accept")
                        .then(Commands.argument("team-id", StringArgumentType.word())
                                .suggests((context, builder) -> suggestInvitations(context.getSource(), builder))
                                .executes(context -> accept(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "team-id")))))
                .then(Commands.literal("decline")
                        .then(Commands.argument("team-id", StringArgumentType.word())
                                .suggests((context, builder) -> suggestInvitations(context.getSource(), builder))
                                .executes(context -> invitationAction(context.getSource(),
                                        StringArgumentType.getString(context, "team-id"), false))))
                .then(Commands.literal("request")
                        .then(Commands.argument("team-id", StringArgumentType.word())
                                .suggests((context, builder) -> teams.searchPublicTeams("", 0, 20)
                                        .thenApply(page -> {
                                            page.items().forEach(team -> builder.suggest(team.id().toString()));
                                            return builder.build();
                                        }).toCompletableFuture())
                                .executes(context -> requestJoin(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "team-id")))))
                .then(Commands.literal("approve")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> suggestRequests(context.getSource(), builder))
                                .executes(context -> targetAction(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        TargetOperation.APPROVE))))
                .then(Commands.literal("reject")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> suggestRequests(context.getSource(), builder))
                                .executes(context -> targetAction(context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        TargetOperation.REJECT))))
                .then(Commands.literal("revoke")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> suggestOutgoing(context.getSource(), builder))
                                .executes(context -> targetAction(context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        TargetOperation.REVOKE))))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("team-id", StringArgumentType.word())
                                .suggests((context, builder) -> suggestMyRequests(context.getSource(), builder))
                                .executes(context -> cancelRequest(context.getSource(),
                                        StringArgumentType.getString(context, "team-id")))))
                .then(Commands.literal("leave")
                        .executes(context -> leave(context.getSource())))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> suggestMembers(context.getSource(), builder))
                                .executes(context -> targetAction(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        TargetOperation.KICK))))
                .then(Commands.literal("transfer")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> suggestMembers(context.getSource(), builder))
                                .executes(context -> targetAction(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        TargetOperation.TRANSFER))))
                .then(Commands.literal("ban")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> suggestDirectory(builder))
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
                                .suggests((context, builder) -> suggestBans(context.getSource(), builder))
                                .executes(context -> targetAction(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        TargetOperation.UNBAN))))
                .then(Commands.literal("role")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> suggestMembers(context.getSource(), builder))
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .suggests((context, builder) -> teams.roles().thenApply(roles -> {
                                            return suggestAssignableRoles(context.getSource(), builder, roles);
                                        }).toCompletableFuture())
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
                .then(Commands.literal("visibility")
                        .then(Commands.argument("value", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    builder.suggest("public");
                                    builder.suggest("private");
                                    return builder.buildFuture();
                                })
                                .executes(context -> visibility(context.getSource(),
                                        StringArgumentType.getString(context, "value"), false))
                                .then(Commands.literal("confirm")
                                        .executes(context -> visibility(context.getSource(),
                                                StringArgumentType.getString(context, "value"), true)))))
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
        respond(player, "success.created",
                teams.create(new TeamRequests.Create(player.getUniqueId(), name, null)));
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandSourceStack source) {
        var player = player(source);
        if (player == null) {
            return 0;
        }
        teams.loadMembership(player.getUniqueId()).thenAccept(result ->
                dispatch(player, () -> {
                    if (result.status() != me.alexisbinh.openteams.api.MembershipLookup.Status.PRESENT) {
                        player.sendMessage(messages.component(player, "error.not-in-team"));
                    } else {
                        sendInfo(player, result.optionalTeam().orElseThrow());
                    }
                }));
        return Command.SINGLE_SUCCESS;
    }

    private int listMembers(CommandSourceStack source) {
        var actor = player(source);
        if (actor == null) return 0;
        var team = team(actor);
        if (team == null) return 0;
        memberCandidates(team).whenComplete((items, failure) -> dispatch(actor, () -> {
            if (failure != null) {
                actor.sendMessage(messages.component(actor, "error.database_unavailable"));
                return;
            }
            actor.sendMessage(messages.component(actor, "members.title"));
            var byId = team.members().stream().collect(java.util.stream.Collectors.toMap(
                    me.alexisbinh.openteams.api.TeamMemberSnapshot::playerId,
                    java.util.function.Function.identity()));
            items.forEach(item -> actor.sendMessage(Component.text(item.lastKnownName()
                    + " · " + byId.get(item.playerId()).roleKey(), NamedTextColor.WHITE)));
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int listRequests(CommandSourceStack source) {
        var actor = player(source);
        if (actor == null) return 0;
        var team = team(actor);
        if (team == null || !teams.hasPermissionCached(actor.getUniqueId(),
                "team.join-request.accept")) return forbidden(actor);
        teams.joinRequests(team.id()).whenComplete((items, failure) -> dispatch(actor, () -> {
            if (failure != null) actor.sendMessage(messages.component(actor, "error.database_unavailable"));
            else if (items.isEmpty()) actor.sendMessage(messages.component(actor, "requests.empty"));
            else items.forEach(item -> actor.sendMessage(Component.text(item.player().lastKnownName())
                    .append(Component.text(" [Accept]", NamedTextColor.GREEN).clickEvent(
                            ClickEvent.runCommand("/team approve " + item.player().playerId())))
                    .append(Component.text(" [Reject]", NamedTextColor.RED).clickEvent(
                            ClickEvent.runCommand("/team reject " + item.player().playerId())))));
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int listSentInvitations(CommandSourceStack source) {
        var actor = player(source);
        if (actor == null) return 0;
        var team = team(actor);
        if (team == null || !teams.hasPermissionCached(actor.getUniqueId(), "team.invite"))
            return forbidden(actor);
        teams.outgoingInvitations(team.id()).whenComplete((items, failure) -> dispatch(actor, () -> {
            if (failure != null) actor.sendMessage(messages.component(actor, "error.database_unavailable"));
            else if (items.isEmpty()) actor.sendMessage(messages.component(actor, "sent-invitations.empty"));
            else items.forEach(item -> actor.sendMessage(Component.text(item.player().lastKnownName())
                    .append(Component.text(" [Revoke]", NamedTextColor.RED).clickEvent(
                            ClickEvent.runCommand("/team revoke " + item.player().playerId())))));
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int listMyRequests(CommandSourceStack source) {
        var actor = player(source);
        if (actor == null) return 0;
        teams.joinRequestsByPlayer(actor.getUniqueId()).whenComplete((items, failure) ->
                dispatch(actor, () -> {
                    if (failure != null) actor.sendMessage(messages.component(
                            actor, "error.database_unavailable"));
                    else if (items.isEmpty()) actor.sendMessage(messages.component(
                            actor, "my-requests.empty"));
                    else items.forEach(item -> actor.sendMessage(Component.text(item.team().name())
                            .append(Component.text(" [Cancel]", NamedTextColor.RED).clickEvent(
                                    ClickEvent.runCommand("/team cancel " + item.team().id())))));
                }));
        return Command.SINGLE_SUCCESS;
    }

    private int cancelRequest(CommandSourceStack source, String teamId) {
        var actor = player(source);
        if (actor == null) return 0;
        try {
            respond(actor, "success.request-cancelled", teams.cancelJoinRequest(
                    new TeamRequests.TeamAction(actor.getUniqueId(), TeamId.parse(teamId))));
        } catch (IllegalArgumentException exception) {
            actor.sendMessage(messages.component(actor, "error.invalid-team-id"));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private int listBans(CommandSourceStack source) {
        var actor = player(source);
        if (actor == null) return 0;
        var team = team(actor);
        if (team == null || !teams.hasPermissionCached(actor.getUniqueId(), "team.ban"))
            return forbidden(actor);
        teams.bans(team.id()).whenComplete((items, failure) -> dispatch(actor, () -> {
            if (failure != null) actor.sendMessage(messages.component(actor, "error.database_unavailable"));
            else if (items.isEmpty()) actor.sendMessage(messages.component(actor, "bans.empty"));
            else items.forEach(item -> actor.sendMessage(Component.text(item.player().lastKnownName())
                    .append(Component.text(" [Unban]", NamedTextColor.GREEN).clickEvent(
                            ClickEvent.runCommand("/team unban " + item.player().playerId())))));
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int listSettings(CommandSourceStack source) {
        var actor = player(source);
        if (actor == null) return 0;
        var snapshot = team(actor);
        if (snapshot == null) return 0;
        var line = Component.empty();
        if (teams.hasPermissionCached(actor.getUniqueId(), "team.rename")) line = line.append(
                Component.text("[Rename]", NamedTextColor.YELLOW).clickEvent(
                        ClickEvent.suggestCommand("/team rename "))).append(Component.space());
        if (teams.hasPermissionCached(actor.getUniqueId(), "team.settings.manage")) line = line
                .append(Component.text("[Tag]", NamedTextColor.YELLOW).clickEvent(
                        ClickEvent.suggestCommand("/team tag "))).append(Component.space())
                .append(Component.text(snapshot.visibility() == me.alexisbinh.openteams.api.TeamVisibility.PUBLIC
                                ? "[Make private]" : "[Make public]", NamedTextColor.YELLOW)
                        .clickEvent(ClickEvent.runCommand("/team visibility "
                                + (snapshot.visibility() == me.alexisbinh.openteams.api.TeamVisibility.PUBLIC
                                ? "private" : "public")))).append(Component.space());
        if (teams.hasPermissionCached(actor.getUniqueId(), "team.ban")) line = line.append(
                Component.text("[Bans]", NamedTextColor.YELLOW).clickEvent(
                        ClickEvent.runCommand("/team bans")));
        if (line.equals(Component.empty()) && !snapshot.ownerId().equals(actor.getUniqueId()))
            return forbidden(actor);
        actor.sendMessage(messages.component(actor, "settings.title"));
        actor.sendMessage(line);
        return Command.SINGLE_SUCCESS;
    }

    private int explore(CommandSourceStack source, String query) {
        var player = player(source);
        if (player == null) return 0;
        teams.searchPublicTeams(query, 0, 10).whenComplete((page, failure) -> dispatch(player, () -> {
            if (failure != null) {
                player.sendMessage(messages.component(player, "error.public-teams-load"));
                return;
            }
            if (page.items().isEmpty()) {
                player.sendMessage(messages.component(player, "explore.empty"));
                return;
            }
            player.sendMessage(messages.component(player, "explore.title"));
            page.items().forEach(team -> player.sendMessage(Component.text(
                            team.name() + " [" + (team.tag() == null ? "—" : team.tag()) + "] · "
                                    + team.memberCount() + "/" + team.memberLimit(), NamedTextColor.WHITE)
                    .append(messages.component(player, "command.request-button")
                            .clickEvent(ClickEvent.runCommand("/team request " + team.id())))));
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int invitations(CommandSourceStack source) {
        var player = player(source);
        if (player == null) return 0;
        teams.invitations(player.getUniqueId()).whenComplete((items, failure) -> dispatch(player, () -> {
            if (failure != null) {
                player.sendMessage(messages.component(player, "error.invitations-load"));
                return;
            }
            if (items.isEmpty()) {
                player.sendMessage(messages.component(player, "invitations.empty"));
                return;
            }
            items.forEach(item -> player.sendMessage(Component.text(item.team().name(), NamedTextColor.AQUA)
                    .append(messages.component(player, "command.accept-button")
                            .clickEvent(ClickEvent.runCommand("/team accept " + item.team().id())))
                    .append(messages.component(player, "command.decline-button")
                            .clickEvent(ClickEvent.runCommand("/team decline " + item.team().id())))));
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int invitationAction(CommandSourceStack source, String teamId, boolean accept) {
        var player = player(source);
        if (player == null) return 0;
        try {
            var request = new TeamRequests.TargetAction(player.getUniqueId(), TeamId.parse(teamId),
                    player.getUniqueId());
            respond(player, accept ? "success.joined" : "success.declined",
                    accept ? teams.acceptInvitation(request) : teams.declineInvitation(request));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(messages.component(player, "error.invalid-team-id"));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private int invite(CommandSourceStack source, String playerName) {
        var actor = player(source);
        if (actor == null) return 0;
        var team = teams.membershipCached(actor.getUniqueId()).optionalTeam();
        if (team.isEmpty()) {
            actor.sendMessage(messages.component(actor, "error.not-in-team"));
            return 0;
        }
        resolveTarget(actor, playerName, directoryCandidates(playerName), targetId ->
                respond(actor, "success.invited", teams.invite(new TeamRequests.TargetAction(
                        actor.getUniqueId(), team.get().id(), targetId))));
        return Command.SINGLE_SUCCESS;
    }

    private int accept(CommandSourceStack source, String teamId) {
        var player = player(source);
        if (player == null) {
            return 0;
        }
        try {
            var id = TeamId.parse(teamId);
            respond(player, "success.joined", teams.acceptInvitation(new TeamRequests.TargetAction(
                    player.getUniqueId(), id, player.getUniqueId())));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(messages.component(player, "error.invalid-team-id"));
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
            respond(player, "success.requested", teams.requestJoin(new TeamRequests.TeamAction(
                    player.getUniqueId(), TeamId.parse(teamId))));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(messages.component(player, "error.invalid-team-id"));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private int leave(CommandSourceStack source) {
        var player = player(source);
        if (player == null) {
            return 0;
        }
        var team = teams.membershipCached(player.getUniqueId()).optionalTeam();
        if (team.isEmpty()) {
            player.sendMessage(messages.component(player, "error.not-in-team"));
            return 0;
        }
        if (team.get().ownerId().equals(player.getUniqueId())) {
            player.sendMessage(messages.component(player, "notice.owner-cannot-leave"));
            return 0;
        }
        respond(player, "success.left", teams.leave(new TeamRequests.TeamAction(
                player.getUniqueId(), team.get().id())));
        return Command.SINGLE_SUCCESS;
    }

    private int targetAction(CommandSourceStack source, String playerName, TargetOperation operation) {
        var actor = player(source);
        if (actor == null) {
            return 0;
        }
        var team = teams.membershipCached(actor.getUniqueId()).optionalTeam();
        if (team.isEmpty()) {
            actor.sendMessage(messages.component(actor, "error.not-in-team"));
            return 0;
        }
        var candidates = switch (operation) {
            case KICK, TRANSFER -> memberCandidates(team.get());
            case APPROVE, REJECT -> teams.joinRequests(team.get().id()).thenApply(items ->
                    items.stream().map(me.alexisbinh.openteams.api.TeamDirectory.JoinRequest::player).toList());
            case UNBAN -> teams.bans(team.get().id()).thenApply(items ->
                    items.stream().map(me.alexisbinh.openteams.api.TeamDirectory.Ban::player).toList());
            case REVOKE -> teams.outgoingInvitations(team.get().id()).thenApply(items ->
                    items.stream().map(me.alexisbinh.openteams.api.TeamDirectory.OutgoingInvitation::player)
                            .toList());
        };
        resolveTarget(actor, playerName, candidates, targetId -> {
            var request = new TeamRequests.TargetAction(
                    actor.getUniqueId(), team.get().id(), targetId);
            var stage = switch (operation) {
                case KICK -> teams.kick(request);
                case TRANSFER -> teams.transferOwnership(request);
                case APPROVE -> teams.acceptJoinRequest(request);
                case REJECT -> teams.rejectJoinRequest(request);
                case UNBAN -> teams.unban(request);
                case REVOKE -> teams.revokeInvitation(request);
            };
            var success = switch (operation) {
                case KICK -> "success.kicked";
                case TRANSFER -> "success.transferred";
                case APPROVE -> "success.request-accepted";
                case REJECT -> "success.request-rejected";
                case UNBAN -> "success.unbanned";
                case REVOKE -> "success.revoked";
            };
            respond(actor, success, stage);
        });
        return Command.SINGLE_SUCCESS;
    }

    private int ban(CommandSourceStack source, String playerName, String reason) {
        var actor = player(source);
        if (actor == null) {
            return 0;
        }
        var team = teams.membershipCached(actor.getUniqueId()).optionalTeam();
        if (team.isEmpty()) {
            actor.sendMessage(messages.component(actor, "error.not-in-team"));
            return 0;
        }
        resolveTarget(actor, playerName, directoryCandidates(playerName), targetId ->
                respond(actor, "success.banned", teams.ban(new TeamRequests.Ban(
                        actor.getUniqueId(), team.get().id(), targetId, reason))));
        return Command.SINGLE_SUCCESS;
    }

    private int changeRole(CommandSourceStack source, String playerName, String role) {
        var actor = player(source);
        if (actor == null) {
            return 0;
        }
        var team = teams.membershipCached(actor.getUniqueId()).optionalTeam();
        if (team.isEmpty()) {
            actor.sendMessage(messages.component(actor, "error.not-in-team"));
            return 0;
        }
        resolveTarget(actor, playerName, memberCandidates(team.get()), targetId ->
                respond(actor, "success.role-changed", teams.changeRole(new TeamRequests.ChangeRole(
                        actor.getUniqueId(), team.get().id(), targetId,
                        role.toLowerCase(java.util.Locale.ROOT)))));
        return Command.SINGLE_SUCCESS;
    }

    private int setting(CommandSourceStack source, String key, String value) {
        var actor = player(source);
        var team = actor == null ? java.util.Optional.<TeamSnapshot>empty()
                : teams.membershipCached(actor.getUniqueId()).optionalTeam();
        if (actor == null || team.isEmpty()) {
            if (actor != null) {
                actor.sendMessage(messages.component(actor, "error.not-in-team"));
            }
            return 0;
        }
        var namespacedKey = key.contains(":") ? key : "openteams:" + key;
        respond(actor, "success.updated", teams.setSetting(new TeamRequests.SetSetting(
                actor.getUniqueId(), team.get().id(), namespacedKey, value)));
        return Command.SINGLE_SUCCESS;
    }

    private int visibility(CommandSourceStack source, String value, boolean confirmed) {
        var actor = player(source);
        var team = actor == null ? java.util.Optional.<TeamSnapshot>empty()
                : teams.membershipCached(actor.getUniqueId()).optionalTeam();
        if (actor == null || team.isEmpty()) return 0;
        try {
            var visibility = me.alexisbinh.openteams.api.TeamVisibility.valueOf(
                    value.toUpperCase(java.util.Locale.ROOT));
            if (visibility == me.alexisbinh.openteams.api.TeamVisibility.PRIVATE && !confirmed) {
                teams.joinRequests(team.get().id()).whenComplete((requests, failure) -> dispatch(actor, () -> {
                    if (failure != null) {
                        actor.sendMessage(messages.component(actor, "error.database_unavailable"));
                        return;
                    }
                    actor.sendMessage(messages.component(actor, "confirm.make-private",
                                    java.util.Map.of("count", Integer.toString(requests.size())))
                            .append(Component.space()).append(Component.text("[Confirm]",
                                    NamedTextColor.RED).clickEvent(ClickEvent.runCommand(
                                    "/team visibility private confirm"))));
                }));
                return Command.SINGLE_SUCCESS;
            }
            respond(actor, "success.visibility", teams.setVisibility(new TeamRequests.SetVisibility(
                    actor.getUniqueId(), team.get().id(), visibility)));
        } catch (IllegalArgumentException exception) {
            actor.sendMessage(messages.component(actor, "error.visibility-value"));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private int rename(CommandSourceStack source, String name) {
        var actor = player(source);
        var team = actor == null ? java.util.Optional.<TeamSnapshot>empty()
                : teams.membershipCached(actor.getUniqueId()).optionalTeam();
        if (actor == null || team.isEmpty()) {
            if (actor != null) {
                actor.sendMessage(messages.component(actor, "error.not-in-team"));
            }
            return 0;
        }
        respond(actor, "success.renamed", teams.rename(new TeamRequests.Rename(
                actor.getUniqueId(), team.get().id(), name)));
        return Command.SINGLE_SUCCESS;
    }

    private int tag(CommandSourceStack source, String tag) {
        var actor = player(source);
        var team = actor == null ? java.util.Optional.<TeamSnapshot>empty()
                : teams.membershipCached(actor.getUniqueId()).optionalTeam();
        if (actor == null || team.isEmpty()) {
            if (actor != null) {
                actor.sendMessage(messages.component(actor, "error.not-in-team"));
            }
            return 0;
        }
        respond(actor, "success.tagged", teams.setTag(new TeamRequests.SetTag(
                actor.getUniqueId(), team.get().id(), tag)));
        return Command.SINGLE_SUCCESS;
    }

    private int disband(CommandSourceStack source) {
        var actor = player(source);
        var team = actor == null ? java.util.Optional.<TeamSnapshot>empty()
                : teams.membershipCached(actor.getUniqueId()).optionalTeam();
        if (actor == null || team.isEmpty()) {
            if (actor != null) {
                actor.sendMessage(messages.component(actor, "error.not-in-team"));
            }
            return 0;
        }
        respond(actor, "success.disbanded", teams.disband(new TeamRequests.TeamAction(
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
                        ? messages.component(player, enabled ? "chat.enabled" : "chat.disabled")
                        : messages.component(player, "error.chat-preference"))));
        return Command.SINGLE_SUCCESS;
    }

    private int toggleSpy(CommandSourceStack source) {
        var player = player(source);
        if (player == null) {
            return 0;
        }
        chat.toggleSpy(player.getUniqueId()).whenComplete((enabled, failure) ->
                dispatch(player, () -> player.sendMessage(failure == null
                        ? messages.component(player, enabled ? "spy.enabled" : "spy.disabled")
                        : messages.component(player, "error.spy-preference"))));
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
            String successKey,
            java.util.concurrent.CompletionStage<OperationResult<TeamSnapshot>> stage
    ) {
        stage.whenComplete((result, exception) -> dispatch(player, () -> {
            if (exception != null) {
                player.sendMessage(messages.component(player, "error.database_unavailable"));
            } else if (result instanceof OperationResult.Success<TeamSnapshot>) {
                player.sendMessage(messages.component(player, successKey));
            } else if (result instanceof OperationResult.Failure<TeamSnapshot> failure) {
                var key = failure.messageKey().startsWith("openteams.")
                        ? failure.messageKey().substring("openteams.".length()) : failure.messageKey();
                player.sendMessage(messages.component(player, key, failure.messageArguments()));
            }
        }));
    }

    private void sendInfo(CommandSender sender, TeamSnapshot snapshot) {
        if (sender instanceof Player player) {
            player.sendMessage(Component.text(snapshot.name(), NamedTextColor.AQUA)
                    .append(Component.text(" [" + (snapshot.tag() == null ? "—" : snapshot.tag()) + "]",
                            NamedTextColor.GRAY)));
            player.sendMessage(messages.component(player, "label.members")
                    .append(Component.text(" " + snapshot.members().size() + "/" + snapshot.memberLimit()
                            + " · ", NamedTextColor.GRAY))
                    .append(messages.component(player, "label.team-id"))
                    .append(Component.text(" " + snapshot.id(), NamedTextColor.GRAY)));
            return;
        }
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

    private TeamSnapshot team(Player actor) {
        var team = teams.membershipCached(actor.getUniqueId()).optionalTeam();
        if (team.isEmpty()) actor.sendMessage(messages.component(actor, "error.not-in-team"));
        return team.orElse(null);
    }

    private int forbidden(Player actor) {
        actor.sendMessage(messages.component(actor, "error.forbidden"));
        return 0;
    }

    private java.util.concurrent.CompletionStage<java.util.List<me.alexisbinh.openteams.api.TeamDirectory.PlayerSummary>>
    memberCandidates(TeamSnapshot team) {
        var ids = team.members().stream()
                .map(me.alexisbinh.openteams.api.TeamMemberSnapshot::playerId).toList();
        return players.resolve(ids).thenApply(byId -> ids.stream().map(byId::get).toList());
    }

    private java.util.concurrent.CompletionStage<java.util.List<me.alexisbinh.openteams.api.TeamDirectory.PlayerSummary>>
    directoryCandidates(String input) {
        try {
            var id = UUID.fromString(input);
            return players.resolve(java.util.List.of(id)).thenApply(byId -> java.util.List.of(byId.get(id)));
        } catch (IllegalArgumentException ignored) {
            return players.findExact(input);
        }
    }

    private void resolveTarget(
            Player actor,
            String input,
            java.util.concurrent.CompletionStage<java.util.List<me.alexisbinh.openteams.api.TeamDirectory.PlayerSummary>> stage,
            java.util.function.Consumer<UUID> resolved
    ) {
        stage.whenComplete((candidates, failure) -> dispatch(actor, () -> {
            if (failure != null) {
                actor.sendMessage(messages.component(actor, "error.database_unavailable"));
                return;
            }
            java.util.List<me.alexisbinh.openteams.api.TeamDirectory.PlayerSummary> matches;
            try {
                var id = UUID.fromString(input);
                matches = candidates.stream().filter(item -> item.playerId().equals(id)).toList();
            } catch (IllegalArgumentException ignored) {
                matches = candidates.stream().filter(item ->
                        item.lastKnownName().equalsIgnoreCase(input)).toList();
            }
            if (matches.isEmpty()) {
                actor.sendMessage(messages.component(actor, "error.player-not-found"));
            } else if (matches.size() > 1) {
                actor.sendMessage(messages.component(actor, "error.player-ambiguous"));
                matches.forEach(item -> actor.sendMessage(Component.text(
                        item.lastKnownName() + " · " + item.playerId(), NamedTextColor.GRAY)));
            } else {
                resolved.accept(matches.getFirst().playerId());
            }
        }));
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
    suggestDirectory(com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return players.search(builder.getRemaining(), 20).thenApply(items -> {
            items.forEach(item -> builder.suggest(item.lastKnownName()));
            return builder.build();
        }).toCompletableFuture();
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
    suggestMembers(CommandSourceStack source,
                   com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        if (!(source.getSender() instanceof Player player)) return builder.buildFuture();
        var team = teams.membershipCached(player.getUniqueId()).optionalTeam();
        if (team.isEmpty()) return builder.buildFuture();
        return memberCandidates(team.get()).thenApply(items -> {
            items.forEach(item -> builder.suggest(item.lastKnownName()));
            return builder.build();
        }).toCompletableFuture();
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
    suggestRequests(CommandSourceStack source,
                    com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        if (!(source.getSender() instanceof Player player)) return builder.buildFuture();
        var team = teams.membershipCached(player.getUniqueId()).optionalTeam();
        if (team.isEmpty()) return builder.buildFuture();
        return teams.joinRequests(team.get().id()).thenApply(items -> {
            items.forEach(item -> builder.suggest(item.player().lastKnownName()));
            return builder.build();
        }).toCompletableFuture();
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
    suggestBans(CommandSourceStack source,
                com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        if (!(source.getSender() instanceof Player player)) return builder.buildFuture();
        var team = teams.membershipCached(player.getUniqueId()).optionalTeam();
        if (team.isEmpty()) return builder.buildFuture();
        return teams.bans(team.get().id()).thenApply(items -> {
            items.forEach(item -> builder.suggest(item.player().lastKnownName()));
            return builder.build();
        }).toCompletableFuture();
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
    suggestOutgoing(CommandSourceStack source,
                    com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        if (!(source.getSender() instanceof Player player)) return builder.buildFuture();
        var team = teams.membershipCached(player.getUniqueId()).optionalTeam();
        if (team.isEmpty()) return builder.buildFuture();
        return teams.outgoingInvitations(team.get().id()).thenApply(items -> {
            items.forEach(item -> builder.suggest(item.player().lastKnownName()));
            return builder.build();
        }).toCompletableFuture();
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
    suggestMyRequests(CommandSourceStack source,
                      com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        if (!(source.getSender() instanceof Player player)) return builder.buildFuture();
        return teams.joinRequestsByPlayer(player.getUniqueId()).thenApply(items -> {
            items.forEach(item -> builder.suggest(item.team().id().toString()));
            return builder.build();
        }).toCompletableFuture();
    }

    private com.mojang.brigadier.suggestion.Suggestions suggestAssignableRoles(
            CommandSourceStack source,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder,
            java.util.List<me.alexisbinh.openteams.api.TeamDirectory.Role> roles) {
        if (!(source.getSender() instanceof Player player)) return builder.build();
        var team = teams.membershipCached(player.getUniqueId()).optionalTeam();
        if (team.isEmpty()) return builder.build();
        var actorRole = team.get().members().stream()
                .filter(item -> item.playerId().equals(player.getUniqueId())).findFirst()
                .map(me.alexisbinh.openteams.api.TeamMemberSnapshot::roleKey).orElse("");
        var priority = roles.stream().filter(role -> role.key().equals(actorRole)).findFirst()
                .map(me.alexisbinh.openteams.api.TeamDirectory.Role::priority).orElse(Integer.MIN_VALUE);
        roles.stream().filter(role -> !role.protectedRole() && role.priority() < priority)
                .forEach(role -> builder.suggest(role.key()));
        return builder.build();
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
    suggestInvitations(CommandSourceStack source,
                       com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        if (!(source.getSender() instanceof Player player)) return builder.buildFuture();
        return teams.invitations(player.getUniqueId()).thenApply(items -> {
            items.forEach(item -> builder.suggest(item.team().id().toString()));
            return builder.build();
        }).toCompletableFuture();
    }

    private enum TargetOperation {
        KICK,
        TRANSFER,
        APPROVE,
        REJECT,
        UNBAN,
        REVOKE
    }
}
