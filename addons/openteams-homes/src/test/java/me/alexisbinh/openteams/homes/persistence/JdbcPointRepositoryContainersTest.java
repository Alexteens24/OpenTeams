package me.alexisbinh.openteams.homes.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import me.alexisbinh.openteams.api.TeamId;
import me.alexisbinh.openteams.homes.config.HomesConfig;
import me.alexisbinh.openteams.homes.domain.StoredLocation;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcPointRepositoryContainersTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");
    @Container
    private static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:11.8");

    @Test
    void mysqlRepositoryContract() throws Exception {
        verify(HomesConfig.StorageType.MYSQL, MYSQL.getHost(), MYSQL.getFirstMappedPort(),
                MYSQL.getDatabaseName(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    @Test
    void mariaDbRepositoryContract() throws Exception {
        verify(HomesConfig.StorageType.MARIADB, MARIADB.getHost(), MARIADB.getFirstMappedPort(),
                MARIADB.getDatabaseName(), MARIADB.getUsername(), MARIADB.getPassword());
    }

    private static void verify(HomesConfig.StorageType type, String host, int port,
                               String databaseName, String username, String password)
            throws Exception {
        var config = new HomesConfig.Storage(type, "container", Path.of("unused"), host, port,
                databaseName, username, password, 4, Duration.ofSeconds(10));
        try (var database = new HomesDatabase(config)) {
            database.start();
            var repository = new JdbcPointRepository(database.dataSource(), "container",
                    Clock.systemUTC());
            var team = TeamId.random();
            var point = repository.createWarp(team, "Spawn", "spawn",
                    new StoredLocation("local", UUID.randomUUID(), "world", 1, 64, 2, 0, 0),
                    UUID.randomUUID(), 20);
            assertThat(repository.findWarp(team, "spawn")).contains(point);
        }
    }
}
