package me.alexisbinh.openteams.ui;

import me.alexisbinh.openteams.api.TeamService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;

public final class ChatTeamUserInterface implements TeamUserInterface {
    private final TeamService teams;
    private final LocalizedMessages messages;

    public ChatTeamUserInterface(TeamService teams, LocalizedMessages messages) {
        this.teams = teams;
        this.messages = messages;
    }

    @Override
    public void openDashboard(Player viewer) {
        var team = teams.findByPlayerCached(viewer.getUniqueId());
        if (team.isEmpty()) {
            viewer.sendMessage(messages.component(viewer, "dashboard.title"));
            viewer.sendMessage(messages.component(viewer, "dashboard.no-team"));
            viewer.sendMessage(link(messages.component(viewer, "command.create-button"), "/team create ")
                    .append(Component.space()).append(link(
                            messages.component(viewer, "command.explore-button"), "/team explore"))
                    .append(Component.space()).append(link(
                            messages.component(viewer, "command.invitations-button"),
                            "/team invitations")));
            return;
        }
        var snapshot = team.get();
        viewer.sendMessage(messages.component(viewer, "dashboard.title")
                .append(Component.text(" · " + snapshot.name())));
        viewer.sendMessage(messages.component(viewer, "label.tag")
                .append(Component.text(" " + (snapshot.tag() == null ? "—" : snapshot.tag()) + "  "))
                .append(messages.component(viewer, "label.members"))
                .append(Component.text(" " + snapshot.members().size() + "/" + snapshot.memberLimit())));
        viewer.sendMessage(link(messages.component(viewer, "command.info-button"), "/team info")
                .append(Component.space()).append(link(
                        messages.component(viewer, "command.invite-button"), "/team invite "))
                .append(Component.space()).append(link(
                        messages.component(viewer, "command.chat-button"), "/team chat "))
                .append(Component.space()).append(link(
                        messages.component(viewer, "command.leave-button"), "/team leave")));
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
