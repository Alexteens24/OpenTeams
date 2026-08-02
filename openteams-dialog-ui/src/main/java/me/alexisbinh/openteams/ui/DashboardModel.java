package me.alexisbinh.openteams.ui;

import java.util.EnumSet;
import java.util.Set;
import me.alexisbinh.openteams.api.TeamService;
import me.alexisbinh.openteams.api.TeamSnapshot;

/** Permission-filtered dashboard actions shared by dialog and chat renderers. */
public record DashboardModel(TeamSnapshot team, Set<Action> actions) {
    public DashboardModel {
        actions = Set.copyOf(actions);
    }

    public static DashboardModel create(TeamService teams, java.util.UUID viewerId,
                                        TeamSnapshot snapshot) {
        var actions = EnumSet.of(Action.MEMBERS, Action.CHAT);
        if (teams.hasPermissionCached(viewerId, "team.invite")) {
            actions.add(Action.INVITE);
            actions.add(Action.SENT_INVITATIONS);
        }
        if (teams.hasPermissionCached(viewerId, "team.join-request.accept")) {
            actions.add(Action.REQUESTS);
        }
        if (canOpenSettings(teams, viewerId, snapshot)) actions.add(Action.SETTINGS);
        if (!snapshot.ownerId().equals(viewerId)) actions.add(Action.LEAVE);
        return new DashboardModel(snapshot, actions);
    }

    public static boolean canOpenSettings(TeamService teams, java.util.UUID viewerId,
                                          TeamSnapshot snapshot) {
        return snapshot.ownerId().equals(viewerId)
                || teams.hasPermissionCached(viewerId, "team.rename")
                || teams.hasPermissionCached(viewerId, "team.settings.manage")
                || teams.hasPermissionCached(viewerId, "team.ban");
    }

    public enum Action {
        MEMBERS, INVITE, SENT_INVITATIONS, REQUESTS, CHAT, SETTINGS, LEAVE
    }
}
