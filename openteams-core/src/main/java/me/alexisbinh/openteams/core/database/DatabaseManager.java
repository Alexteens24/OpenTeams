package me.alexisbinh.openteams.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.flywaydb.core.Flyway;

public final class DatabaseManager implements AutoCloseable {
    private static final long LEASE_MILLIS = 45_000;

    private final DatabaseConfig config;
    private final Clock clock;
    private final String instanceId = UUID.randomUUID().toString();
    private final AtomicBoolean leaseHeld = new AtomicBoolean();
    private final AtomicLong fenceToken = new AtomicLong();
    private HikariDataSource dataSource;

    public DatabaseManager(DatabaseConfig config, Clock clock) {
        this.config = config;
        this.clock = clock;
    }

    public void start() throws SQLException {
        var hikari = new HikariConfig();
        hikari.setPoolName("OpenTeams-" + config.type().name().toLowerCase());
        hikari.setJdbcUrl(config.jdbcUrl());
        hikari.setUsername(config.username());
        hikari.setPassword(config.password());
        hikari.setConnectionTimeout(config.connectionTimeoutMillis());
        hikari.setMaximumPoolSize(config.type() == DatabaseConfig.Type.SQLITE ? 1 : config.poolSize());
        hikari.setMinimumIdle(config.type() == DatabaseConfig.Type.SQLITE ? 1 : Math.min(2, config.poolSize()));
        hikari.setAutoCommit(true);
        hikari.setInitializationFailTimeout(config.connectionTimeoutMillis());
        if (config.type() == DatabaseConfig.Type.SQLITE) {
            hikari.setConnectionInitSql("PRAGMA foreign_keys=ON");
        }
        dataSource = new HikariDataSource(hikari);

        if (config.type() == DatabaseConfig.Type.SQLITE) {
            try (var connection = dataSource.getConnection();
                 var statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=FULL");
                statement.execute("PRAGMA busy_timeout=5000");
            }
        }

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/common")
                .validateMigrationNaming(true)
                .load()
                .migrate();
        acquireLease();
        seedRoles();
    }

    private void seedRoles() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                assertLease(connection);
                if (roleCount(connection) == 0) {
                    seedRole(connection, "owner", "Owner", 1000, 1, true);
                    seedRole(connection, "co_owner", "Co-owner", 750, null, false);
                    seedRole(connection, "moderator", "Moderator", 500, null, false);
                    seedRole(connection, "member", "Member", 100, null, false);
                    seedPermission(connection, "owner", "*");
                    for (var role : java.util.List.of("co_owner", "moderator")) {
                        seedPermission(connection, role, "team.invite");
                        seedPermission(connection, role, "team.kick");
                        seedPermission(connection, role, "team.ban");
                        seedPermission(connection, role, "team.join-request.accept");
                    }
                    seedPermission(connection, "co_owner", "team.role.change");
                    seedPermission(connection, "co_owner", "team.rename");
                    seedPermission(connection, "co_owner", "team.settings.manage");
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private int roleCount(java.sql.Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM role_templates WHERE namespace = ?")) {
            statement.setString(1, config.namespace());
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private void seedPermission(
            java.sql.Connection connection,
            String roleKey,
            String permission
    ) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO role_permissions(namespace, role_key, permission_key)
                SELECT ?, ?, ? WHERE NOT EXISTS (
                    SELECT 1 FROM role_permissions
                    WHERE namespace = ? AND role_key = ? AND permission_key = ?
                )
                """)) {
            statement.setString(1, config.namespace());
            statement.setString(2, roleKey);
            statement.setString(3, permission);
            statement.setString(4, config.namespace());
            statement.setString(5, roleKey);
            statement.setString(6, permission);
            statement.executeUpdate();
        }
    }

    private void seedRole(
            java.sql.Connection connection,
            String key,
            String name,
            int priority,
            Integer limit,
            boolean protectedRole
    ) throws SQLException {
        var query = """
                INSERT INTO role_templates(
                    namespace, role_key, display_name, priority, member_limit, protected_role
                ) SELECT ?, ?, ?, ?, ?, ? WHERE NOT EXISTS (
                    SELECT 1 FROM role_templates WHERE namespace = ? AND role_key = ?
                )
                """;
        try (var statement = connection.prepareStatement(query)) {
            statement.setString(1, config.namespace());
            statement.setString(2, key);
            statement.setString(3, name);
            statement.setInt(4, priority);
            if (limit == null) {
                statement.setNull(5, java.sql.Types.INTEGER);
            } else {
                statement.setInt(5, limit);
            }
            statement.setInt(6, protectedRole ? 1 : 0);
            statement.setString(7, config.namespace());
            statement.setString(8, key);
            statement.executeUpdate();
        }
    }

    public void acquireLease() throws SQLException {
        for (var attempt = 0; attempt < 5; attempt++) {
            var now = clock.millis();
            try (var connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    ensureFenceCounter(connection);
                    String currentOwner = null;
                    long expiry = 0;
                    try (var select = connection.prepareStatement(
                            "SELECT instance_id, expires_at FROM core_leases WHERE namespace = ?")) {
                        select.setString(1, config.namespace());
                        try (var result = select.executeQuery()) {
                            if (result.next()) {
                                currentOwner = result.getString(1);
                                expiry = result.getLong(2);
                            }
                        }
                    }
                    if (currentOwner != null && !currentOwner.equals(instanceId) && expiry > now) {
                        throw new LeaseLostException(
                                "Database namespace is already leased by " + currentOwner);
                    }
                    var token = nextFenceToken(connection);
                    try (var delete = connection.prepareStatement(
                            "DELETE FROM core_leases WHERE namespace = ?")) {
                        delete.setString(1, config.namespace());
                        delete.executeUpdate();
                    }
                    try (var insert = connection.prepareStatement("""
                            INSERT INTO core_leases(
                                namespace,instance_id,fence_token,validation_counter,
                                heartbeat_at,expires_at
                            ) VALUES(?,?,?,0,?,?)
                            """)) {
                        insert.setString(1, config.namespace());
                        insert.setString(2, instanceId);
                        insert.setLong(3, token);
                        insert.setLong(4, now);
                        insert.setLong(5, now + LEASE_MILLIS);
                        insert.executeUpdate();
                    }
                    connection.commit();
                    fenceToken.set(token);
                    leaseHeld.set(true);
                    return;
                } catch (LeaseLostException exception) {
                    connection.rollback();
                    throw exception;
                } catch (SQLException exception) {
                    connection.rollback();
                    if (attempt == 4) {
                        throw exception;
                    }
                    Thread.onSpinWait();
                }
            }
        }
        throw new SQLException("Could not acquire a fencing token");
    }

    private void ensureFenceCounter(java.sql.Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO core_lease_fences(namespace,next_token)
                SELECT ?,1 WHERE NOT EXISTS (
                    SELECT 1 FROM core_lease_fences WHERE namespace = ?
                )
                """)) {
            statement.setString(1, config.namespace());
            statement.setString(2, config.namespace());
            statement.executeUpdate();
        }
    }

    private long nextFenceToken(java.sql.Connection connection) throws SQLException {
        long token;
        try (var select = connection.prepareStatement(
                "SELECT next_token FROM core_lease_fences WHERE namespace = ?")) {
            select.setString(1, config.namespace());
            try (var result = select.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Missing lease fence counter");
                }
                token = result.getLong(1);
            }
        }
        try (var update = connection.prepareStatement("""
                UPDATE core_lease_fences SET next_token = ?
                WHERE namespace = ? AND next_token = ?
                """)) {
            update.setLong(1, token + 1);
            update.setString(2, config.namespace());
            update.setLong(3, token);
            if (update.executeUpdate() != 1) {
                throw new SQLException("Concurrent fencing token acquisition");
            }
        }
        return token;
    }

    public boolean heartbeat() {
        var now = clock.millis();
        boolean updated;
        try (var connection = dataSource.getConnection();
             var update = connection.prepareStatement("""
                     UPDATE core_leases SET heartbeat_at = ?, expires_at = ?
                     WHERE namespace = ? AND instance_id = ? AND fence_token = ?
                     """)) {
            update.setQueryTimeout((int) Math.min(
                    Integer.MAX_VALUE,
                    Math.max(1, (config.connectionTimeoutMillis() + 999) / 1000)));
            update.setLong(1, now);
            update.setLong(2, now + LEASE_MILLIS);
            update.setString(3, config.namespace());
            update.setString(4, instanceId);
            update.setLong(5, fenceToken.get());
            updated = update.executeUpdate() == 1;
            leaseHeld.set(updated);
        } catch (SQLException exception) {
            leaseHeld.set(false);
            return false;
        }
        if (updated) {
            return true;
        }
        try {
            acquireLease();
            return true;
        } catch (SQLException exception) {
            leaseHeld.set(false);
            return false;
        }
    }

    public HikariDataSource dataSource() {
        return dataSource;
    }

    public String namespace() {
        return config.namespace();
    }

    public boolean leaseHeld() {
        return leaseHeld.get();
    }

    public long fenceToken() {
        return fenceToken.get();
    }

    /**
     * Validates and locks the current lease row inside a domain transaction.
     * A takeover must update/delete the same row and therefore cannot pass this
     * transaction until it commits or rolls back.
     */
    public void assertLease(java.sql.Connection connection) throws SQLException {
        var token = fenceToken.get();
        if (!leaseHeld.get() || token <= 0) {
            throw new LeaseLostException("This instance does not hold a lease");
        }
        try (var statement = connection.prepareStatement("""
                UPDATE core_leases SET heartbeat_at = ?
                    , validation_counter = validation_counter + 1
                WHERE namespace = ? AND instance_id = ? AND fence_token = ?
                  AND expires_at > ?
                """)) {
            var now = clock.millis();
            statement.setLong(1, now);
            statement.setString(2, config.namespace());
            statement.setString(3, instanceId);
            statement.setLong(4, token);
            statement.setLong(5, now);
            if (statement.executeUpdate() != 1) {
                leaseHeld.set(false);
                throw new LeaseLostException("Lease fencing token is no longer valid");
            }
        }
    }

    public DatabaseReport doctor() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            return new DatabaseReport(
                    count(connection, """
                            SELECT COUNT(*) FROM teams t
                            WHERE t.namespace = ? AND t.state = 'ACTIVE'
                              AND NOT EXISTS (
                                SELECT 1 FROM team_members m
                                WHERE m.namespace = t.namespace AND m.team_id = t.id
                                  AND m.player_id = t.owner_id
                              )
                            """),
                    count(connection, """
                            SELECT COUNT(*) FROM teams t
                            JOIN team_members m
                              ON m.namespace = t.namespace AND m.team_id = t.id
                             AND m.player_id = t.owner_id
                            WHERE t.namespace = ? AND t.state = 'ACTIVE'
                              AND m.role_key <> 'owner'
                            """),
                    count(connection, """
                            SELECT COUNT(*) FROM team_members m
                            WHERE m.namespace = ? AND NOT EXISTS (
                                SELECT 1 FROM teams t
                                WHERE t.namespace = m.namespace AND t.id = m.team_id
                                  AND t.state = 'ACTIVE'
                            )
                            """),
                    countExpired(connection, "team_invitations"),
                    countExpired(connection, "team_join_requests"),
                    countExpired(connection, "team_bans"),
                    count(connection,
                            "SELECT COUNT(*) FROM audit_entries WHERE namespace = ?")
            );
        }
    }

    public CleanupReport cleanupExpired(long auditRetentionMillis) throws SQLException {
        var cutoff = clock.millis() - Math.max(0, auditRetentionMillis);
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                assertLease(connection);
                var invitations = deleteExpired(connection, "team_invitations");
                var requests = deleteExpired(connection, "team_join_requests");
                var bans = deleteExpired(connection, "team_bans");
                int audit;
                try (var statement = connection.prepareStatement(
                        "DELETE FROM audit_entries WHERE namespace = ? AND created_at < ?")) {
                    statement.setString(1, config.namespace());
                    statement.setLong(2, cutoff);
                    audit = statement.executeUpdate();
                }
                connection.commit();
                return new CleanupReport(invitations, requests, bans, audit);
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private long count(java.sql.Connection connection, String sql) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, config.namespace());
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private long countExpired(java.sql.Connection connection, String table)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + table
                        + " WHERE namespace = ? AND expires_at IS NOT NULL AND expires_at < ?")) {
            statement.setString(1, config.namespace());
            statement.setLong(2, clock.millis());
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private int deleteExpired(java.sql.Connection connection, String table)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "DELETE FROM " + table
                        + " WHERE namespace = ? AND expires_at IS NOT NULL AND expires_at < ?")) {
            statement.setString(1, config.namespace());
            statement.setLong(2, clock.millis());
            return statement.executeUpdate();
        }
    }

    public record DatabaseReport(
            long activeTeamsWithoutOwnerMember,
            long ownersWithWrongRole,
            long danglingMembers,
            long expiredInvitations,
            long expiredJoinRequests,
            long expiredBans,
            long auditRows
    ) {
        public boolean healthy() {
            return activeTeamsWithoutOwnerMember == 0
                    && ownersWithWrongRole == 0
                    && danglingMembers == 0;
        }
    }

    public record CleanupReport(
            int invitations,
            int joinRequests,
            int bans,
            int auditRows
    ) {
    }

    @Override
    public void close() {
        if (dataSource == null) {
            return;
        }
        if (leaseHeld.get()) {
            try (var connection = dataSource.getConnection();
                 var delete = connection.prepareStatement(
                        """
                        DELETE FROM core_leases
                        WHERE namespace = ? AND instance_id = ? AND fence_token = ?
                        """)) {
                delete.setString(1, config.namespace());
                delete.setString(2, instanceId);
                delete.setLong(3, fenceToken.get());
                delete.executeUpdate();
            } catch (SQLException ignored) {
                // Lease expires automatically after an unclean shutdown.
            }
        }
        dataSource.close();
    }
}
