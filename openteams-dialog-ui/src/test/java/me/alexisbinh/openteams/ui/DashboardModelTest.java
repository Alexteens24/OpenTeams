package me.alexisbinh.openteams.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.alexisbinh.openteams.api.TeamId;
import me.alexisbinh.openteams.api.TeamMemberSnapshot;
import me.alexisbinh.openteams.api.TeamService;
import me.alexisbinh.openteams.api.TeamSnapshot;
import me.alexisbinh.openteams.api.TeamState;
import me.alexisbinh.openteams.api.TeamVisibility;
import org.junit.jupiter.api.Test;

class DashboardModelTest {
    @Test
    void settingsIsReachableForRenameOnlyRoleAndLeaveIsHiddenForOwner() {
        var owner = UUID.randomUUID();
        var member = UUID.randomUUID();
        var team = snapshot(owner, member);

        var memberModel = DashboardModel.create(service(Set.of("team.rename")), member, team);
        var ownerModel = DashboardModel.create(service(Set.of()), owner, team);

        assertThat(memberModel.actions()).contains(
                DashboardModel.Action.SETTINGS, DashboardModel.Action.LEAVE);
        assertThat(ownerModel.actions()).contains(DashboardModel.Action.SETTINGS)
                .doesNotContain(DashboardModel.Action.LEAVE);
    }

    @Test
    void managementActionsFollowIndividualPermissions() {
        var owner = UUID.randomUUID();
        var member = UUID.randomUUID();
        var model = DashboardModel.create(service(Set.of(
                "team.invite", "team.join-request.accept")), member, snapshot(owner, member));

        assertThat(model.actions()).contains(
                DashboardModel.Action.INVITE,
                DashboardModel.Action.SENT_INVITATIONS,
                DashboardModel.Action.REQUESTS)
                .doesNotContain(DashboardModel.Action.SETTINGS);
    }

    private static TeamService service(Set<String> permissions) {
        return (TeamService) Proxy.newProxyInstance(TeamService.class.getClassLoader(),
                new Class<?>[]{TeamService.class}, (proxy, method, args) -> {
                    if (method.getName().equals("hasPermissionCached")) {
                        return permissions.contains(args[1]);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static TeamSnapshot snapshot(UUID owner, UUID member) {
        var now = Instant.now();
        return new TeamSnapshot(TeamId.random(), "Test Team", "test team", null, owner,
                TeamState.ACTIVE, TeamVisibility.PRIVATE, 20, 1, now, now, Map.of(), List.of(
                new TeamMemberSnapshot(owner, "owner", Set.of("*"), now, now),
                new TeamMemberSnapshot(member, "member", Set.of(), now, now)));
    }
}
