package me.alexisbinh.openteams.core.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import me.alexisbinh.openteams.api.TeamErrorCode;
import me.alexisbinh.openteams.api.TeamId;
import me.alexisbinh.openteams.api.TeamState;
import me.alexisbinh.openteams.api.TeamVisibility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcTeamStoreTest {
    @TempDir
    Path temporaryDirectory;

    private DatabaseManager database;
    private JdbcTeamStore store;
    private final java.util.Set<String> runtimePermissions =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

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
                database.dataSource(), config.namespace(), Clock.systemUTC(), 20, 60_000,
                database, (role, permission) -> runtimePermissions.contains(role + ":" + permission));
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

        var recreated = store.create(member, "Open Teams", "OT");
        assertThat(recreated.id()).isNotEqualTo(disbanded.id());
        assertThat(recreated.ownerId()).isEqualTo(member);
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
    void coOwnerCanUseSeededRenameAndSettingsPermissions() throws Exception {
        var owner = UUID.randomUUID();
        var coOwner = UUID.randomUUID();
        var team = store.create(owner, "Permission Team", "PERM");
        store.invite(team.id(), owner, coOwner);
        store.acceptInvitation(team.id(), coOwner);
        store.changeRole(team.id(), owner, coOwner, "co_owner");

        var renamed = store.rename(team.id(), coOwner, "Renamed Team");
        var visible = store.setVisibility(renamed.id(), coOwner, TeamVisibility.PUBLIC);

        assertThat(visible.name()).isEqualTo("Renamed Team");
        assertThat(visible.visibility()).isEqualTo(TeamVisibility.PUBLIC);
    }

    @Test
    void runtimeDefaultRoleCanAuthorizeAddonSetting() throws Exception {
        var owner = UUID.randomUUID();
        var moderator = UUID.randomUUID();
        var team = store.create(owner, "Addon Team", "ADD");
        store.invite(team.id(), owner, moderator);
        store.acceptInvitation(team.id(), moderator);
        store.changeRole(team.id(), owner, moderator, "moderator");
        runtimePermissions.add("moderator:example.use");

        var changed = store.setSetting(team.id(), moderator,
                "example:enabled", "true", "example.use");

        assertThat(changed.settings()).containsEntry("example:enabled", "true");
    }

    @Test
    void joinRequestAndBanAreAtomicAndMutuallyConsistent() throws Exception {
        var owner = UUID.randomUUID();
        var applicant = UUID.randomUUID();
        var team = store.create(owner, "Requests Team", "REQ");
        team = store.setVisibility(team.id(), owner, TeamVisibility.PUBLIC);
        var teamId = team.id();

        store.requestJoin(teamId, applicant);
        var joined = store.acceptJoinRequest(teamId, owner, applicant);
        assertThat(joined.members())
                .extracting(member -> member.playerId())
                .contains(applicant);

        var banned = store.ban(teamId, owner, applicant, "test");
        assertThat(banned.members())
                .extracting(member -> member.playerId())
                .doesNotContain(applicant);

        assertThatThrownBy(() -> store.requestJoin(teamId, applicant))
                .isInstanceOf(DomainFailure.class)
                .extracting(error -> ((DomainFailure) error).code())
                .isEqualTo(TeamErrorCode.FORBIDDEN);

        store.unban(teamId, owner, applicant);
        store.requestJoin(teamId, applicant);
    }

    @Test
    void discoveryAndPlayerDirectoryExposeOnlyPublicActiveTeams() throws Exception {
        var publicOwner = UUID.randomUUID();
        var privateOwner = UUID.randomUUID();
        store.rememberPlayer(publicOwner, "PublicOwner");
        var publicTeam = store.create(publicOwner, "Public Guild", "PUB");
        store.setVisibility(publicTeam.id(), publicOwner, TeamVisibility.PUBLIC);
        store.create(privateOwner, "Hidden Guild", "HID");

        var page = store.searchPublicTeams("pub", 0, 10);
        assertThat(page.items()).singleElement()
                .extracting(item -> item.id()).isEqualTo(publicTeam.id());
        assertThat(store.resolvePlayers(java.util.List.of(publicOwner)).get(publicOwner)
                .lastKnownName()).isEqualTo("PublicOwner");
    }

    @Test
    void playerDirectorySupportsNormalizedPrefixAndAmbiguousExactNames() throws Exception {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var third = UUID.randomUUID();
        store.rememberPlayer(first, "AlexisBinh");
        store.rememberPlayer(second, "ALEXISBINH");
        store.rememberPlayer(third, "AlexisBinh_2");

        assertThat(store.findPlayersExact("alexisbinh"))
                .extracting(item -> item.playerId()).containsExactlyInAnyOrder(first, second);
        assertThat(store.searchPlayers("Alexis", 2)).hasSize(2)
                .allSatisfy(item -> assertThat(item.lastKnownName())
                        .startsWithIgnoringCase("alexis"));
    }

    @Test
    void makingTeamPrivateDeletesPendingJoinRequestsAndUsesSpecificFailureKey()
            throws Exception {
        var owner = UUID.randomUUID();
        var team = store.create(owner, "Visibility Team", "VIS");
        team = store.setVisibility(team.id(), owner, TeamVisibility.PUBLIC);
        store.requestJoin(team.id(), UUID.randomUUID());
        assertThat(store.joinRequests(team.id())).hasSize(1);

        store.setVisibility(team.id(), owner, TeamVisibility.PRIVATE);
        assertThat(store.joinRequests(team.id())).isEmpty();
        var id = team.id();
        assertThatThrownBy(() -> store.requestJoin(id, UUID.randomUUID()))
                .isInstanceOfSatisfying(DomainFailure.class, failure ->
                        assertThat(failure.messageKey()).isEqualTo("openteams.error.private-team"));
    }

    @Test
    void pendingPlayerFlowsCanBeListedAndRemoved() throws Exception {
        var owner = UUID.randomUUID();
        var invited = UUID.randomUUID();
        var applicant = UUID.randomUUID();
        store.rememberPlayer(owner, "OwnerName");
        store.rememberPlayer(invited, "InvitedName");
        store.rememberPlayer(applicant, "ApplicantName");
        var team = store.create(owner, "Flow Team", "FLOW");
        team = store.setVisibility(team.id(), owner, TeamVisibility.PUBLIC);
        var teamId = team.id();

        store.invite(teamId, owner, invited);
        assertThat(store.invitations(invited)).singleElement()
                .satisfies(item -> assertThat(item.inviter().lastKnownName()).isEqualTo("OwnerName"));
        assertThat(store.outgoingInvitations(teamId)).singleElement()
                .satisfies(item -> assertThat(item.player().lastKnownName()).isEqualTo("InvitedName"));
        store.revokeInvitation(teamId, owner, invited);
        assertThat(store.invitations(invited)).isEmpty();

        store.requestJoin(teamId, applicant);
        assertThat(store.joinRequests(teamId)).singleElement()
                .satisfies(item -> assertThat(item.player().lastKnownName()).isEqualTo("ApplicantName"));
        assertThat(store.joinRequestsByPlayer(applicant)).singleElement()
                .satisfies(item -> assertThat(item.team().id()).isEqualTo(teamId));
        store.rejectJoinRequest(teamId, owner, applicant);
        assertThat(store.joinRequests(teamId)).isEmpty();
    }

    @Test
    void privateTeamRejectsJoinRequests() throws Exception {
        var owner = UUID.randomUUID();
        var team = store.create(owner, "Private Team", "PRI");

        assertThatThrownBy(() -> store.requestJoin(team.id(), UUID.randomUUID()))
                .isInstanceOf(DomainFailure.class)
                .extracting(error -> ((DomainFailure) error).code())
                .isEqualTo(TeamErrorCode.FORBIDDEN);
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

    @Test
    void batchMembershipLookupDeduplicatesTeamsAndOmitsAbsentPlayers()
            throws Exception {
        var owner = UUID.randomUUID();
        var member = UUID.randomUUID();
        var absent = UUID.randomUUID();
        var team = store.create(owner, "Batch Team", "BATCH");
        store.invite(team.id(), owner, member);
        store.acceptInvitation(team.id(), member);

        var result = store.findByPlayers(java.util.List.of(owner, member, absent));

        assertThat(result).containsOnlyKeys(owner, member);
        assertThat(result.get(owner).id()).isEqualTo(team.id());
        assertThat(result.get(member).id()).isEqualTo(team.id());
        assertThat(result.get(owner)).isSameAs(result.get(member));
    }

    @Test
    void foreignKeysRejectOrphanedMembershipRows() throws Exception {
        try (var connection = database.dataSource().getConnection();
             var statement = connection.prepareStatement("""
                     INSERT INTO team_members(
                         namespace,player_id,team_id,role_key,joined_at,last_active_at,version
                     ) VALUES(?,?,?,?,?,?,0)
                     """)) {
            statement.setString(1, "test");
            statement.setString(2, UUID.randomUUID().toString());
            statement.setString(3, TeamId.random().toString());
            statement.setString(4, "member");
            statement.setLong(5, 0);
            statement.setLong(6, 0);

            assertThatThrownBy(statement::executeUpdate)
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test
    void migrationUsesPluginClassLoaderWhenThreadContextCannotSeeResources()
            throws Exception {
        var config = new DatabaseConfig(
                DatabaseConfig.Type.SQLITE,
                "classloader-test",
                "jdbc:sqlite:" + temporaryDirectory.resolve("classloader.db"),
                "", "", 1, 3000);
        var thread = Thread.currentThread();
        var previous = thread.getContextClassLoader();
        thread.setContextClassLoader(new ClassLoader(null) { });
        try (var isolated = new DatabaseManager(config, Clock.systemUTC())) {
            isolated.start();
            try (var connection = isolated.dataSource().getConnection();
                 var statement = connection.prepareStatement(
                         "SELECT next_token FROM core_lease_fences WHERE namespace = ?")) {
                statement.setString(1, config.namespace());
                try (var result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                }
            }
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    @Test
    void staleInstanceCannotCommitAfterFencedLeaseTakeover() throws Exception {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var config = new DatabaseConfig(
                DatabaseConfig.Type.SQLITE,
                "fence-test",
                "jdbc:sqlite:" + temporaryDirectory.resolve("fence.db"),
                "", "", 1, 3000);
        try (var first = new DatabaseManager(config, clock)) {
            first.start();
            var firstStore = new JdbcTeamStore(
                    first.dataSource(), config.namespace(), clock, 20, 60_000, first,
                    (role, permission) -> false);
            var owner = UUID.randomUUID();
            var team = firstStore.create(owner, "Fenced Team", "FENCE");
            var firstToken = first.fenceToken();

            clock.advanceSeconds(46);
            try (var second = new DatabaseManager(config, clock)) {
                second.start();
                assertThat(second.fenceToken()).isGreaterThan(firstToken);

                assertThatThrownBy(() ->
                        firstStore.rename(team.id(), owner, "Stale Writer"))
                        .isInstanceOf(LeaseLostException.class);
                assertThat(first.leaseHeld()).isFalse();
            }
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
