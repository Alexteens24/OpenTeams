package me.alexisbinh.openteams.ui;

import me.alexisbinh.openteams.api.MembershipLookup;
import me.alexisbinh.openteams.api.TeamService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class ChatTeamUserInterface implements TeamUserInterface {
    private final Plugin plugin;
    private final TeamService teams;
    private final LocalizedMessages messages;

    public ChatTeamUserInterface(Plugin plugin, TeamService teams,
                                 LocalizedMessages messages) {
        this.plugin = plugin;
        this.teams = teams;
        this.messages = messages;
    }

    @Override
    public void openDashboard(Player viewer) {
        var membership = teams.membershipCached(viewer.getUniqueId());
        if (membership.status() == MembershipLookup.Status.LOADING) {
            loadDashboard(viewer);
            return;
        }
        if (membership.status() == MembershipLookup.Status.FAILED) {
            loadDashboard(viewer);
            return;
        }
        if (membership.status() == MembershipLookup.Status.ABSENT) {
            viewer.sendMessage(messages.component(viewer, "dashboard.title"));
            viewer.sendMessage(messages.component(viewer, "dashboard.no-team"));
            viewer.sendMessage(link(messages.component(viewer, "command.create-button"), "/team create ")
                    .append(Component.space()).append(link(
                            messages.component(viewer, "command.explore-button"), "/team explore"))
                    .append(Component.space()).append(link(
                            messages.component(viewer, "command.invitations-button"),
                            "/team invitations"))
                    .append(Component.space()).append(link(
                            messages.component(viewer, "command.my-requests-button"),
                            "/team myrequests")));
            return;
        }
        var snapshot = membership.optionalTeam().orElseThrow();
        var model = DashboardModel.create(teams, viewer.getUniqueId(), snapshot);
        viewer.sendMessage(messages.component(viewer, "dashboard.title")
                .append(Component.text(" · " + snapshot.name())));
        viewer.sendMessage(messages.component(viewer, "label.tag")
                .append(Component.text(" " + (snapshot.tag() == null ? "—" : snapshot.tag()) + "  "))
                .append(messages.component(viewer, "label.members"))
                .append(Component.text(" " + snapshot.members().size() + "/" + snapshot.memberLimit())));
        var actions = link(messages.component(viewer, "command.info-button"), "/team info");
        if (model.actions().contains(DashboardModel.Action.MEMBERS)) actions = actions
                .append(Component.space()).append(link(
                        messages.component(viewer, "command.members-button"), "/team members"));
        if (model.actions().contains(DashboardModel.Action.INVITE)) actions = actions
                .append(Component.space()).append(link(
                        messages.component(viewer, "command.invite-button"), "/team invite "));
        if (model.actions().contains(DashboardModel.Action.REQUESTS)) actions = actions
                .append(Component.space()).append(link(
                        messages.component(viewer, "command.requests-button"), "/team requests"));
        if (model.actions().contains(DashboardModel.Action.SETTINGS)) actions = actions
                .append(Component.space()).append(link(
                        messages.component(viewer, "command.settings-button"), "/team settings"));
        actions = actions.append(Component.space()).append(link(
                messages.component(viewer, "command.chat-button"), "/team chat "));
        if (model.actions().contains(DashboardModel.Action.LEAVE)) actions = actions
                .append(Component.space()).append(link(
                        messages.component(viewer, "command.leave-button"), "/team leave"));
        viewer.sendMessage(actions);
    }

    private void sendLoadFailure(Player viewer) {
        viewer.sendMessage(messages.component(viewer, "error.database_unavailable")
                .append(Component.space())
                .append(link(messages.component(viewer, "action.retry"), "/team")));
    }

    private void loadDashboard(Player viewer) {
        viewer.sendMessage(messages.component(viewer, "status.loading-team"));
        teams.loadMembership(viewer.getUniqueId()).whenComplete((ignored, failure) ->
                viewer.getScheduler().run(plugin, task -> {
                    if (!viewer.isOnline()) return;
                    if (failure == null) openDashboard(viewer);
                    else sendLoadFailure(viewer);
                }, null));
    }

    private static Component link(Component label, String command) {
        return label.clickEvent(command.endsWith(" ")
                ? ClickEvent.suggestCommand(command) : ClickEvent.runCommand(command));
    }

    @Override
    public String mode() {
        return "chat";
    }
}
