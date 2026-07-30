package me.alexisbinh.openteams.ui;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import me.alexisbinh.openteams.api.TeamService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public final class ChatTeamUserInterface implements TeamUserInterface {
    private final TeamService teams;

    public ChatTeamUserInterface(TeamService teams) {
        this.teams = teams;
    }

    @Override
    public void openDashboard(Player viewer) {
        var team = teams.findByPlayerCached(viewer.getUniqueId());
        if (team.isEmpty()) {
            viewer.sendMessage(Component.text("OpenTeams", NamedTextColor.AQUA)
                    .append(Component.text(" · You are not in a team.", NamedTextColor.GRAY)));
            viewer.sendMessage(Component.text("Use /team create <name> to start one.",
                    NamedTextColor.YELLOW));
            return;
        }
        var snapshot = team.get();
        viewer.sendMessage(Component.text("OpenTeams · ", NamedTextColor.AQUA)
                .append(Component.text(snapshot.name(), NamedTextColor.WHITE)));
        viewer.sendMessage(Component.text("Tag: " + (snapshot.tag() == null ? "—" : snapshot.tag())
                + "  Members: " + snapshot.members().size() + "/" + snapshot.memberLimit(),
                NamedTextColor.GRAY));
        viewer.sendMessage(Component.text(
                "Commands: /team info, /team invite, /team chat, /team leave",
                NamedTextColor.YELLOW));
    }

    @Override
    public CompletionStage<Boolean> confirm(Player viewer, UUID token) {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public String mode() {
        return "chat";
    }
}
