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
import me.alexisbinh.openteams.api.MembershipLookup;
import me.alexisbinh.openteams.api.PlayerDirectory;
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
    private final PlayerDirectory players;
    private final Plugin plugin;
    private final TeamUserInterface fallback;
    private final LocalizedMessages messages;
    private final Supplier<List<TeamUiRegistry.UiAction>> addonActions;
    private final java.util.concurrent.ConcurrentMap<UUID, Long> screenGenerations =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong nextScreenGeneration =
            new java.util.concurrent.atomic.AtomicLong();

    public DialogTeamUserInterface(Plugin plugin, TeamService teams, PlayerDirectory players,
                                   TeamUserInterface fallback,
                                   LocalizedMessages messages,
                                   Supplier<List<TeamUiRegistry.UiAction>> addonActions) {
        this.plugin = plugin;
        this.teams = teams;
        this.players = players;
        this.fallback = fallback;
        this.messages = messages;
        this.addonActions = addonActions;
    }

    @Override
    public void openDashboard(Player viewer) {
        try {
            var membership = teams.membershipCached(viewer.getUniqueId());
            switch (membership.status()) {
                case LOADING -> awaitMembership(viewer);
                case FAILED -> show(viewer, errorDialog(viewer,
                        messages.component(viewer, "error.database_unavailable"),
                        () -> awaitMembership(viewer),
                        () -> fallback.openDashboard(viewer)));
                case ABSENT -> show(viewer, noTeam(viewer));
                case PRESENT -> show(viewer, dashboard(viewer,
                        membership.optionalTeam().orElseThrow()));
            }
        } catch (LinkageError | RuntimeException exception) {
            fallback.openDashboard(viewer);
        }
    }

    private void awaitMembership(Player viewer) {
        async(viewer, teams.loadMembership(viewer.getUniqueId()),
                ignored -> openDashboard(viewer), () -> awaitMembership(viewer));
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
        return createForm(viewer, "", "", null);
    }

    private Dialog createForm(Player viewer, String name, String tag, Component error) {
        var inputs = List.of(
                DialogInput.text("name", messages.component(viewer, "form.team-name"))
                        .width(300).maxLength(24).initial(name).build(),
                DialogInput.text("tag", messages.component(viewer, "form.team-tag"))
                        .width(300).maxLength(8).initial(tag).build());
        var create = action(viewer, "action.create", "dashboard.create-tooltip",
                response -> createTeam(viewer, response));
        var body = error == null ? messages.component(viewer, "create.help")
                : error.append(Component.newline()).append(messages.component(viewer, "create.help"));
        return notice(viewer, "create.title", body, inputs, create);
    }

    private void createTeam(Player viewer, DialogResponseView response) {
        var submittedName = response.getText("name");
        var name = submittedName == null ? "" : submittedName;
        var tag = blankToNull(response.getText("tag"));
        var request = new TeamRequests.Create(viewer.getUniqueId(), name, tag);
        async(viewer, teams.create(request).handle(MutationAttempt::new), attempt -> {
            if (attempt.exception() != null) {
                show(viewer, errorDialog(viewer,
                        messages.component(viewer, "error.database_unavailable"),
                        () -> show(viewer, createForm(viewer, name, tag == null ? "" : tag, null)),
                        () -> openDashboard(viewer)));
                return;
            }
            var result = attempt.result();
            if (result instanceof OperationResult.Success<TeamSnapshot>) {
                viewer.sendMessage(messages.component(viewer, "success.created"));
                openDashboard(viewer);
                return;
            }
            var failure = (OperationResult.Failure<TeamSnapshot>) result;
            if (failure.code() == me.alexisbinh.openteams.api.TeamErrorCode.INVALID_ARGUMENT
                    || failure.code() == me.alexisbinh.openteams.api.TeamErrorCode.CONFLICT) {
                show(viewer, createForm(viewer, name, tag == null ? "" : tag,
                        failureMessage(viewer, failure)));
            } else {
                show(viewer, errorDialog(viewer, failureMessage(viewer, failure),
                        () -> show(viewer, createForm(viewer, name, tag == null ? "" : tag, null)),
                        () -> openDashboard(viewer)));
            }
        }, () -> show(viewer, createForm(viewer, name, tag == null ? "" : tag, null)));
    }

    private Dialog dashboard(Player viewer, TeamSnapshot team) {
        var model = DashboardModel.create(teams, viewer.getUniqueId(), team);
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
        if (model.actions().contains(DashboardModel.Action.MEMBERS)) actions.add(action(viewer,
                "dashboard.members", "dashboard.members-tooltip", response -> openMembers(viewer, team)));
        if (model.actions().contains(DashboardModel.Action.INVITE)) {
            actions.add(action(viewer, "dashboard.invite", "dashboard.invite-tooltip",
                    response -> openInvitePicker(viewer, team, "")));
            actions.add(action(viewer, "dashboard.sent-invitations",
                    "dashboard.sent-invitations-tooltip",
                    response -> openOutgoingInvitations(viewer, team)));
        }
        if (model.actions().contains(DashboardModel.Action.REQUESTS)) {
            actions.add(action(viewer, "dashboard.requests", "dashboard.requests-tooltip",
                    response -> openRequests(viewer, team)));
        }
        actions.add(action(viewer, "dashboard.chat", "dashboard.chat-tooltip",
                response -> viewer.performCommand("team chat")));
        if (model.actions().contains(DashboardModel.Action.SETTINGS)
                || hasAvailableAddonAction(viewer, team, TeamUiRegistry.Area.SETTINGS)) {
            actions.add(action(viewer, "dashboard.settings", "dashboard.settings-tooltip",
                    response -> show(viewer, settings(viewer, team))));
        }
        if (model.actions().contains(DashboardModel.Action.LEAVE)) {
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
                                show(viewer, teamPreview(viewer, team, query, page))));
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

    private Dialog teamPreview(Player viewer, TeamDirectory.TeamSummary team, String query, int page) {
        var body = Component.text(team.name())
                .append(Component.newline()).append(Component.text(
                        (team.tag() == null ? "—" : team.tag()) + " · "))
                .append(messages.component(viewer, "label.members"))
                .append(Component.text(" " + team.memberCount() + "/" + team.memberLimit()));
        return multi(viewer, "explore.preview-title", body, List.of(), List.of(
                rawAction(messages.component(viewer, "action.request-join"),
                        messages.component(viewer, "action.request-join-tooltip"), response ->
                                mutate(viewer, "success.requested", teams.requestJoin(
                                        new TeamRequests.TeamAction(viewer.getUniqueId(), team.id())),
                                        ignored -> openExplore(viewer, query, page))),
                action(viewer, "action.back", "action.back-tooltip",
                        response -> openExplore(viewer, query, page))), 1);
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
                                                viewer.getUniqueId())), ignored -> openInvitations(viewer))));
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
                                            request.team().id())), ignored -> openMyRequests(viewer)))));
            actions.add(action(viewer, "action.back", "action.back-tooltip",
                    response -> openDashboard(viewer)));
            show(viewer, multi(viewer, "my-requests.title", requests.isEmpty()
                    ? messages.component(viewer, "my-requests.empty")
                    : messages.component(viewer, "my-requests.help"), List.of(), actions, 1));
        });
    }

    private void openMembers(Player viewer, TeamSnapshot team) {
        var ids = team.members().stream().map(TeamMemberSnapshot::playerId).toList();
        async(viewer, players.resolve(ids).thenCombine(teams.roles(), MemberData::new), data -> {
            var actions = new ArrayList<ActionButton>();
            var roles = data.roles().stream().collect(java.util.stream.Collectors.toMap(
                    TeamDirectory.Role::key, java.util.function.Function.identity()));
            var sorted = new ArrayList<>(team.members());
            sorted.sort(Comparator
                    .comparingInt((TeamMemberSnapshot item) -> rolePriority(roles, item.roleKey())).reversed()
                    .thenComparing((TeamMemberSnapshot item) -> Bukkit.getPlayer(item.playerId()) == null)
                    .thenComparing(item -> data.players().get(item.playerId()).lastKnownName(),
                            String.CASE_INSENSITIVE_ORDER));
            sorted.forEach(item -> {
                var profile = data.players().get(item.playerId());
                var online = Bukkit.getPlayer(item.playerId()) != null;
                var roleName = roles.containsKey(item.roleKey())
                        ? roles.get(item.roleKey()).displayName() : item.roleKey();
                actions.add(rawAction((online ? "● " : "○ ") + profile.lastKnownName()
                                + " · " + roleName,
                        messages.component(viewer, "members.manage-tooltip"), response ->
                                show(viewer, memberActions(viewer, team, item, profile, roles))));
            });
            appendAddonActions(viewer, team, TeamUiRegistry.Area.MEMBERS, actions);
            actions.add(action(viewer, "action.back", "action.back-tooltip", response -> openDashboard(viewer)));
            show(viewer, multi(viewer, "members.title", messages.component(viewer, "members.help"),
                    List.of(), actions, 2));
        });
    }

    private Dialog memberActions(Player viewer, TeamSnapshot team, TeamMemberSnapshot target,
                                 TeamDirectory.PlayerSummary profile,
                                 Map<String, TeamDirectory.Role> roles) {
        var actions = new ArrayList<ActionButton>();
        var manageable = canManage(viewer, team, target, roles);
        if (!target.playerId().equals(viewer.getUniqueId())
                && manageable
                && teams.hasPermissionCached(viewer.getUniqueId(), "team.role.change")) {
            actions.add(action(viewer, "member.change-role", "member.change-role-tooltip",
                    response -> openRolePicker(viewer, team, target)));
        }
        if (!target.playerId().equals(team.ownerId())
                && manageable
                && teams.hasPermissionCached(viewer.getUniqueId(), "team.kick")) {
            var kickConfirmation = confirm(viewer, team, "confirm.kick",
                    () -> mutate(viewer, "success.kicked",
                            teams.kick(new TeamRequests.TargetAction(viewer.getUniqueId(),
                                    team.id(), target.playerId())),
                            snapshot -> openMembers(viewer, snapshot)),
                    () -> openMember(viewer, team, target.playerId()),
                    snapshot -> openMember(viewer, snapshot, target.playerId()));
            actions.add(action(viewer, "member.kick", "member.kick-tooltip",
                    response -> show(viewer, kickConfirmation)));
        }
        if (!target.playerId().equals(team.ownerId())
                && manageable
                && teams.hasPermissionCached(viewer.getUniqueId(), "team.ban")) {
            actions.add(action(viewer, "member.ban", "member.ban-tooltip",
                    response -> show(viewer, banForm(viewer, team, target))));
        }
        if (team.ownerId().equals(viewer.getUniqueId())
                && !target.playerId().equals(viewer.getUniqueId())) {
            var transferConfirmation = confirm(viewer, team, "confirm.transfer",
                    () -> mutate(viewer, "success.transferred",
                            teams.transferOwnership(new TeamRequests.TargetAction(
                                    viewer.getUniqueId(), team.id(), target.playerId()))),
                    () -> openMember(viewer, team, target.playerId()),
                    snapshot -> openMember(viewer, snapshot, target.playerId()));
            actions.add(action(viewer, "member.transfer", "member.transfer-tooltip",
                    response -> show(viewer, transferConfirmation)));
        }
        actions.add(action(viewer, "action.back", "action.back-tooltip", response -> openMembers(viewer, team)));
        return multi(viewer, "member.title", Component.text(profile.lastKnownName())
                .append(Component.text(" · "))
                .append(Component.text(roles.containsKey(target.roleKey())
                        ? roles.get(target.roleKey()).displayName() : target.roleKey())),
                List.of(), actions, 1);
    }

    private void openRolePicker(Player viewer, TeamSnapshot team, TeamMemberSnapshot target) {
        async(viewer, teams.roles(), roles -> {
            var roleMap = roles.stream().collect(java.util.stream.Collectors.toMap(
                    TeamDirectory.Role::key, java.util.function.Function.identity()));
            var actor = member(team, viewer.getUniqueId());
            var actorPriority = rolePriority(roleMap, actor.roleKey());
            var counts = team.members().stream().collect(java.util.stream.Collectors.groupingBy(
                    TeamMemberSnapshot::roleKey, java.util.stream.Collectors.counting()));
            var eligible = roles.stream().filter(role -> !role.protectedRole())
                    .filter(role -> role.priority() < actorPriority)
                    .filter(role -> !role.key().equals(target.roleKey()))
                    .filter(role -> role.memberLimit() == null
                            || counts.getOrDefault(role.key(), 0L) < role.memberLimit())
                    .toList();
            if (eligible.isEmpty()) {
                show(viewer, multi(viewer, "member.role-title",
                        messages.component(viewer, "member.no-assignable-role"), List.of(),
                        List.of(action(viewer, "action.back", "action.back-tooltip",
                                response -> openMember(viewer, team, target.playerId()))), 1));
                return;
            }
            var options = eligible.stream()
                    .map(role -> SingleOptionDialogInput.OptionEntry.create(
                            role.key(), Component.text(role.displayName() + roleUsage(role, counts)), false))
                    .toList();
            var input = DialogInput.singleOption("role", messages.component(viewer, "form.role"), options)
                    .width(300).build();
            var submit = action(viewer, "action.save", "action.save-tooltip", response ->
                    mutate(viewer, "success.role-changed", teams.changeRole(new TeamRequests.ChangeRole(
                            viewer.getUniqueId(), team.id(), target.playerId(), response.getText("role"))),
                            snapshot -> openMember(viewer, snapshot, target.playerId())));
            show(viewer, notice(viewer, "member.role-title", messages.component(viewer, "member.role-help"),
                    List.of(input), submit));
        });
    }

    private void openMember(Player viewer, TeamSnapshot team, UUID targetId) {
        var target = team.members().stream().filter(item -> item.playerId().equals(targetId))
                .findFirst();
        if (target.isEmpty()) {
            openMembers(viewer, team);
            return;
        }
        async(viewer, players.resolve(List.of(targetId)).thenCombine(teams.roles(), MemberData::new), data -> {
            var roles = data.roles().stream().collect(java.util.stream.Collectors.toMap(
                    TeamDirectory.Role::key, java.util.function.Function.identity()));
            show(viewer, memberActions(viewer, team, target.get(), data.players().get(targetId), roles));
        });
    }

    private boolean canManage(Player viewer, TeamSnapshot team, TeamMemberSnapshot target,
                              Map<String, TeamDirectory.Role> roles) {
        if (target.playerId().equals(viewer.getUniqueId())) return false;
        var actor = member(team, viewer.getUniqueId());
        return rolePriority(roles, actor.roleKey()) > rolePriority(roles, target.roleKey());
    }

    private static int rolePriority(Map<String, TeamDirectory.Role> roles, String key) {
        var role = roles.get(key);
        return role == null ? Integer.MIN_VALUE : role.priority();
    }

    private static String roleUsage(TeamDirectory.Role role, Map<String, Long> counts) {
        if (role.memberLimit() == null) return "";
        return " · " + counts.getOrDefault(role.key(), 0L) + "/" + role.memberLimit();
    }

    private void openInvitePicker(Player viewer, TeamSnapshot team, String query) {
        async(viewer, players.search(query == null ? "" : query.strip(), 20), found -> {
            var members = team.members().stream().map(TeamMemberSnapshot::playerId)
                    .collect(java.util.stream.Collectors.toSet());
            var actions = new ArrayList<ActionButton>();
            found.stream().filter(player -> !members.contains(player.playerId())).forEach(target -> {
                var online = Bukkit.getPlayer(target.playerId()) != null;
                var label = (online ? "● " : "○ ") + target.lastKnownName();
                actions.add(rawAction(label, messages.component(viewer, "invite.player-tooltip"),
                        response -> mutate(viewer, "success.invited",
                                teams.invite(new TeamRequests.TargetAction(viewer.getUniqueId(),
                                        team.id(), target.playerId())),
                                snapshot -> openInvitePicker(viewer, snapshot, query))));
            });
            var input = DialogInput.text("player", messages.component(viewer, "form.player-name"))
                    .width(300).maxLength(16).initial(query == null ? "" : query).build();
            actions.add(action(viewer, "action.search", "invite.by-name-tooltip", response ->
                    openInvitePicker(viewer, team, response.getText("player"))));
            actions.add(action(viewer, "action.back", "action.back-tooltip",
                    response -> openDashboard(viewer)));
            show(viewer, multi(viewer, "invite.title", messages.component(viewer,
                    found.isEmpty() ? "invite.no-results" : "invite.help"),
                    List.of(input), actions, 2));
        }, () -> openInvitePicker(viewer, team, query));
    }

    private void openRequests(Player viewer, TeamSnapshot team) {
        async(viewer, teams.joinRequests(team.id()), requests -> {
            var actions = new ArrayList<ActionButton>();
            for (var request : requests) {
                actions.add(rawAction(messages.component(viewer, "action.accept").append(Component.text(" · "
                                + request.player().lastKnownName())), messages.component(viewer, "request.accept-tooltip"),
                        response -> mutate(viewer, "success.request-accepted", teams.acceptJoinRequest(
                                new TeamRequests.TargetAction(viewer.getUniqueId(), team.id(),
                                        request.player().playerId())), snapshot -> openRequests(viewer, snapshot))));
                actions.add(rawAction(messages.component(viewer, "action.reject").append(Component.text(" · "
                                + request.player().lastKnownName())), messages.component(viewer, "request.reject-tooltip"),
                        response -> mutate(viewer, "success.request-rejected", teams.rejectJoinRequest(
                                new TeamRequests.TargetAction(viewer.getUniqueId(), team.id(),
                                        request.player().playerId())), snapshot -> openRequests(viewer, snapshot))));
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
                                            invitation.player().playerId())),
                                    snapshot -> openOutgoingInvitations(viewer, snapshot)))));
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
        if (teams.hasPermissionCached(viewer.getUniqueId(), "team.rename")) {
            actions.add(action(viewer, "settings.rename", "settings.rename-tooltip",
                    response -> show(viewer, textMutation(viewer, "rename.title", "form.team-name", "name",
                            team.name(), 24, value -> teams.rename(new TeamRequests.Rename(
                                    viewer.getUniqueId(), team.id(), value)), "success.renamed", team, null))));
        }
        if (teams.hasPermissionCached(viewer.getUniqueId(), "team.settings.manage")) {
            actions.add(action(viewer, "settings.tag", "settings.tag-tooltip",
                    response -> show(viewer, textMutation(viewer, "tag.title", "form.team-tag", "tag",
                            team.tag() == null ? "" : team.tag(), 8,
                            value -> teams.setTag(new TeamRequests.SetTag(viewer.getUniqueId(),
                                    team.id(), blankToNull(value))), "success.tagged", team, null))));
            actions.add(rawAction(messages.component(viewer, team.visibility() == TeamVisibility.PUBLIC
                            ? "settings.make-private" : "settings.make-public"),
                    messages.component(viewer, "settings.visibility-tooltip"), response -> {
                        if (team.visibility() == TeamVisibility.PUBLIC) openPrivateConfirmation(viewer, team);
                        else mutate(viewer, "success.visibility", teams.setVisibility(
                                        new TeamRequests.SetVisibility(viewer.getUniqueId(), team.id(),
                                                TeamVisibility.PUBLIC)),
                                snapshot -> show(viewer, settings(viewer, snapshot)));
                    }));
        }
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
                                String successKey, TeamSnapshot team, Component error) {
        var input = DialogInput.text(key, messages.component(viewer, label)).width(300)
                .maxLength(maxLength).initial(initial).build();
        var body = error == null ? messages.component(viewer, "form.save-help")
                : error.append(Component.newline()).append(messages.component(viewer, "form.save-help"));
        return notice(viewer, title, body, List.of(input),
                action(viewer, "action.save", "action.save-tooltip", response -> {
                    var submitted = response.getText(key);
                    var retry = (Runnable) () -> show(viewer, textMutation(viewer, title, label, key,
                            submitted, maxLength, operation, successKey, team, null));
                    async(viewer, operation.apply(submitted).handle(MutationAttempt::new), attempt -> {
                        if (attempt.exception() != null) {
                            show(viewer, errorDialog(viewer,
                                    messages.component(viewer, "error.database_unavailable"), retry,
                                    () -> show(viewer, settings(viewer, team))));
                            return;
                        }
                        if (attempt.result() instanceof OperationResult.Success<TeamSnapshot> success) {
                            viewer.sendMessage(messages.component(viewer, successKey));
                            show(viewer, settings(viewer, success.value()));
                        } else if (attempt.result() instanceof OperationResult.Failure<TeamSnapshot> failure) {
                            if (failure.code() == me.alexisbinh.openteams.api.TeamErrorCode.CONFLICT) {
                                async(viewer, teams.find(team.id()), current -> {
                                    if (current.isPresent()) show(viewer, textMutation(viewer, title, label,
                                            key, submitted, maxLength, operation, successKey,
                                            current.get(), failureMessage(viewer, failure)));
                                    else openDashboard(viewer);
                                });
                            } else if (failure.code()
                                    == me.alexisbinh.openteams.api.TeamErrorCode.INVALID_ARGUMENT) {
                                show(viewer, textMutation(viewer, title, label, key, submitted,
                                        maxLength, operation, successKey, team,
                                        failureMessage(viewer, failure)));
                            } else {
                                show(viewer, errorDialog(viewer, failureMessage(viewer, failure), retry,
                                        () -> show(viewer, settings(viewer, team))));
                            }
                        }
                    }, retry);
                }));
    }

    private void openPrivateConfirmation(Player viewer, TeamSnapshot team) {
        async(viewer, teams.joinRequests(team.id()), requests -> {
            var body = messages.component(viewer, "confirm.make-private",
                    Map.of("count", Integer.toString(requests.size())));
            var confirmation = Dialog.create(factory -> factory.empty()
                    .base(base(viewer, "confirm.title", body, List.of()))
                    .type(DialogType.confirmation(
                            action(viewer, "settings.make-private", "action.confirm-tooltip",
                                    response -> runIfCurrent(viewer, team, () -> mutate(viewer,
                                            "success.visibility", teams.setVisibility(
                                                    new TeamRequests.SetVisibility(viewer.getUniqueId(),
                                                            team.id(), TeamVisibility.PRIVATE)),
                                            snapshot -> show(viewer, settings(viewer, snapshot))),
                                            snapshot -> show(viewer, settings(viewer, snapshot)))),
                            action(viewer, "action.cancel", "action.cancel-tooltip",
                                    response -> show(viewer, settings(viewer, team))))));
            show(viewer, confirmation);
        }, () -> openPrivateConfirmation(viewer, team));
    }

    private Dialog banForm(Player viewer, TeamSnapshot team, TeamMemberSnapshot target) {
        return banForm(viewer, team, target, "", null);
    }

    private Dialog banForm(Player viewer, TeamSnapshot team, TeamMemberSnapshot target,
                           String reason, Component error) {
        var input = DialogInput.text("reason", messages.component(viewer, "form.ban-reason"))
                .width(300).maxLength(255).initial(reason).build();
        var body = error == null ? messages.component(viewer, "confirm.ban")
                : error.append(Component.newline()).append(messages.component(viewer, "confirm.ban"));
        var submit = action(viewer, "action.confirm", "action.confirm-tooltip", response ->
                runIfCurrent(viewer, team, () -> submitBan(viewer, team, target,
                        response.getText("reason")),
                        snapshot -> openMember(viewer, snapshot, target.playerId())));
        return notice(viewer, "ban.title", body, List.of(input), submit);
    }

    private void submitBan(Player viewer, TeamSnapshot team, TeamMemberSnapshot target,
                           String reason) {
        var retry = (Runnable) () -> show(viewer, banForm(viewer, team, target, reason, null));
        async(viewer, teams.ban(new TeamRequests.Ban(viewer.getUniqueId(), team.id(),
                target.playerId(), reason)).handle(MutationAttempt::new), attempt -> {
            if (attempt.exception() != null) {
                show(viewer, errorDialog(viewer,
                        messages.component(viewer, "error.database_unavailable"), retry,
                        () -> openMember(viewer, team, target.playerId())));
            } else if (attempt.result() instanceof OperationResult.Success<TeamSnapshot> success) {
                viewer.sendMessage(messages.component(viewer, "success.banned"));
                openMembers(viewer, success.value());
            } else if (attempt.result() instanceof OperationResult.Failure<TeamSnapshot> failure) {
                show(viewer, banForm(viewer, team, target, reason, failureMessage(viewer, failure)));
            }
        }, retry);
    }

    private void openBans(Player viewer, TeamSnapshot team) {
        async(viewer, teams.bans(team.id()), bans -> {
            var actions = new ArrayList<ActionButton>();
            bans.forEach(ban -> actions.add(rawAction(ban.player().lastKnownName(),
                    messages.component(viewer, "ban.unban-tooltip"), response -> mutate(viewer,
                            "success.unbanned", teams.unban(new TeamRequests.TargetAction(
                                    viewer.getUniqueId(), team.id(), ban.player().playerId())),
                            snapshot -> openBans(viewer, snapshot)))));
            actions.add(action(viewer, "action.back", "action.back-tooltip",
                    response -> show(viewer, settings(viewer, team))));
            show(viewer, multi(viewer, "bans.title", bans.isEmpty() ? messages.component(viewer, "bans.empty")
                    : messages.component(viewer, "bans.help"), List.of(), actions, 1));
        });
    }

    private Dialog disbandForm(Player viewer, TeamSnapshot team) {
        return disbandForm(viewer, team, "", null);
    }

    private Dialog disbandForm(Player viewer, TeamSnapshot team, String initial, Component error) {
        var input = DialogInput.text("confirm", messages.component(viewer, "form.confirm-team-name"))
                .width(300).maxLength(24).initial(initial).build();
        var body = error == null ? messages.component(viewer, "confirm.disband")
                : error.append(Component.newline()).append(messages.component(viewer, "confirm.disband"));
        return notice(viewer, "disband.title", body, List.of(input),
                action(viewer, "action.disband", "settings.disband-tooltip", response -> {
                    var submitted = response.getText("confirm");
                    if (!team.name().equals(submitted)) {
                        show(viewer, disbandForm(viewer, team, submitted,
                                messages.component(viewer, "error.confirm-name")));
                        return;
                    }
                    runIfCurrent(viewer, team, () -> mutate(viewer, "success.disbanded",
                            teams.disband(new TeamRequests.TeamAction(viewer.getUniqueId(), team.id()))));
                }));
    }

    private Dialog confirm(Player viewer, TeamSnapshot team, String messageKey, Runnable confirmed) {
        return confirm(viewer, team, messageKey, confirmed, () -> openDashboard(viewer),
                snapshot -> openDashboard(viewer));
    }

    private Dialog confirm(Player viewer, TeamSnapshot team, String messageKey, Runnable confirmed,
                           Runnable cancelled,
                           java.util.function.Consumer<TeamSnapshot> conflict) {
        return Dialog.create(factory -> factory.empty()
                .base(base(viewer, "confirm.title", messages.component(viewer, messageKey), List.of()))
                .type(DialogType.confirmation(
                        action(viewer, "action.confirm", "action.confirm-tooltip",
                                response -> runIfCurrent(viewer, team, confirmed, conflict)),
                        action(viewer, "action.cancel", "action.cancel-tooltip",
                                response -> cancelled.run()))));
    }

    private void runIfCurrent(Player viewer, TeamSnapshot expected, Runnable action) {
        runIfCurrent(viewer, expected, action, snapshot -> openDashboard(viewer));
    }

    private void runIfCurrent(Player viewer, TeamSnapshot expected, Runnable action,
                              java.util.function.Consumer<TeamSnapshot> conflict) {
        var current = teams.findCached(expected.id());
        if (current.isEmpty() || current.get().version() != expected.version()) {
            async(viewer, teams.find(expected.id()), refreshed -> {
                if (refreshed.isPresent()) conflict.accept(refreshed.get());
                else openDashboard(viewer);
            });
            return;
        }
        action.run();
    }

    private void mutate(Player viewer, String successKey,
                        CompletionStage<OperationResult<TeamSnapshot>> stage) {
        mutate(viewer, successKey, stage, ignored -> openDashboard(viewer));
    }

    private void mutate(Player viewer, String successKey,
                        CompletionStage<OperationResult<TeamSnapshot>> stage,
                        java.util.function.Consumer<TeamSnapshot> onSuccess) {
        async(viewer, stage.handle(MutationAttempt::new), attempt -> {
            if (attempt.exception() != null) {
                var restore = (Runnable) () -> restoreDestination(viewer, onSuccess);
                show(viewer, errorDialog(viewer,
                        messages.component(viewer, "error.database_unavailable"),
                        restore, restore));
                return;
            }
            var result = attempt.result();
            if (result instanceof OperationResult.Success<TeamSnapshot>) {
                viewer.sendMessage(messages.component(viewer, successKey));
                onSuccess.accept(((OperationResult.Success<TeamSnapshot>) result).value());
            } else if (result instanceof OperationResult.Failure<TeamSnapshot> failure) {
                if (failure.code() == me.alexisbinh.openteams.api.TeamErrorCode.CONFLICT
                        || failure.code() == me.alexisbinh.openteams.api.TeamErrorCode.NOT_FOUND) {
                    reloadTeam(viewer, onSuccess);
                } else {
                    var restore = (Runnable) () -> restoreDestination(viewer, onSuccess);
                    show(viewer, errorDialog(viewer, failureMessage(viewer, failure),
                            restore, restore));
                }
            }
        });
    }

    private void reloadTeam(Player viewer,
                            java.util.function.Consumer<TeamSnapshot> destination) {
        var membership = teams.membershipCached(viewer.getUniqueId());
        var id = membership.optionalTeam().map(TeamSnapshot::id).orElse(null);
        if (id == null) {
            openDashboard(viewer);
            return;
        }
        async(viewer, teams.find(id), current -> {
            if (current.isPresent()) destination.accept(current.get());
            else openDashboard(viewer);
        });
    }

    private void restoreDestination(Player viewer,
                                    java.util.function.Consumer<TeamSnapshot> destination) {
        var id = teams.membershipCached(viewer.getUniqueId()).optionalTeam()
                .map(TeamSnapshot::id).orElse(null);
        if (id == null) {
            openDashboard(viewer);
            return;
        }
        async(viewer, teams.find(id), current -> {
            if (current.isPresent()) destination.accept(current.get());
            else openDashboard(viewer);
        });
    }

    private void showError(Player viewer, Component message) {
        show(viewer, errorDialog(viewer, message, () -> openDashboard(viewer),
                () -> openDashboard(viewer)));
    }

    private Component failureMessage(Player viewer, OperationResult.Failure<?> failure) {
        var messageKey = failure.messageKey();
        if (messageKey.startsWith("openteams.")) {
            messageKey = messageKey.substring("openteams.".length());
        }
        return messages.component(viewer, messageKey, failure.messageArguments());
    }

    private <T> void async(Player viewer, CompletionStage<T> stage,
                           java.util.function.Consumer<T> success) {
        async(viewer, stage, success, () -> openDashboard(viewer));
    }

    private <T> void async(Player viewer, CompletionStage<T> stage,
                           java.util.function.Consumer<T> success, Runnable retry) {
        var generation = nextScreenGeneration.incrementAndGet();
        screenGenerations.put(viewer.getUniqueId(), generation);
        showDirect(viewer, loadingDialog(viewer));
        stage.whenComplete((value, failure) -> viewer.getScheduler().run(plugin, task -> {
            if (!viewer.isOnline()) {
                screenGenerations.remove(viewer.getUniqueId(), generation);
                return;
            }
            if (!java.util.Objects.equals(screenGenerations.get(viewer.getUniqueId()), generation)) return;
            if (failure == null) success.accept(value);
            else show(viewer, errorDialog(viewer,
                    messages.component(viewer, "error.database_unavailable"), retry,
                    () -> openDashboard(viewer)));
        }, null));
    }

    private Dialog loadingDialog(Player viewer) {
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.component(viewer, "loading.title"))
                        .body(List.of(DialogBody.plainMessage(
                                messages.component(viewer, "status.loading"), 360)))
                        .canCloseWithEscape(false).build())
                .type(DialogType.notice(action(viewer, "action.back", "action.back-tooltip",
                        response -> openDashboard(viewer)))));
    }

    private Dialog errorDialog(Player viewer, Component message, Runnable retry, Runnable back) {
        return multi(viewer, "error.title", message, List.of(), List.of(
                action(viewer, "action.retry", "action.retry-tooltip", response -> retry.run()),
                action(viewer, "action.back", "action.back-tooltip", response -> back.run())), 1);
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

    private boolean hasAvailableAddonAction(Player viewer, TeamSnapshot team,
                                            TeamUiRegistry.Area area) {
        var context = new TeamUiRegistry.UiContext(viewer.getUniqueId(), team.id(), team.version());
        return addonActions.get().stream().anyMatch(item -> item.area() == area
                && teams.hasPermissionCached(viewer.getUniqueId(), item.permission())
                && safeAvailable(item, context));
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
        screenGenerations.remove(viewer.getUniqueId());
        showDirect(viewer, dialog);
    }

    private void showDirect(Player viewer, Dialog dialog) {
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

    private record MutationAttempt<T>(T result, Throwable exception) { }

    private record MemberData(Map<UUID, TeamDirectory.PlayerSummary> players,
                              List<TeamDirectory.Role> roles) { }
}
