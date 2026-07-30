package me.alexisbinh.openteams.core.database;

import java.nio.file.Path;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

public record DatabaseConfig(
        Type type,
        String namespace,
        String jdbcUrl,
        String username,
        String password,
        int poolSize,
        long connectionTimeoutMillis
) {
    public enum Type {
        SQLITE,
        MYSQL,
        MARIADB
    }

    public static DatabaseConfig from(ConfigurationSection section, Path dataDirectory) {
        var type = Type.valueOf(section.getString("type", "sqlite").toUpperCase(Locale.ROOT));
        var namespace = section.getString("namespace", "default");
        var host = section.getString("host", "localhost");
        var port = section.getInt("port", 3306);
        var database = section.getString("database", "openteams");
        var jdbcUrl = switch (type) {
            case SQLITE -> "jdbc:sqlite:" + dataDirectory.resolve(
                    section.getString("sqlite-file", "openteams.db")).toAbsolutePath();
            case MYSQL -> "jdbc:mysql://%s:%d/%s?useSSL=true&tcpKeepAlive=true"
                    .formatted(host, port, database);
            case MARIADB -> "jdbc:mariadb://%s:%d/%s?tcpKeepAlive=true"
                    .formatted(host, port, database);
        };
        return new DatabaseConfig(
                type,
                namespace,
                jdbcUrl,
                section.getString("username", ""),
                section.getString("password", ""),
                Math.max(1, section.getInt("pool-size", 8)),
                Math.max(250, section.getLong("connection-timeout-ms", 3000))
        );
    }
}
