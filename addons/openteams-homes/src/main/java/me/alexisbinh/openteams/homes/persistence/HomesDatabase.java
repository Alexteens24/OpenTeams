package me.alexisbinh.openteams.homes.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import javax.sql.DataSource;
import me.alexisbinh.openteams.homes.config.HomesConfig;
import org.flywaydb.core.Flyway;

public final class HomesDatabase implements AutoCloseable {
    private final HomesConfig.Storage config;
    private HikariDataSource dataSource;

    public HomesDatabase(HomesConfig.Storage config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public void start() throws IOException {
        if (config.type() == HomesConfig.StorageType.SQLITE) {
            var parent = config.sqliteFile().toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
        }
        var hikari = new HikariConfig();
        hikari.setPoolName("OpenTeams-Homes");
        hikari.setJdbcUrl(jdbcUrl());
        hikari.setUsername(config.type() == HomesConfig.StorageType.SQLITE ? "" : config.username());
        hikari.setPassword(config.type() == HomesConfig.StorageType.SQLITE ? "" : config.password());
        hikari.setMaximumPoolSize(config.type() == HomesConfig.StorageType.SQLITE
                ? 1 : config.poolSize());
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(config.connectionTimeout().toMillis());
        hikari.setAutoCommit(true);
        dataSource = new HikariDataSource(hikari);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/common")
                .table("oth_flyway_history")
                .load()
                .migrate();
    }

    public DataSource dataSource() {
        if (dataSource == null) throw new IllegalStateException("Database has not started");
        return dataSource;
    }

    public boolean healthy() {
        try (var connection = dataSource().getConnection();
             var statement = connection.prepareStatement("SELECT 1")) {
            return statement.executeQuery().next();
        } catch (Exception exception) {
            return false;
        }
    }

    private String jdbcUrl() {
        return switch (config.type()) {
            case SQLITE -> "jdbc:sqlite:" + config.sqliteFile().toAbsolutePath();
            case MYSQL -> "jdbc:mysql://%s:%d/%s?sslMode=PREFERRED&tcpKeepAlive=true"
                    .formatted(config.host(), config.port(), config.database());
            case MARIADB -> "jdbc:mariadb://%s:%d/%s?tcpKeepAlive=true"
                    .formatted(config.host(), config.port(), config.database());
        };
    }

    @Override
    public void close() {
        if (dataSource != null) dataSource.close();
    }
}
