package me.alexisbinh.openteams.core.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import me.alexisbinh.openteams.api.TeamErrorCode;
import me.alexisbinh.openteams.api.TeamState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcTeamStoreTest {
    @TempDir
    Path temporaryDirectory;

    private DatabaseManager database;
    private JdbcTeamStore store;

    @BeforeEach
    void setUp() throws Exception {
        var config = new DatabaseConfig(
                DatabaseConfig.Type.SQLITE,
                "test",
                "jdbc:sqlite:" + temporaryDirectory.resolve("teams.db"),
                "",
                "",
                1,
                3000
        );
        database = new DatabaseManager(config, Clock.systemUTC());
        database.start();
        store = new JdbcTeamStore(
                database.dataSource(), config.namespace(), Clock.systemUTC(), 20, 60_000);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void lifecycleMaintainsOwnerAndMembershipInvariants() throws Exception {
        var owner = UUID.randomUUID();
        var member = UUID.randomUUID();

        var created = store.create(owner, "Open Teams", "OT");
        assertThat(created.ownerId()).isEqualTo(owner);
        assertThat(created.members()).hasSize(1);

        store.invite(created.id(), owner, member);
        var joined = store.acceptInvitation(created.id(), member);
        assertThat(joined.members()).hasSize(2);

        var transferred = store.transfer(created.id(), owner, member);
        assertThat(transferred.ownerId()).isEqualTo(member);
        assertThat(transferred.members())
                .filteredOn(candidate -> candidate.playerId().equals(member))
                .singleElement()
                .extracting(candidate -> candidate.roleKey())
                .isEqualTo("owner");

        var afterLeave = store.leave(created.id(), owner);
        assertThat(afterLeave.members()).hasSize(1);

        var disbanded = store.disband(created.id(), member);
        assertThat(disbanded.state()).isEqualTo(TeamState.DISBANDED);
        assertThat(disbanded.members()).isEmpty();
    }

    @Test
    void uniqueMembershipRejectsSecondTeam() throws Exception {
        var owner = UUID.randomUUID();
        store.create(owner, "First Team", "ONE");

        assertThatThrownBy(() -> store.create(owner, "Second Team", "TWO"))
                .isInstanceOf(DomainFailure.class)
                .extracting(error -> ((DomainFailure) error).code())
                .isEqualTo(TeamErrorCode.ALREADY_IN_TEAM);
    }

    @Test
    void ownerCannotLeaveWithoutTransfer() throws Exception {
        var owner = UUID.randomUUID();
        var team = store.create(owner, "Safe Team", "SAFE");

        assertThatThrownBy(() -> store.leave(team.id(), owner))
                .isInstanceOf(DomainFailure.class)
                .extracting(error -> ((DomainFailure) error).code())
                .isEqualTo(TeamErrorCode.FORBIDDEN);
    }

    @Test
    void joinRequestAndBanAreAtomicAndMutuallyConsistent() throws Exception {
        var owner = UUID.randomUUID();
        var applicant = UUID.randomUUID();
        var team = store.create(owner, "Requests Team", "REQ");

        store.requestJoin(team.id(), applicant);
        var joined = store.acceptJoinRequest(team.id(), owner, applicant);
        assertThat(joined.members())
                .extracting(member -> member.playerId())
                .contains(applicant);

        var banned = store.ban(team.id(), owner, applicant, "test");
        assertThat(banned.members())
                .extracting(member -> member.playerId())
                .doesNotContain(applicant);

        assertThatThrownBy(() -> store.requestJoin(team.id(), applicant))
                .isInstanceOf(DomainFailure.class)
                .extracting(error -> ((DomainFailure) error).code())
                .isEqualTo(TeamErrorCode.FORBIDDEN);

        store.unban(team.id(), owner, applicant);
        store.requestJoin(team.id(), applicant);
    }

    @Test
    void auditUsesCallerCorrelationId() throws Exception {
        var correlationId = UUID.randomUUID();
        store.correlated(correlationId,
                () -> store.create(UUID.randomUUID(), "Audit Team", "AUD"));

        try (var connection = database.dataSource().getConnection();
             var statement = connection.prepareStatement(
                     "SELECT correlation_id FROM audit_entries WHERE namespace = ?")) {
            statement.setString(1, "test");
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo(correlationId.toString());
            }
        }
    }

    @Test
    void roleChangeResolvesPermissionsAndEnforcesHierarchy() throws Exception {
        var owner = UUID.randomUUID();
        var moderator = UUID.randomUUID();
        var member = UUID.randomUUID();
        var team = store.create(owner, "Role Team", "ROLE");
        store.invite(team.id(), owner, moderator);
        store.acceptInvitation(team.id(), moderator);
        store.invite(team.id(), owner, member);
        store.acceptInvitation(team.id(), member);

        var promoted = store.changeRole(team.id(), owner, moderator, "moderator");
        assertThat(promoted.members())
                .filteredOn(candidate -> candidate.playerId().equals(moderator))
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.roleKey()).isEqualTo("moderator");
                    assertThat(candidate.hasPermission("team.kick")).isTrue();
                    assertThat(candidate.hasPermission("team.role.change")).isFalse();
                });

        assertThatThrownBy(() ->
                store.changeRole(team.id(), moderator, member, "co_owner"))
                .isInstanceOf(DomainFailure.class)
                .extracting(error -> ((DomainFailure) error).code())
                .isEqualTo(TeamErrorCode.FORBIDDEN);
    }

    @Test
    void settingIsTransactionalAndDoctorReportsHealthyState() throws Exception {
        var owner = UUID.randomUUID();
        var team = store.create(owner, "Settings Team", "SET");

        var changed = store.setSetting(
                team.id(), owner, "openteams:friendly-fire", "true",
                "team.settings.manage");

        assertThat(changed.settings())
                .containsEntry("openteams:friendly-fire", "true");
        assertThat(changed.version()).isGreaterThan(team.version());
        assertThat(database.doctor().healthy()).isTrue();
    }
}
