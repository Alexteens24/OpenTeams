package me.alexisbinh.openteams.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import me.alexisbinh.openteams.api.OperationResult;
import me.alexisbinh.openteams.api.TeamErrorCode;
import me.alexisbinh.openteams.api.TeamRequests;
import me.alexisbinh.openteams.api.event.TeamMutationCommittedEvent;
import me.alexisbinh.openteams.api.extension.MutationPolicyRegistry;
import me.alexisbinh.openteams.api.extension.TeamPermissionRegistry;
import me.alexisbinh.openteams.api.mutation.PolicyDecision;
import me.alexisbinh.openteams.core.cache.TeamCache;
import me.alexisbinh.openteams.core.database.DatabaseConfig;
import me.alexisbinh.openteams.core.database.DatabaseManager;
import me.alexisbinh.openteams.core.database.JdbcTeamStore;
import me.alexisbinh.openteams.core.extension.ExtensionRegistries;
import me.alexisbinh.openteams.core.runtime.RuntimeController;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TeamServiceImplTest {
    @TempDir
    Path temporaryDirectory;

    private DatabaseManager database;
    private TeamServiceImpl service;
    private ExtensionRegistries registries;
    private RuntimeController runtime;
    private final ArrayList<TeamMutationCommittedEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        var config = new DatabaseConfig(
                DatabaseConfig.Type.SQLITE,
                "service-test",
                "jdbc:sqlite:" + temporaryDirectory.resolve("teams.db"),
                "", "", 1, 3000);
        database = new DatabaseManager(config, Clock.systemUTC());
        database.start();
        registries = new ExtensionRegistries();
        runtime = new RuntimeController();
        runtime.writableAfterStartup();
        service = new TeamServiceImpl(
                new JdbcTeamStore(database.dataSource(), config.namespace(),
                        Clock.systemUTC(), 20, 60_000, database,
                        registries::hasDefaultPermission),
                new TeamCache(), 1, runtime, registries, events::add,
                database::leaseHeld);
    }

    @AfterEach
    void tearDown() {
        service.close();
        database.close();
    }

    @Test
    void committedEventAndResultShareCorrelationAfterCachePublication() {
        var result = service.create(new TeamRequests.Create(
                UUID.randomUUID(), "Service Team", "SVC"))
                .toCompletableFuture().join();

        assertThat(result).isInstanceOf(OperationResult.Success.class);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.intent().correlationId()).isEqualTo(result.correlationId());
            assertThat(service.findCached(event.after().id())).contains(event.after());
        });
    }

    @Test
    void explicitPolicyDenialPreventsCommitAndEvent() {
        registries.policies().register(plugin("rules"), new MutationPolicyRegistry.PolicyContribution(
                "deny", 0, Duration.ofMillis(100),
                intent -> CompletableFuture.completedFuture(
                        PolicyDecision.deny("rules.denied"))));

        var result = service.create(new TeamRequests.Create(
                UUID.randomUUID(), "Denied Team", "NO"))
                .toCompletableFuture().join();

        assertThat(result).isInstanceOfSatisfying(
                OperationResult.Failure.class,
                failure -> assertThat(failure.code()).isEqualTo(TeamErrorCode.FORBIDDEN));
        assertThat(events).isEmpty();
    }

    @Test
    void ownerCanCreateAgainWithReleasedNameAndTagAfterDisband() {
        var owner = UUID.randomUUID();
        var created = service.create(new TeamRequests.Create(owner, "Reusable Team", "REUSE"))
                .toCompletableFuture().join().optionalValue().orElseThrow();

        assertThat(service.disband(new TeamRequests.TeamAction(owner, created.id()))
                .toCompletableFuture().join()).isInstanceOf(OperationResult.Success.class);
        assertThat(service.membershipCached(owner).status())
                .isEqualTo(me.alexisbinh.openteams.api.MembershipLookup.Status.ABSENT);

        var recreated = service.create(new TeamRequests.Create(owner, "Reusable Team", "REUSE"))
                .toCompletableFuture().join();
        assertThat(recreated).isInstanceOf(OperationResult.Success.class);
        assertThat(service.membershipCached(owner).optionalTeam()).isPresent()
                .get().extracting(team -> team.id()).isNotEqualTo(created.id());
    }

    @Test
    void addonPermissionDisappearsImmediatelyAfterRegistrationCloses() {
        var owner = UUID.randomUUID();
        var member = UUID.randomUUID();
        var registration = registries.permissions().register(plugin("chat-addon"),
                new TeamPermissionRegistry.Permission(
                        "chat.use", "chat.permission.use", java.util.Set.of("member")));
        var team = service.create(new TeamRequests.Create(owner, "Dynamic Team", "DYN"))
                .toCompletableFuture().join().optionalValue().orElseThrow();
        service.invite(new TeamRequests.TargetAction(owner, team.id(), member))
                .toCompletableFuture().join();
        service.acceptInvitation(new TeamRequests.TargetAction(member, team.id(), member))
                .toCompletableFuture().join();

        assertThat(service.hasPermissionCached(member, "chat.use")).isTrue();
        registration.close();
        assertThat(service.hasPermissionCached(member, "chat.use")).isFalse();
    }

    @Test
    void queuedMutationRechecksRuntimeAfterPolicyBeforeTransaction()
            throws Exception {
        var policyEntered = new java.util.concurrent.CountDownLatch(1);
        var releasePolicy = new CompletableFuture<PolicyDecision>();
        registries.policies().register(plugin("blocking-rules"),
                new MutationPolicyRegistry.PolicyContribution(
                        "block", 0, Duration.ofSeconds(2),
                        intent -> {
                            policyEntered.countDown();
                            return releasePolicy;
                        }));
        var actor = UUID.randomUUID();
        var resultFuture = service.create(new TeamRequests.Create(
                actor, "Queued Team", "QUEUE")).toCompletableFuture();

        assertThat(policyEntered.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        runtime.degrade();
        releasePolicy.complete(PolicyDecision.allow());

        assertThat(resultFuture.join()).isInstanceOfSatisfying(
                OperationResult.Failure.class,
                failure -> assertThat(failure.code()).isEqualTo(TeamErrorCode.READ_ONLY));
        assertThat(service.loadMembership(actor).toCompletableFuture().join().status())
                .isEqualTo(me.alexisbinh.openteams.api.MembershipLookup.Status.ABSENT);
        assertThat(events).isEmpty();
    }

    private static Plugin plugin(String name) {
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, arguments) -> method.getName().equals("getName") ? name : null);
    }
}
