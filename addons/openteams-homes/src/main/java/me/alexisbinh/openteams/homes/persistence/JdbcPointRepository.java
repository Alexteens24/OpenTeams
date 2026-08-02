package me.alexisbinh.openteams.homes.persistence;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import javax.sql.DataSource;
import me.alexisbinh.openteams.api.TeamId;
import me.alexisbinh.openteams.homes.domain.PointPage;
import me.alexisbinh.openteams.homes.domain.PointType;
import me.alexisbinh.openteams.homes.domain.StoredLocation;
import me.alexisbinh.openteams.homes.domain.TeleportPoint;

public final class JdbcPointRepository implements PointRepository {
    private static final String COLUMNS = "id,team_id,point_type,display_name,normalized_name,"
            + "server_id,world_id,world_name,x,y,z,yaw,pitch,creator_id,created_at,updated_at,version";

    private final DataSource dataSource;
    private final String namespace;
    private final Clock clock;

    public JdbcPointRepository(DataSource dataSource, String namespace, Clock clock) {
        this.dataSource = dataSource;
        this.namespace = namespace;
        this.clock = clock;
    }

    @Override
    public Optional<TeleportPoint> findHome(TeamId teamId) throws SQLException {
        return find(teamId, PointType.HOME, TeleportPoint.HOME_KEY);
    }

    @Override
    public Optional<TeleportPoint> findWarp(TeamId teamId, String normalizedName)
            throws SQLException {
        return find(teamId, PointType.WARP, normalizedName);
    }

    private Optional<TeleportPoint> find(TeamId teamId, PointType type, String name)
            throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT " + COLUMNS
                     + " FROM oth_teleport_points WHERE namespace=? AND team_id=?"
                     + " AND point_type=? AND normalized_name=?")) {
            statement.setString(1, namespace);
            statement.setString(2, teamId.toString());
            statement.setString(3, type.name());
            statement.setString(4, name);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<TeleportPoint> findById(UUID id) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT " + COLUMNS
                     + " FROM oth_teleport_points WHERE namespace=? AND id=?")) {
            statement.setString(1, namespace);
            statement.setString(2, id.toString());
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        }
    }

    @Override
    public PointPage searchWarps(TeamId teamId, String query, int page, int pageSize)
            throws SQLException {
        var safePage = Math.max(0, page);
        var pattern = "%" + escapeLike(query) + "%";
        try (var connection = dataSource.getConnection()) {
            long total;
            try (var count = connection.prepareStatement("SELECT COUNT(*)"
                    + " FROM oth_teleport_points WHERE namespace=? AND team_id=?"
                    + " AND point_type='WARP' AND normalized_name LIKE ? ESCAPE '!'")) {
                count.setString(1, namespace);
                count.setString(2, teamId.toString());
                count.setString(3, pattern);
                try (var result = count.executeQuery()) { result.next(); total = result.getLong(1); }
            }
            var entries = new ArrayList<TeleportPoint>();
            try (var statement = connection.prepareStatement("SELECT " + COLUMNS
                    + " FROM oth_teleport_points WHERE namespace=? AND team_id=?"
                    + " AND point_type='WARP' AND normalized_name LIKE ? ESCAPE '!'"
                    + " ORDER BY normalized_name ASC LIMIT ? OFFSET ?")) {
                statement.setString(1, namespace);
                statement.setString(2, teamId.toString());
                statement.setString(3, pattern);
                statement.setInt(4, pageSize);
                statement.setInt(5, safePage * pageSize);
                try (var result = statement.executeQuery()) {
                    while (result.next()) entries.add(read(result));
                }
            }
            return new PointPage(entries, safePage, pageSize, total);
        }
    }

    @Override
    public TeleportPoint setHome(TeamId teamId, StoredLocation location, UUID actor,
                                 OptionalLong expectedVersion) throws SQLException {
        return transaction(connection -> {
            var existing = find(connection, teamId, PointType.HOME, TeleportPoint.HOME_KEY);
            if (existing.isEmpty()) {
                if (expectedVersion.isPresent()) throw new Conflict();
                var now = Instant.ofEpochMilli(clock.millis());
                var point = new TeleportPoint(UUID.randomUUID(), teamId, PointType.HOME,
                        "Team Home", TeleportPoint.HOME_KEY, location, actor, now, now, 0);
                try {
                    insert(connection, point);
                } catch (SQLException exception) {
                    if (isConstraint(exception)) throw new Conflict();
                    throw exception;
                }
                return point;
            }
            var point = existing.get();
            if (expectedVersion.isPresent() && expectedVersion.getAsLong() != point.version()) {
                throw new Conflict();
            }
            return updateLocation(connection, point, location);
        });
    }

    @Override
    public TeleportPoint createWarp(TeamId teamId, String displayName, String normalizedName,
                                    StoredLocation location, UUID actor, int limit)
            throws SQLException {
        return transaction(connection -> {
            ensureScope(connection, teamId);
            if (find(connection, teamId, PointType.WARP, normalizedName).isPresent()) {
                throw new DuplicateName();
            }
            var count = lockAndCount(connection, teamId);
            if (count >= limit) throw new LimitReached();
            var now = Instant.ofEpochMilli(clock.millis());
            var point = new TeleportPoint(UUID.randomUUID(), teamId, PointType.WARP,
                    displayName, normalizedName, location, actor, now, now, 0);
            try {
                insert(connection, point);
            } catch (SQLException exception) {
                if (isConstraint(exception)) throw new DuplicateName();
                throw exception;
            }
            try (var update = connection.prepareStatement("UPDATE oth_team_scopes"
                    + " SET warp_count=warp_count+1 WHERE namespace=? AND team_id=?")) {
                update.setString(1, namespace);
                update.setString(2, teamId.toString());
                update.executeUpdate();
            }
            return point;
        });
    }

    @Override
    public TeleportPoint updateLocation(UUID id, TeamId teamId, long expectedVersion,
                                        StoredLocation location) throws SQLException {
        return transaction(connection -> {
            var point = require(connection, id, teamId);
            if (point.version() != expectedVersion) throw new Conflict();
            return updateLocation(connection, point, location);
        });
    }

    private TeleportPoint updateLocation(Connection connection, TeleportPoint point,
                                         StoredLocation location) throws SQLException {
        var now = Instant.ofEpochMilli(clock.millis());
        try (var statement = connection.prepareStatement("UPDATE oth_teleport_points SET "
                + "server_id=?,world_id=?,world_name=?,x=?,y=?,z=?,yaw=?,pitch=?,"
                + "updated_at=?,version=version+1 WHERE namespace=? AND id=? AND version=?")) {
            bindLocation(statement, location, 1);
            statement.setLong(9, now.toEpochMilli());
            statement.setString(10, namespace);
            statement.setString(11, point.id().toString());
            statement.setLong(12, point.version());
            if (statement.executeUpdate() != 1) throw new Conflict();
        }
        return new TeleportPoint(point.id(), point.teamId(), point.type(), point.displayName(),
                point.normalizedName(), location, point.creatorId(), point.createdAt(), now,
                point.version() + 1);
    }

    @Override
    public TeleportPoint renameWarp(UUID id, TeamId teamId, long expectedVersion,
                                    String displayName, String normalizedName) throws SQLException {
        return transaction(connection -> {
            var point = require(connection, id, teamId);
            if (point.type() != PointType.WARP || point.version() != expectedVersion) {
                throw new Conflict();
            }
            var now = Instant.ofEpochMilli(clock.millis());
            try (var statement = connection.prepareStatement("UPDATE oth_teleport_points SET "
                    + "display_name=?,normalized_name=?,updated_at=?,version=version+1"
                    + " WHERE namespace=? AND id=? AND version=?")) {
                statement.setString(1, displayName);
                statement.setString(2, normalizedName);
                statement.setLong(3, now.toEpochMilli());
                statement.setString(4, namespace);
                statement.setString(5, id.toString());
                statement.setLong(6, expectedVersion);
                try {
                    if (statement.executeUpdate() != 1) throw new Conflict();
                } catch (SQLException exception) {
                    if (isConstraint(exception)) throw new DuplicateName();
                    throw exception;
                }
            }
            return new TeleportPoint(point.id(), point.teamId(), point.type(), displayName,
                    normalizedName, point.location(), point.creatorId(), point.createdAt(), now,
                    point.version() + 1);
        });
    }

    @Override
    public void delete(UUID id, TeamId teamId, long expectedVersion) throws SQLException {
        transaction(connection -> {
            var point = require(connection, id, teamId);
            if (point.version() != expectedVersion) throw new Conflict();
            try (var statement = connection.prepareStatement("DELETE FROM oth_teleport_points"
                    + " WHERE namespace=? AND id=? AND team_id=? AND version=?")) {
                statement.setString(1, namespace);
                statement.setString(2, id.toString());
                statement.setString(3, teamId.toString());
                statement.setLong(4, expectedVersion);
                if (statement.executeUpdate() != 1) throw new Conflict();
            }
            if (point.type() == PointType.WARP) {
                try (var update = connection.prepareStatement("UPDATE oth_team_scopes SET"
                        + " warp_count=CASE WHEN warp_count>0 THEN warp_count-1 ELSE 0 END"
                        + " WHERE namespace=? AND team_id=?")) {
                    update.setString(1, namespace);
                    update.setString(2, teamId.toString());
                    update.executeUpdate();
                }
            }
            return null;
        });
    }

    @Override
    public void deleteTeam(TeamId teamId) throws SQLException {
        transaction(connection -> {
            try (var points = connection.prepareStatement("DELETE FROM oth_teleport_points"
                    + " WHERE namespace=? AND team_id=?");
                 var scope = connection.prepareStatement("DELETE FROM oth_team_scopes"
                         + " WHERE namespace=? AND team_id=?")) {
                points.setString(1, namespace); points.setString(2, teamId.toString());
                points.executeUpdate();
                scope.setString(1, namespace); scope.setString(2, teamId.toString());
                scope.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<TeamId> teamIds(int limit) throws SQLException {
        var ids = new ArrayList<TeamId>();
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT DISTINCT team_id"
                     + " FROM oth_teleport_points WHERE namespace=? LIMIT ?")) {
            statement.setString(1, namespace);
            statement.setInt(2, limit);
            try (var result = statement.executeQuery()) {
                while (result.next()) ids.add(TeamId.parse(result.getString(1)));
            }
        }
        return List.copyOf(ids);
    }

    private Optional<TeleportPoint> find(Connection connection, TeamId teamId,
                                         PointType type, String name) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT " + COLUMNS
                + " FROM oth_teleport_points WHERE namespace=? AND team_id=?"
                + " AND point_type=? AND normalized_name=?")) {
            statement.setString(1, namespace); statement.setString(2, teamId.toString());
            statement.setString(3, type.name()); statement.setString(4, name);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        }
    }

    private TeleportPoint require(Connection connection, UUID id, TeamId teamId)
            throws SQLException {
        try (var statement = connection.prepareStatement("SELECT " + COLUMNS
                + " FROM oth_teleport_points WHERE namespace=? AND id=? AND team_id=?")) {
            statement.setString(1, namespace); statement.setString(2, id.toString());
            statement.setString(3, teamId.toString());
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new Conflict();
                return read(result);
            }
        }
    }

    private void ensureScope(Connection connection, TeamId teamId) throws SQLException {
        try (var insert = connection.prepareStatement("INSERT INTO oth_team_scopes"
                + " (namespace,team_id,warp_count) VALUES (?,?,0)")) {
            insert.setString(1, namespace); insert.setString(2, teamId.toString());
            insert.executeUpdate();
        } catch (SQLException exception) {
            if (!isConstraint(exception)) throw exception;
        }
    }

    private int lockAndCount(Connection connection, TeamId teamId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT warp_count FROM oth_team_scopes"
                + " WHERE namespace=? AND team_id=? FOR UPDATE")) {
            statement.setString(1, namespace); statement.setString(2, teamId.toString());
            try (var result = statement.executeQuery()) { result.next(); return result.getInt(1); }
        } catch (SQLException unsupported) {
            try (var statement = connection.prepareStatement("SELECT warp_count"
                    + " FROM oth_team_scopes WHERE namespace=? AND team_id=?")) {
                statement.setString(1, namespace); statement.setString(2, teamId.toString());
                try (var result = statement.executeQuery()) { result.next(); return result.getInt(1); }
            }
        }
    }

    private void insert(Connection connection, TeleportPoint point) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO oth_teleport_points"
                + " (namespace," + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, namespace);
            statement.setString(2, point.id().toString());
            statement.setString(3, point.teamId().toString());
            statement.setString(4, point.type().name());
            statement.setString(5, point.displayName());
            statement.setString(6, point.normalizedName());
            bindLocation(statement, point.location(), 7);
            statement.setString(15, point.creatorId().toString());
            statement.setLong(16, point.createdAt().toEpochMilli());
            statement.setLong(17, point.updatedAt().toEpochMilli());
            statement.setLong(18, point.version());
            statement.executeUpdate();
        }
    }

    private static void bindLocation(java.sql.PreparedStatement statement,
                                     StoredLocation location, int start) throws SQLException {
        statement.setString(start, location.serverId());
        statement.setString(start + 1, location.worldId().toString());
        statement.setString(start + 2, location.worldName());
        statement.setDouble(start + 3, location.x());
        statement.setDouble(start + 4, location.y());
        statement.setDouble(start + 5, location.z());
        statement.setFloat(start + 6, location.yaw());
        statement.setFloat(start + 7, location.pitch());
    }

    private TeleportPoint read(ResultSet result) throws SQLException {
        return new TeleportPoint(
                UUID.fromString(result.getString("id")), TeamId.parse(result.getString("team_id")),
                PointType.valueOf(result.getString("point_type")), result.getString("display_name"),
                result.getString("normalized_name"), new StoredLocation(
                        result.getString("server_id"), UUID.fromString(result.getString("world_id")),
                        result.getString("world_name"), result.getDouble("x"), result.getDouble("y"),
                        result.getDouble("z"), result.getFloat("yaw"), result.getFloat("pitch")),
                UUID.fromString(result.getString("creator_id")),
                Instant.ofEpochMilli(result.getLong("created_at")),
                Instant.ofEpochMilli(result.getLong("updated_at")), result.getLong("version"));
    }

    private <T> T transaction(SqlWork<T> work) throws SQLException {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                var value = work.run(connection);
                connection.commit();
                return value;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static boolean isConstraint(SQLException exception) {
        return exception instanceof SQLIntegrityConstraintViolationException
                || exception.getSQLState() != null && exception.getSQLState().startsWith("23")
                || exception.getMessage() != null
                && exception.getMessage().toLowerCase(java.util.Locale.ROOT).contains("unique");
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    @FunctionalInterface
    private interface SqlWork<T> { T run(Connection connection) throws SQLException; }
}
