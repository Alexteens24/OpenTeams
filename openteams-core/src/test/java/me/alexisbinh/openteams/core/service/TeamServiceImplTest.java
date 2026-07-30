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
        var runtime = new RuntimeController();
        runtime.writableAfterStartup();
        service = new TeamServiceImpl(
                new JdbcTeamStore(database.dataSource(), config.namespace(),
                        Clock.systemUTC(), 20, 60_000),
                new TeamCache(), 1, runtime, registries, events::add);
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

    private static Plugin plugin(String name) {
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, arguments) -> method.getName().equals("getName") ? name : null);
    }
}
