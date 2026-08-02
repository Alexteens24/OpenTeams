package me.alexisbinh.openteams.homes.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.Executors;
import me.alexisbinh.openteams.api.TeamId;
import me.alexisbinh.openteams.homes.config.HomesConfig;
import me.alexisbinh.openteams.homes.domain.StoredLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcPointRepositoryTest {
    @TempDir Path temporary;
    private HomesDatabase database;
    private JdbcPointRepository repository;
    private final TeamId team = TeamId.random();
    private final UUID actor = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        database = new HomesDatabase(storage(temporary.resolve("homes.db"), "test"));
        database.start();
        repository = new JdbcPointRepository(database.dataSource(), "test", Clock.systemUTC());
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void createsUpdatesAndDeletesHomeWithOptimisticLocking() throws Exception {
        var created = repository.setHome(team, location(1), actor, OptionalLong.empty());
        assertThat(repository.findHome(team)).contains(created);

        var updated = repository.setHome(team, location(2), actor,
                OptionalLong.of(created.version()));
        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.creatorId()).isEqualTo(actor);
        assertThat(updated.location().x()).isEqualTo(2);

        assertThatThrownBy(() -> repository.setHome(team, location(3), actor,
                OptionalLong.of(created.version())))
                .isInstanceOf(PointRepository.Conflict.class);
        repository.delete(updated.id(), team, updated.version());
        assertThat(repository.findHome(team)).isEmpty();
    }

    @Test
    void enforcesCaseInsensitiveNamesLimitSearchAndTeamIsolation() throws Exception {
        var alpha = repository.createWarp(team, "Alpha", "alpha", location(1), actor, 2);
        repository.createWarp(team, "Beta", "beta", location(2), actor, 2);
        assertThatThrownBy(() -> repository.createWarp(team, "ALPHA", "alpha",
                location(3), actor, 2)).isInstanceOf(PointRepository.DuplicateName.class);
        assertThatThrownBy(() -> repository.createWarp(team, "Gamma", "gamma",
                location(3), actor, 2)).isInstanceOf(PointRepository.LimitReached.class);

        var page = repository.searchWarps(team, "alp", 0, 8);
        assertThat(page.total()).isOne();
        assertThat(page.entries()).extracting(point -> point.displayName()).containsExactly("Alpha");
        assertThat(repository.findWarp(TeamId.random(), "alpha")).isEmpty();

        var renamed = repository.renameWarp(alpha.id(), team, alpha.version(), "Spawn", "spawn");
        assertThat(repository.findWarp(team, "spawn")).contains(renamed);
        assertThat(repository.findWarp(team, "alpha")).isEmpty();
    }

    @Test
    void isolatesNamespacesAndSurvivesRestart() throws Exception {
        var point = repository.createWarp(team, "Alpha", "alpha", location(1), actor, 20);
        var other = new JdbcPointRepository(database.dataSource(), "other", Clock.systemUTC());
        assertThat(other.findWarp(team, "alpha")).isEmpty();

        database.close();
        database = new HomesDatabase(storage(temporary.resolve("homes.db"), "test"));
        database.start();
        repository = new JdbcPointRepository(database.dataSource(), "test", Clock.systemUTC());
        assertThat(repository.findWarp(team, "alpha")).contains(point);
    }

    @Test
    void concurrentCreatesCannotExceedLimit() throws Exception {
        try (var executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory())) {
            var first = executor.submit(() -> create("One"));
            var second = executor.submit(() -> create("Two"));
            var successes = 0;
            for (var future : java.util.List.of(first, second)) {
                try { future.get(); successes++; }
                catch (java.util.concurrent.ExecutionException exception) {
                    assertThat(exception.getCause()).isInstanceOf(PointRepository.LimitReached.class);
                }
            }
            assertThat(successes).isOne();
            assertThat(repository.searchWarps(team, "", 0, 8).total()).isOne();
        }
    }

    private Object create(String name) throws Exception {
        return repository.createWarp(team, name, name.toLowerCase(java.util.Locale.ROOT),
                location(1), actor, 1);
    }

    private static StoredLocation location(double x) {
        return new StoredLocation("local", UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "world", x, 64, 0, 0, 0);
    }

    private static HomesConfig.Storage storage(Path file, String namespace) {
        return new HomesConfig.Storage(HomesConfig.StorageType.SQLITE, namespace, file,
                "localhost", 3306, "unused", "", "", 1, Duration.ofSeconds(3));
    }
}
