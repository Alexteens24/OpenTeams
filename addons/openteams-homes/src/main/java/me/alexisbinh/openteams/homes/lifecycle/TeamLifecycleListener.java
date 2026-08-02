package me.alexisbinh.openteams.homes.lifecycle;

import me.alexisbinh.openteams.api.event.TeamMutationCommittedEvent;
import me.alexisbinh.openteams.api.mutation.MutationType;
import me.alexisbinh.openteams.homes.teleport.WarmupManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class TeamLifecycleListener implements Listener {
    private final WarmupManager warmups;
    private final CleanupService cleanup;

    public TeamLifecycleListener(WarmupManager warmups, CleanupService cleanup) {
        this.warmups = warmups;
        this.cleanup = cleanup;
    }

    @EventHandler
    public void onMutation(TeamMutationCommittedEvent event) {
        var type = event.intent().type();
        if (type == MutationType.TEAM_DISBAND) {
            warmups.cancelTeam(event.after().id());
            cleanup.enqueue(event.after().id());
            return;
        }
        if (type == MutationType.MEMBER_LEAVE || type == MutationType.MEMBER_KICK
                || type == MutationType.MEMBER_ROLE_CHANGE || type == MutationType.OWNER_TRANSFER) {
            warmups.cancelTeam(event.after().id());
        }
    }
}
