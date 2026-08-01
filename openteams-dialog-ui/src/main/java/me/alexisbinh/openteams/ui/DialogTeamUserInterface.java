package me.alexisbinh.openteams.ui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import me.alexisbinh.openteams.api.OperationResult;
import me.alexisbinh.openteams.api.TeamDirectory;
import me.alexisbinh.openteams.api.TeamMemberSnapshot;
import me.alexisbinh.openteams.api.TeamRequests;
import me.alexisbinh.openteams.api.TeamService;
import me.alexisbinh.openteams.api.TeamSnapshot;
import me.alexisbinh.openteams.api.TeamVisibility;
import me.alexisbinh.openteams.api.extension.TeamUiRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Dialog-first player experience. All database work remains in TeamService. */
public final class DialogTeamUserInterface implements TeamUserInterface {
    private static final int PAGE_SIZE = 8;
    private final TeamService teams;
    private final Plugin plugin;
    private final TeamUserInterface fallback;
    private final LocalizedMessages messages;
    private final Supplier<List<TeamUiRegistry.UiAction>> addonActions;

    public DialogTeamUserInterface(Plugin plugin, TeamService teams, TeamUserInterface fallback,
                                   LocalizedMessages messages,
                                   Supplier<List<TeamUiRegistry.UiAction>> addonActions) {
        this.plugin = plugin;
        this.teams = teams;
        this.fallback = fallback;
        this.messages = messages;
        this.addonActions = addonActions;
    }

    @Override
    public void openDashboard(Player viewer) {
        try {
            var team = teams.findByPlayerCached(viewer.getUniqueId());
            viewer.showDialog(team.isEmpty() ? noTeam(viewer) : dashboard(viewer, team.get()));
        } catch (LinkageError | RuntimeException exception) {
            fallback.openDashboard(viewer);
        }
    }

    private Dialog noTeam(Player viewer) {
        var actions = List.of(
                action(viewer, "dashboard.create", "dashboard.create-tooltip",
                        response -> show(viewer, createForm(viewer))),
                action(viewer, "dashboard.explore", "dashboard.explore-tooltip",
                        response -> openExplore(viewer, "", 0)),
                action(viewer, "dashboard.invitations", "dashboard.invitations-tooltip",
                        response -> openInvitations(viewer)),
                action(viewer, "dashboard.my-requests", "dashboard.my-requests-tooltip",
                        response -> openMyRequests(viewer)));
        return multi(viewer, "dashboard.title", messages.component(viewer, "dashboard.no-team"),
                List.of(), actions, 1);
    }

    private Dialog createForm(Player viewer) {
        var inputs = List.of(
                DialogInput.text("name", messages.component(viewer, "form.team-name"))
                        .width(300).maxLength(24).build(),
                DialogInput.text("tag", messages.component(viewer, "form.team-tag"))
                        .width(300).maxLength(8).build());
        var create = action(viewer, "action.create", "dashboard.create-tooltip",
                response -> createTeam(viewer, response));
        return notice(viewer, "create.title", messages.component(viewer, "create.help"), inputs, create);
    }

    private void createTeam(Player viewer, DialogResponseView response) {
        var submittedName = response.getText("name");
        var name = submittedName == null ? "" : submittedName;
        var tag = blankToNull(response.getText("tag"));
        viewer.sendMessage(messages.component(viewer, "status.working"));
        async(viewer, teams.create(new TeamRequests.Create(viewer.getUniqueId(), name, tag)), result -> {
            if (result instanceof OperationResult.Success<TeamSnapshot>) {
                viewer.sendMessage(messages.component(viewer, "success.created"));
                openDashboard(viewer);
                return;
            }
            var failure = (OperationResult.Failure<TeamSnapshot>) result;
            showError(viewer, failureMessage(viewer, failure));
        });
    }

    private Dialog dashboard(Player viewer, TeamSnapshot team) {
        var member = member(team, viewer.getUniqueId());
        var summary = Component.text(team.name())
                .append(Component.text(" [" + (team.tag() == null ? "—" : team.tag()) + "]"))
                .append(Component.newline())
                .append(messages.component(viewer, "label.members"))
                .append(Component.text(" " + team.members().size() + "/" + team.memberLimit()
                        + " · "))
                .append(messages.component(viewer, "visibility."
                        + team.visibility().name().toLowerCase()))
                .append(Component.text(" · "))
                .append(roleLabel(viewer, member.roleKey()));
        var actions = new ArrayList<ActionButton>();
        actions.add(action(viewer, "dashboard.members", "dashboard.members-tooltip",
                response -> openMembers(viewer, team)));
        if (teams.hasPermissionCached(viewer.getUniqueId(), "team.invite")) {
            actions.add(action(viewer, "dashboard.invite", "dashboard.invite-tooltip",
                    response -> show(viewer, invitePicker(viewer, team))));
            actions.add(action(viewer, "dashboard.sent-invitations",
                    "dashboard.sent-invitations-tooltip",
                    response -> openOutgoingInvitations(viewer, team)));
        }
        if (teams.hasPermissionCached(viewer.getUniqueId(), "team.join-request.accept")) {
            actions.add(action(viewer, "dashboard.requests", "dashboard.requests-tooltip",
                    response -> openRequests(viewer, team)));
        }
        actions.add(action(viewer, "dashboard.chat", "dashboard.chat-tooltip",
                response -> viewer.performCommand("team chat")));
        if (teams.hasPermissionCached(viewer.getUniqueId(), "team.settings.manage")
                || team.ownerId().equals(viewer.getUniqueId())) {
            actions.add(action(viewer, "dashboard.settings", "dashboard.settings-tooltip",
                    response -> show(viewer, settings(viewer, team))));
        }
        if (!team.ownerId().equals(viewer.getUniqueId())) {
            actions.add(action(viewer, "dashboard.leave", "dashboard.leave-tooltip",
                    response -> show(viewer, confirm(viewer, team, "confirm.leave", () ->
                            mutate(viewer, "success.left", teams.leave(new TeamRequests.TeamAction(
                                    viewer.getUniqueId(), team.id())))))));
        }
        appendAddonActions(viewer, team, TeamUiRegistry.Area.DASHBOARD, actions);
        return multi(viewer, "dashboard.title", summary, List.of(), actions, 2);
    }

    private void openExplore(Player viewer, String query, int page) {
        async(viewer, teams.searchPublicTeams(query, page, PAGE_SIZE), result -> {
            var actions = new ArrayList<ActionButton>();
            for (var team : result.items()) {
                actions.add(rawAction(team.name() + "  " + team.memberCount() + "/" + team.memberLimit(),
                        messages.component(viewer, "explore.view-tooltip"), response ->
                                show(viewer, teamPreview(viewer, team))));
            }
            if (page > 0) actions.add(action(viewer, "action.previous", "action.previous-tooltip",
                    response -> openExplore(viewer, query, page - 1)));
            if (result.hasNext()) actions.add(action(viewer, "action.next", "action.next-tooltip",
                    response -> openExplore(viewer, query, page + 1)));
            actions.add(action(viewer, "action.back", "action.back-tooltip", response -> openDashboard(viewer)));
            var inputs = List.of(DialogInput.text("search", messages.component(viewer, "explore.search"))
                    .width(300).maxLength(24).initial(query).build());
            actions.add(action(viewer, "action.search", "explore.search-tooltip", response ->
                    openExplore(viewer, response.getText("search"), 0)));
            show(viewer, multi(viewer, "explore.title",
                    result.items().isEmpty() ? messages.component(viewer, "explore.empty")
                            : messages.component(viewer, "explore.help"), inputs, actions, 2));
        });
    }

    private Dialog teamPreview(Player viewer, TeamDirectory.TeamSummary team) {
        var body = Component.text(team.name())
                .append(Component.newline()).append(Component.text(
                        (team.tag() == null ? "—" : team.tag()) + " · "))
                .append(messages.component(viewer, "label.members"))
                .append(Component.text(" " + team.memberCount() + "/" + team.memberLimit()));
        return multi(viewer, "explore.preview-title", body, List.of(), List.of(
                rawAction(messages.component(viewer, "action.request-join"),
                        messages.component(viewer, "action.request-join-tooltip"), response ->
                                mutate(viewer, "success.requested", teams.requestJoin(
                                        new TeamRequests.TeamAction(viewer.getUniqueId(), team.id())))),
                action(viewer, "action.back", "action.back-tooltip",
                        response -> openExplore(viewer, "", 0))), 1);
    }

    private void openInvitations(Player viewer) {
        async(viewer, teams.invitations(viewer.getUniqueId()), invitations -> {
            var actions = new ArrayList<ActionButton>();
            for (var invitation : invitations) {
                var team = invitation.team();
                actions.add(rawAction(messages.component(viewer, "action.accept")
                                .append(Component.text(" · " + team.name())),
                        messages.component(viewer, "invitation.accept-tooltip"), response ->
                                mutate(viewer, "success.joined", teams.acceptInvitation(
                                        new TeamRequests.TargetAction(viewer.getUniqueId(), team.id(),
                                                viewer.getUniqueId())))));
                actions.add(rawAction(messages.component(viewer, "action.decline")
                                .append(Component.text(" · " + team.name())),
                        messages.component(viewer, "invitation.decline-tooltip"), response ->
                                mutate(viewer, "success.declined", teams.declineInvitation(
                                        new TeamRequests.TargetAction(viewer.getUniqueId(), team.id(),
                                                viewer.getUniqueId())))));
            }
            actions.add(action(viewer, "action.back", "action.back-tooltip", response -> openDashboard(viewer)));
            show(viewer, multi(viewer, "invitations.title",
                    invitations.isEmpty() ? messages.component(viewer, "invitations.empty")
                            : messages.component(viewer, "invitations.help"), List.of(), actions, 2));
        });
    }

    private void openMyRequests(Player viewer) {
        async(viewer, teams.joinRequestsByPlayer(viewer.getUniqueId()), requests -> {
            var actions = new ArrayList<ActionButton>();
            requests.forEach(request -> actions.add(rawAction(request.team().name(),
                    messages.component(viewer, "request.cancel-tooltip"), response ->
                            mutate(viewer, "success.request-cancelled", teams.cancelJoinRequest(
                                    new TeamRequests.TeamAction(viewer.getUniqueId(),
                                            request.team().id()))))));
            actions.add(action(viewer, "action.back", "action.back-tooltip",
                    response -> openDashboard(viewer)));
            show(viewer, multi(viewer, "my-requests.title", requests.isEmpty()
                    ? messages.component(viewer, "my-requests.empty")
                    : messages.component(viewer, "my-requests.help"), List.of(), actions, 1));
        });
    }

    private void openMembers(Player viewer, TeamSnapshot team) {
        var ids = team.members().stream().map(TeamMemberSnapshot::playerId).toList();
        async(viewer, teams.resolvePlayers(ids), names -> {
            var actions = new ArrayList<ActionButton>();
            team.members().forEach(item -> {
                var profile = names.get(item.playerId());
                var online = Bukkit.getPlayer(item.playerId()) != null;
                actions.add(rawAction((online ? "● " : "○ ") + profile.lastKnownName()
                                + " · " + item.roleKey(),
                        messages.component(viewer, "members.manage-tooltip"), response ->
                                show(viewer, memberActions(viewer, team, item, profile))));
            });
            appendAddonActions(viewer, team, TeamUiRegistry.Area.MEMBERS, actions);
            actions.add(action(viewer, "action.back", "action.back-tooltip", response -> openDashboard(viewer)));
            show(viewer, multi(viewer, "members.title", messages.component(viewer, "members.help"),
                    List.of(), actions, 2));
        });
    }

    private Dialog memberActions(Player viewer, TeamSnapshot team, TeamMemberSnapshot target,
                                 TeamDirectory.PlayerSummary profile) {
        var actions = new ArrayList<ActionButton>();
        if (!target.playerId().equals(viewer.getUniqueId())
                && teams.hasPermissionCached(viewer.getUniqueId(), "team.role.change")) {
            actions.add(action(viewer, "member.change-role", "member.change-role-tooltip",
                    response -> openRolePicker(viewer, team, target)));
        }
        if (!target.playerId().equals(team.ownerId())
                && teams.hasPermissionCached(viewer.getUniqueId(), "team.kick")) {
            actions.add(action(viewer, "member.kick", "member.kick-tooltip", response ->
                    show(viewer, confirm(viewer, team, "confirm.kick", () -> mutate(viewer,
                            "success.kicked", teams.kick(new TeamRequests.TargetAction(
                                    viewer.getUniqueId(), team.id(), target.playerId())))))));
        }
        if (!target.playerId().equals(team.ownerId())
                && teams.hasPermissionCached(viewer.getUniqueId(), "team.ban")) {
            actions.add(action(viewer, "member.ban", "member.ban-tooltip",
                    response -> show(viewer, banForm(viewer, team, target))));
        }
        if (team.ownerId().equals(viewer.getUniqueId())
                && !target.playerId().equals(viewer.getUniqueId())) {
            actions.add(action(viewer, "member.transfer", "member.transfer-tooltip", response ->
                    show(viewer, confirm(viewer, team, "confirm.transfer", () -> mutate(viewer,
                            "success.transferred", teams.transferOwnership(new TeamRequests.TargetAction(
                                    viewer.getUniqueId(), team.id(), target.playerId())))))));
        }
        actions.add(action(viewer, "action.back", "action.back-tooltip", response -> openMembers(viewer, team)));
        return multi(viewer, "member.title", Component.text(profile.lastKnownName())
                .append(Component.text(" · "))
                .append(roleLabel(viewer, target.roleKey())),
                List.of(), actions, 1);
    }

    private void openRolePicker(Player viewer, TeamSnapshot team, TeamMemberSnapshot target) {
        async(viewer, teams.roles(), roles -> {
            var options = roles.stream().filter(role -> !role.protectedRole())
                    .map(role -> SingleOptionDialogInput.OptionEntry.create(
                            role.key(), Component.text(role.displayName()), role.key().equals(target.roleKey())))
                    .toList();
            var input = DialogInput.singleOption("role", messages.component(viewer, "form.role"), options)
                    .width(300).build();
            var submit = action(viewer, "action.save", "action.save-tooltip", response ->
                    mutate(viewer, "success.role-changed", teams.changeRole(new TeamRequests.ChangeRole(
                            viewer.getUniqueId(), team.id(), target.playerId(), response.getText("role")))));
            show(viewer, notice(viewer, "member.role-title", messages.component(viewer, "member.role-help"),
                    List.of(input), submit));
        });
    }

    private Dialog invitePicker(Player viewer, TeamSnapshot team) {
        var members = team.members().stream().map(TeamMemberSnapshot::playerId).collect(java.util.stream.Collectors.toSet());
        var actions = Bukkit.getOnlinePlayers().stream().filter(player -> !members.contains(player.getUniqueId()))
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(20).map(target -> rawAction(target.getName(),
                        messages.component(viewer, "invite.player-tooltip"), response ->
                                mutate(viewer, "success.invited", teams.invite(new TeamRequests.TargetAction(
                                        viewer.getUniqueId(), team.id(), target.getUniqueId()))))).toList();
        var all = new ArrayList<>(actions);
        all.add(action(viewer, "invite.by-name", "invite.by-name-tooltip", response -> {
            var playerName = response.getText("player");
            var target = playerName == null ? null : Bukkit.getOfflinePlayerIfCached(playerName.strip());
            if (target == null) {
                showError(viewer, messages.component(viewer, "error.player-not-found"));
                return;
            }
            mutate(viewer, "success.invited", teams.invite(new TeamRequests.TargetAction(
                    viewer.getUniqueId(), team.id(), target.getUniqueId())));
        }));
        all.add(action(viewer, "action.back", "action.back-tooltip", response -> openDashboard(viewer)));
        var playerInput = DialogInput.text("player", messages.component(viewer, "form.player-name"))
                .width(300).maxLength(16).build();
        return multi(viewer, "invite.title", messages.component(viewer,
                actions.isEmpty() ? "invite.offline-only" : "invite.help"),
                List.of(playerInput), all, 2);
    }

    private void openRequests(Player viewer, TeamSnapshot team) {
        async(viewer, teams.joinRequests(team.id()), requests -> {
            var actions = new ArrayList<ActionButton>();
            for (var request : requests) {
                actions.add(rawAction(messages.component(viewer, "action.accept").append(Component.text(" · "
                                + request.player().lastKnownName())), messages.component(viewer, "request.accept-tooltip"),
                        response -> mutate(viewer, "success.request-accepted", teams.acceptJoinRequest(
                                new TeamRequests.TargetAction(viewer.getUniqueId(), team.id(),
                                        request.player().playerId())))));
                actions.add(rawAction(messages.component(viewer, "action.reject").append(Component.text(" · "
                                + request.player().lastKnownName())), messages.component(viewer, "request.reject-tooltip"),
                        response -> mutate(viewer, "success.request-rejected", teams.rejectJoinRequest(
                                new TeamRequests.TargetAction(viewer.getUniqueId(), team.id(),
                                        request.player().playerId())))));
            }
            actions.add(action(viewer, "action.back", "action.back-tooltip", response -> openDashboard(viewer)));
            show(viewer, multi(viewer, "requests.title", requests.isEmpty()
                    ? messages.component(viewer, "requests.empty") : messages.component(viewer, "requests.help"),
                    List.of(), actions, 2));
        });
    }

    private void openOutgoingInvitations(Player viewer, TeamSnapshot team) {
        async(viewer, teams.outgoingInvitations(team.id()), invitations -> {
            var actions = new ArrayList<ActionButton>();
            invitations.forEach(invitation -> actions.add(rawAction(
                    invitation.player().lastKnownName(),
                    messages.component(viewer, "invitation.revoke-tooltip"), response ->
                            mutate(viewer, "success.revoked", teams.revokeInvitation(
                                    new TeamRequests.TargetAction(viewer.getUniqueId(), team.id(),
                                            invitation.player().playerId()))))));
            actions.add(action(viewer, "action.back", "action.back-tooltip",
                    response -> openDashboard(viewer)));
            show(viewer, multi(viewer, "sent-invitations.title", invitations.isEmpty()
                    ? messages.component(viewer, "sent-invitations.empty")
                    : messages.component(viewer, "sent-invitations.help"),
                    List.of(), actions, 1));
        });
    }

    private Dialog settings(Player viewer, TeamSnapshot team) {
        var actions = new ArrayList<ActionButton>();
        actions.add(action(viewer, "settings.rename", "settings.rename-tooltip",
                response -> show(viewer, textMutation(viewer, "rename.title", "form.team-name", "name",
                        team.name(), 24, value -> teams.rename(new TeamRequests.Rename(
                                viewer.getUniqueId(), team.id(), value)), "success.renamed"))));
        actions.add(action(viewer, "settings.tag", "settings.tag-tooltip",
                response -> show(viewer, textMutation(viewer, "tag.title", "form.team-tag", "tag",
                        team.tag() == null ? "" : team.tag(), 8, value -> teams.setTag(new TeamRequests.SetTag(
                                viewer.getUniqueId(), team.id(), blankToNull(value))), "success.tagged"))));
        actions.add(rawAction(messages.component(viewer, team.visibility() == TeamVisibility.PUBLIC
                        ? "settings.make-private" : "settings.make-public"),
                messages.component(viewer, "settings.visibility-tooltip"), response -> mutate(viewer,
                        "success.visibility", teams.setVisibility(new TeamRequests.SetVisibility(
                                viewer.getUniqueId(), team.id(), team.visibility() == TeamVisibility.PUBLIC
                                ? TeamVisibility.PRIVATE : TeamVisibility.PUBLIC)))));
        if (teams.hasPermissionCached(viewer.getUniqueId(), "team.ban")) {
            actions.add(action(viewer, "settings.bans", "settings.bans-tooltip",
                    response -> openBans(viewer, team)));
        }
        if (team.ownerId().equals(viewer.getUniqueId())) {
            actions.add(action(viewer, "settings.disband", "settings.disband-tooltip",
                    response -> show(viewer, disbandForm(viewer, team))));
        }
        appendAddonActions(viewer, team, TeamUiRegistry.Area.SETTINGS, actions);
        actions.add(action(viewer, "action.back", "action.back-tooltip", response -> openDashboard(viewer)));
        return multi(viewer, "settings.title", messages.component(viewer, "settings.help"),
                List.of(), actions, 1);
    }

    private Dialog textMutation(Player viewer, String title, String label, String key,
                                String initial, int maxLength,
                                java.util.function.Function<String, CompletionStage<OperationResult<TeamSnapshot>>> operation,
                                String successKey) {
        var input = DialogInput.text(key, messages.component(viewer, label)).width(300)
                .maxLength(maxLength).initial(initial).build();
        return notice(viewer, title, messages.component(viewer, "form.save-help"), List.of(input),
                action(viewer, "action.save", "action.save-tooltip",
                        response -> mutate(viewer, successKey, operation.apply(response.getText(key)))));
    }

    private Dialog banForm(Player viewer, TeamSnapshot team, TeamMemberSnapshot target) {
        var input = DialogInput.text("reason", messages.component(viewer, "form.ban-reason"))
                .width(300).maxLength(255).build();
        return notice(viewer, "ban.title", messages.component(viewer, "confirm.ban"), List.of(input),
                action(viewer, "action.confirm", "action.confirm-tooltip", response ->
                        runIfCurrent(viewer, team, () -> mutate(viewer,
                                "success.banned", teams.ban(new TeamRequests.Ban(viewer.getUniqueId(), team.id(),
                                        target.playerId(), response.getText("reason")))))));
    }

    private void openBans(Player viewer, TeamSnapshot team) {
        async(viewer, teams.bans(team.id()), bans -> {
            var actions = new ArrayList<ActionButton>();
            bans.forEach(ban -> actions.add(rawAction(ban.player().lastKnownName(),
                    messages.component(viewer, "ban.unban-tooltip"), response -> mutate(viewer,
                            "success.unbanned", teams.unban(new TeamRequests.TargetAction(
                                    viewer.getUniqueId(), team.id(), ban.player().playerId()))))));
            actions.add(action(viewer, "action.back", "action.back-tooltip",
                    response -> show(viewer, settings(viewer, team))));
            show(viewer, multi(viewer, "bans.title", bans.isEmpty() ? messages.component(viewer, "bans.empty")
                    : messages.component(viewer, "bans.help"), List.of(), actions, 1));
        });
    }

    private Dialog disbandForm(Player viewer, TeamSnapshot team) {
        var input = DialogInput.text("confirm", messages.component(viewer, "form.confirm-team-name"))
                .width(300).maxLength(24).build();
        return notice(viewer, "disband.title", messages.component(viewer, "confirm.disband"), List.of(input),
                action(viewer, "action.disband", "settings.disband-tooltip", response -> {
                    if (!team.name().equals(response.getText("confirm"))) {
                        showError(viewer, messages.component(viewer, "error.confirm-name"));
                        return;
                    }
                    runIfCurrent(viewer, team, () -> mutate(viewer, "success.disbanded",
                            teams.disband(new TeamRequests.TeamAction(viewer.getUniqueId(), team.id()))));
                }));
    }

    private Dialog confirm(Player viewer, TeamSnapshot team, String messageKey, Runnable confirmed) {
        return Dialog.create(factory -> factory.empty()
                .base(base(viewer, "confirm.title", messages.component(viewer, messageKey), List.of()))
                .type(DialogType.confirmation(
                        action(viewer, "action.confirm", "action.confirm-tooltip",
                                response -> runIfCurrent(viewer, team, confirmed)),
                        action(viewer, "action.cancel", "action.cancel-tooltip", response -> openDashboard(viewer)))));
    }

    private void runIfCurrent(Player viewer, TeamSnapshot expected, Runnable action) {
        var current = teams.findCached(expected.id());
        if (current.isEmpty() || current.get().version() != expected.version()) {
            showError(viewer, messages.component(viewer, "error.conflict"));
            return;
        }
        action.run();
    }

    private void mutate(Player viewer, String successKey,
                        CompletionStage<OperationResult<TeamSnapshot>> stage) {
        viewer.sendMessage(messages.component(viewer, "status.working"));
        async(viewer, stage, result -> {
            if (result instanceof OperationResult.Success<TeamSnapshot>) {
                viewer.sendMessage(messages.component(viewer, successKey));
                openDashboard(viewer);
            } else if (result instanceof OperationResult.Failure<TeamSnapshot> failure) {
                showError(viewer, failureMessage(viewer, failure));
            }
        });
    }

    private void showError(Player viewer, Component message) {
        viewer.closeDialog();
        viewer.sendMessage(message);
    }

    private Component failureMessage(Player viewer, OperationResult.Failure<?> failure) {
        var messageKey = failure.messageKey();
        if (messageKey.startsWith("openteams.")) {
            messageKey = messageKey.substring("openteams.".length());
        }
        return messages.component(viewer, messageKey);
    }

    private <T> void async(Player viewer, CompletionStage<T> stage,
                           java.util.function.Consumer<T> success) {
        stage.whenComplete((value, failure) -> viewer.getScheduler().run(plugin, task -> {
            if (!viewer.isOnline()) return;
            if (failure == null) success.accept(value);
            else showError(viewer, messages.component(viewer, "error.database_unavailable"));
        }, null));
    }

    private ActionButton addonAction(Player viewer, TeamUiRegistry.UiAction item,
                                     TeamUiRegistry.UiContext context) {
        return rawAction(messages.component(viewer, item.labelKey()),
                messages.component(viewer, item.descriptionKey()), response ->
                        runAddon(viewer, item, context));
    }

    private void runAddon(Player viewer, TeamUiRegistry.UiAction item,
                          TeamUiRegistry.UiContext context) {
        var current = teams.findCached(context.teamId());
        var registered = addonActions.get().stream().anyMatch(candidate -> candidate == item);
        if (!registered
                || !teams.hasPermissionCached(viewer.getUniqueId(), item.permission())
                || !safeAvailable(item, context)
                || current.isEmpty()
                || current.get().version() != context.teamVersion()) {
            showError(viewer, messages.component(viewer, "error.conflict"));
            return;
        }
        item.handler().execute(context).whenComplete((outcome, failure) ->
                                viewer.getScheduler().run(plugin, task -> {
                                    if (failure != null) showError(viewer,
                                            messages.component(viewer, "error.addon"));
                                    else if (outcome == TeamUiRegistry.ActionOutcome.REFRESH) openDashboard(viewer);
                                }, null));
    }

    private void appendAddonActions(Player viewer, TeamSnapshot team, TeamUiRegistry.Area area,
                                    Collection<ActionButton> target) {
        var context = new TeamUiRegistry.UiContext(viewer.getUniqueId(), team.id(), team.version());
        addonActions.get().stream().filter(item -> item.area() == area)
                .filter(item -> teams.hasPermissionCached(viewer.getUniqueId(), item.permission()))
                .filter(item -> safeAvailable(item, context))
                .sorted(Comparator.comparingInt(TeamUiRegistry.UiAction::priority).reversed())
                .map(item -> addonAction(viewer, item, context)).forEach(target::add);
    }

    private static boolean safeAvailable(TeamUiRegistry.UiAction action,
                                         TeamUiRegistry.UiContext context) {
        try { return action.availability().available(context); }
        catch (RuntimeException ignored) { return false; }
    }

    private Dialog multi(Player viewer, String titleKey, Component body,
                         List<? extends DialogInput> inputs, List<ActionButton> actions, int columns) {
        return Dialog.create(factory -> factory.empty()
                .base(base(viewer, titleKey, body, inputs))
                .type(DialogType.multiAction(actions).columns(columns).build()));
    }

    private Dialog notice(Player viewer, String titleKey, Component body,
                          List<? extends DialogInput> inputs, ActionButton exit) {
        return Dialog.create(factory -> factory.empty().base(base(viewer, titleKey, body, inputs))
                .type(DialogType.notice(exit)));
    }

    private DialogBase base(Player viewer, String titleKey, Component body,
                            List<? extends DialogInput> inputs) {
        return DialogBase.builder(messages.component(viewer, titleKey))
                .body(List.of(DialogBody.plainMessage(body, 360))).inputs(inputs)
                .canCloseWithEscape(true).build();
    }

    private ActionButton action(Player viewer, String labelKey, String tooltipKey,
                                DialogHandler handler) {
        return rawAction(messages.component(viewer, labelKey),
                messages.component(viewer, tooltipKey), handler);
    }

    private static ActionButton rawAction(String label, Component tooltip, DialogHandler handler) {
        return rawAction(Component.text(label), tooltip, handler);
    }

    private static ActionButton rawAction(Component label, Component tooltip, DialogHandler handler) {
        return ActionButton.builder(label)
                .tooltip(tooltip).width(150)
                .action(DialogAction.customClick((response, audience) -> handler.accept(response),
                        ClickCallback.Options.builder().uses(1).build())).build();
    }

    private void show(Player viewer, Dialog dialog) {
        try { viewer.showDialog(dialog); }
        catch (LinkageError | RuntimeException exception) { fallback.openDashboard(viewer); }
    }

    private static TeamMemberSnapshot member(TeamSnapshot team, UUID playerId) {
        return team.members().stream().filter(item -> item.playerId().equals(playerId)).findFirst()
                .orElseThrow();
    }

    private Component roleLabel(Player viewer, String roleKey) {
        return switch (roleKey) {
            case "owner", "co_owner", "moderator", "member" ->
                    messages.component(viewer, "role." + roleKey);
            default -> Component.text(roleKey);
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    @Override
    public String mode() { return "dialog"; }

    @FunctionalInterface
    private interface DialogHandler { void accept(DialogResponseView response); }
}
