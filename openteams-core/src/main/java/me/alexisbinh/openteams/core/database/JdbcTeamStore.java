package me.alexisbinh.openteams.core.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import me.alexisbinh.openteams.api.TeamErrorCode;
import me.alexisbinh.openteams.api.TeamId;
import me.alexisbinh.openteams.api.TeamMemberSnapshot;
import me.alexisbinh.openteams.api.TeamSnapshot;
import me.alexisbinh.openteams.api.TeamState;
import me.alexisbinh.openteams.api.TeamVisibility;
import me.alexisbinh.openteams.core.domain.TeamNames;

public final class JdbcTeamStore {
    private final DataSource dataSource;
    private final String namespace;
    private final Clock clock;
    private final int defaultMemberLimit;
    private final long invitationLifetimeMillis;
    private final ThreadLocal<String> activeCorrelationId = new ThreadLocal<>();
    private final DatabaseManager database;

    public JdbcTeamStore(
            DataSource dataSource,
            String namespace,
            Clock clock,
            int defaultMemberLimit,
            long invitationLifetimeMillis,
            DatabaseManager database
    ) {
        this.dataSource = dataSource;
        this.namespace = namespace;
        this.clock = clock;
        this.defaultMemberLimit = defaultMemberLimit;
        this.invitationLifetimeMillis = invitationLifetimeMillis;
        this.database = database;
    }

    public Optional<TeamSnapshot> find(TeamId id) throws SQLException {
        try (var connection = dataSource.getConnection()) {
            return load(connection, id);
        }
    }

    public Optional<TeamSnapshot> findByPlayer(UUID playerId) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT team_id FROM team_members WHERE namespace = ? AND player_id = ?")) {
            statement.setString(1, namespace);
            statement.setString(2, playerId.toString());
            try (var result = statement.executeQuery()) {
                return result.next() ? load(connection, TeamId.parse(result.getString(1))) : Optional.empty();
            }
        }
    }

    public Map<UUID, TeamSnapshot> findByPlayers(Collection<UUID> playerIds)
            throws SQLException {
        if (playerIds.isEmpty()) {
            return Map.of();
        }
        try (var connection = dataSource.getConnection()) {
            var teamByPlayer = new HashMap<UUID, TeamId>();
            var ids = new ArrayList<>(playerIds);
            for (var offset = 0; offset < ids.size(); offset += 500) {
                var chunk = ids.subList(offset, Math.min(offset + 500, ids.size()));
                var placeholders = String.join(",", java.util.Collections.nCopies(
                        chunk.size(), "?"));
                try (var statement = connection.prepareStatement(
                        "SELECT player_id,team_id FROM team_members"
                                + " WHERE namespace = ? AND player_id IN (" + placeholders + ")")) {
                    statement.setString(1, namespace);
                    for (var index = 0; index < chunk.size(); index++) {
                        statement.setString(index + 2, chunk.get(index).toString());
                    }
                    try (var result = statement.executeQuery()) {
                        while (result.next()) {
                            teamByPlayer.put(
                                    UUID.fromString(result.getString(1)),
                                    TeamId.parse(result.getString(2)));
                        }
                    }
                }
            }
            var snapshots = loadMany(
                    connection, new java.util.HashSet<>(teamByPlayer.values()));
            var result = new LinkedHashMap<UUID, TeamSnapshot>();
            teamByPlayer.forEach((playerId, teamId) -> {
                var snapshot = snapshots.get(teamId);
                if (snapshot != null) {
                    result.put(playerId, snapshot);
                }
            });
            return Map.copyOf(result);
        }
    }

    public <T> T correlated(UUID correlationId, CorrelatedWork<T> work)
            throws SQLException, DomainFailure {
        activeCorrelationId.set(correlationId.toString());
        try {
            return work.run();
        } finally {
            activeCorrelationId.remove();
        }
    }

    public TeamSnapshot create(UUID actorId, String name, String tag) throws SQLException, DomainFailure {
        return transaction(connection -> {
            if (membershipExists(connection, actorId)) {
                throw failure(TeamErrorCode.ALREADY_IN_TEAM, "Player already belongs to a team");
            }
            var now = clock.millis();
            var id = TeamId.random();
            try (var statement = connection.prepareStatement("""
                    INSERT INTO teams(
                        namespace,id,display_name,normalized_name,tag,normalized_tag,owner_id,
                        state,visibility,member_limit,version,created_at,updated_at,deleted_at
                    ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,NULL)
                    """)) {
                statement.setString(1, namespace);
                statement.setString(2, id.toString());
                statement.setString(3, name.strip());
                statement.setString(4, TeamNames.normalize(name));
                nullable(statement, 5, tag);
                nullable(statement, 6, tag == null ? null : TeamNames.normalize(tag));
                statement.setString(7, actorId.toString());
                statement.setString(8, TeamState.ACTIVE.name());
                statement.setString(9, TeamVisibility.PRIVATE.name());
                statement.setInt(10, defaultMemberLimit);
                statement.setLong(11, 0);
                statement.setLong(12, now);
                statement.setLong(13, now);
                statement.executeUpdate();
            }
            insertClaim(connection, "team_name_claims", "normalized_name",
                    TeamNames.normalize(name), id);
            if (tag != null && !tag.isBlank()) {
                insertClaim(connection, "team_tag_claims", "normalized_tag",
                        TeamNames.normalize(tag), id);
            }
            insertMember(connection, id, actorId, "owner", now);
            audit(connection, id, actorId, "TEAM_CREATED", "{\"name\":\"" + json(name) + "\"}");
            return require(connection, id);
        });
    }

    public TeamSnapshot invite(TeamId id, UUID actorId, UUID targetId)
            throws SQLException, DomainFailure {
        return transaction(connection -> {
            var team = requireActive(connection, id);
            requireManagePermission(connection, team, actorId, null);
            if (membershipExists(connection, targetId)) {
                throw failure(TeamErrorCode.ALREADY_IN_TEAM, "Target already belongs to a team");
            }
            if (isBanned(connection, id, targetId)) {
                throw failure(TeamErrorCode.FORBIDDEN, "Target is banned from this team");
            }
            try (var delete = connection.prepareStatement("""
                    DELETE FROM team_invitations
                    WHERE namespace = ? AND team_id = ? AND target_id = ?
                    """)) {
                bindTeamTarget(delete, id, targetId);
                delete.executeUpdate();
            }
            var now = clock.millis();
            try (var insert = connection.prepareStatement("""
                    INSERT INTO team_invitations(
                        namespace,team_id,target_id,inviter_id,created_at,expires_at
                    ) VALUES(?,?,?,?,?,?)
                    """)) {
                insert.setString(1, namespace);
                insert.setString(2, id.toString());
                insert.setString(3, targetId.toString());
                insert.setString(4, actorId.toString());
                insert.setLong(5, now);
                insert.setLong(6, now + invitationLifetimeMillis);
                insert.executeUpdate();
            }
            bump(connection, team);
            audit(connection, id, actorId, "MEMBER_INVITED",
                    "{\"target\":\"" + targetId + "\"}");
            return require(connection, id);
        });
    }

    public TeamSnapshot acceptInvitation(TeamId id, UUID actorId)
            throws SQLException, DomainFailure {
        return transaction(connection -> {
            var team = requireActive(connection, id);
            if (membershipExists(connection, actorId)) {
                throw failure(TeamErrorCode.ALREADY_IN_TEAM, "Player already belongs to a team");
            }
            if (isBanned(connection, id, actorId)) {
                throw failure(TeamErrorCode.FORBIDDEN, "Player is banned from this team");
            }
            long expiry;
            try (var select = connection.prepareStatement("""
                    SELECT expires_at FROM team_invitations
                    WHERE namespace = ? AND team_id = ? AND target_id = ?
                    """)) {
                bindTeamTarget(select, id, actorId);
                try (var result = select.executeQuery()) {
                    if (!result.next()) {
                        throw failure(TeamErrorCode.INVITATION_NOT_FOUND, "Invitation not found");
                    }
                    expiry = result.getLong(1);
                }
            }
            if (expiry < clock.millis()) {
                throw failure(TeamErrorCode.INVITATION_EXPIRED, "Invitation expired");
            }
            if (team.members().size() >= team.memberLimit()) {
                throw failure(TeamErrorCode.LIMIT_REACHED, "Team member limit reached");
            }
            insertMember(connection, id, actorId, "member", clock.millis());
            try (var delete = connection.prepareStatement(
                    "DELETE FROM team_invitations WHERE namespace = ? AND target_id = ?")) {
                delete.setString(1, namespace);
                delete.setString(2, actorId.toString());
                delete.executeUpdate();
            }
            bump(connection, team);
            audit(connection, id, actorId, "MEMBER_JOINED", "{}");
            return require(connection, id);
        });
    }

    public TeamSnapshot leave(TeamId id, UUID actorId) throws SQLException, DomainFailure {
        return transaction(connection -> {
            var team = requireActive(connection, id);
            requireMember(connection, id, actorId);
            if (team.ownerId().equals(actorId)) {
                throw failure(TeamErrorCode.FORBIDDEN, "Owner must transfer ownership first");
            }
            deleteMember(connection, id, actorId);
            bump(connection, team);
            audit(connection, id, actorId, "MEMBER_LEFT", "{}");
            return require(connection, id);
        });
    }

    public TeamSnapshot kick(TeamId id, UUID actorId, UUID targetId)
            throws SQLException, DomainFailure {
        return transaction(connection -> {
            var team = requireActive(connection, id);
            requireManagePermission(connection, team, actorId, targetId);
            if (team.ownerId().equals(targetId)) {
                throw failure(TeamErrorCode.FORBIDDEN, "Owner cannot be kicked");
            }
            deleteMember(connection, id, targetId);
            bump(connection, team);
            audit(connection, id, actorId, "MEMBER_KICKED",
                    "{\"target\":\"" + targetId + "\"}");
            return require(connection, id);
        });
    }

    public TeamSnapshot transfer(TeamId id, UUID actorId, UUID targetId)
            throws SQLException, DomainFailure {
        return transaction(connection -> {
            var team = requireActive(connection, id);
            if (!team.ownerId().equals(actorId)) {
                throw failure(TeamErrorCode.FORBIDDEN, "Only the owner can transfer ownership");
            }
            requireMember(connection, id, targetId);
            setRole(connection, actorId, id, "co_owner");
            setRole(connection, targetId, id, "owner");
            try (var update = connection.prepareStatement("""
                    UPDATE teams SET owner_id = ?, version = version + 1, updated_at = ?
                    WHERE namespace = ? AND id = ? AND version = ?
                    """)) {
                update.setString(1, targetId.toString());
                update.setLong(2, clock.millis());
                update.setString(3, namespace);
                update.setString(4, id.toString());
                update.setLong(5, team.version());
                requireUpdated(update);
            }
            audit(connection, id, actorId, "OWNER_TRANSFERRED",
                    "{\"target\":\"" + targetId + "\"}");
            return require(connection, id);
        });
    }

    public TeamSnapshot rename(TeamId id, UUID actorId, String name)
            throws SQLException, DomainFailure {
        return transaction(connection -> {
            var team = requireActive(connection, id);
            requireOwner(connection, team, actorId);
            try (var delete = connection.prepareStatement(
                    "DELETE FROM team_name_claims WHERE namespace = ? AND team_id = ?")) {
                delete.setString(1, namespace);
                delete.setString(2, id.toString());
                delete.executeUpdate();
            }
            insertClaim(connection, "team_name_claims", "normalized_name", TeamNames.normalize(name), id);
            try (var update = connection.prepareStatement("""
                    UPDATE teams SET display_name = ?, normalized_name = ?,
                    version = version + 1, updated_at = ?
                    WHERE namespace = ? AND id = ? AND version = ?
                    """)) {
                update.setString(1, name.strip());
                update.setString(2, TeamNames.normalize(name));
                update.setLong(3, clock.millis());
                update.setString(4, namespace);
                update.setString(5, id.toString());
                update.setLong(6, team.version());
                requireUpdated(update);
            }
            audit(connection, id, actorId, "TEAM_RENAMED",
                    "{\"from\":\"" + json(team.name()) + "\",\"to\":\"" + json(name) + "\"}");
            return require(connection, id);
        });
    }

    public TeamSnapshot setTag(TeamId id, UUID actorId, String tag)
            throws SQLException, DomainFailure {
        return transaction(connection -> {
            var team = requireActive(connection, id);
            requireOwner(connection, team, actorId);
            try (var delete = connection.prepareStatement(
                    "DELETE FROM team_tag_claims WHERE namespace = ? AND team_id = ?")) {
                delete.setString(1, namespace);
                delete.setString(2, id.toString());
                delete.executeUpdate();
            }
            if (tag != null && !tag.isBlank()) {
                insertClaim(connection, "team_tag_claims", "normalized_tag", TeamNames.normalize(tag), id);
            }
            try (var update = connection.prepareStatement("""
                    UPDATE teams SET tag = ?, normalized_tag = ?, version = version + 1, updated_at = ?
                    WHERE namespace = ? AND id = ? AND version = ?
                    """)) {
                nullable(update, 1, tag);
                nullable(update, 2, tag == null ? null : TeamNames.normalize(tag));
                update.setLong(3, clock.millis());
                update.setString(4, namespace);
                update.setString(5, id.toString());
                update.setLong(6, team.version());
                requireUpdated(update);
            }
            audit(connection, id, actorId, "TEAM_TAG_CHANGED", "{}");
            return require(connection, id);
        });
    }

    public TeamSnapshot disband(TeamId id, UUID actorId) throws SQLException, DomainFailure {
        return transaction(connection -> {
            var team = requireActive(connection, id);
            requireOwner(connection, team, actorId);
            try (var update = connection.prepareStatement("""
                    UPDATE teams SET state = ?, deleted_at = ?, version = version + 1, updated_at = ?
                    WHERE namespace = ? AND id = ? AND version = ?
                    """)) {
                var now = clock.millis();
                update.setString(1, TeamState.DISBANDED.name());
                update.setLong(2, now);
                update.setLong(3, now);
                update.setString(4, namespace);
                update.setString(5, id.toString());
                update.setLong(6, team.version());
                requireUpdated(update);
            }
            try (var members = connection.prepareStatement(
                    "DELETE FROM team_members WHERE namespace = ? AND team_id = ?");
                 var invitations = connection.prepareStatement(
                         "DELETE FROM team_invitations WHERE namespace = ? AND team_id = ?")) {
                bindTeam(members, id);
                bindTeam(invitations, id);
                members.executeUpdate();
                invitations.executeUpdate();
            }
            audit(connection, id, actorId, "TEAM_DISBANDED",
                    "{\"memberCount\":" + team.members().size() + "}");
            return require(connection, id);
        });
    }

    public TeamSnapshot requestJoin(TeamId id, UUID actorId)
            throws SQLException, DomainFailure {
        return transaction(connection -> {
            var team = requireActive(connection, id);
            if (membershipExists(connection, actorId)) {
                throw failure(TeamErrorCode.ALREADY_IN_TEAM, "Player already belongs to a team");
            }
            if (isBanned(connection, id, actorId)) {
                throw failure(TeamErrorCode.FORBIDDEN, "Player is banned from this team");
            }
            try (var delete = connection.prepareStatement("""
                    DELETE FROM team_join_requests
                    WHERE namespace = ? AND team_id = ? AND player_id = ?
                    """)) {
                bindTeamTarget(delete, id, actorId);
                delete.executeUpdate();
            }
            var now = clock.millis();
            try (var insert = connection.prepareStatement("""
                    INSERT INTO team_join_requests(
                        namespace,team_id,player_id,created_at,expires_at
                    ) VALUES(?,?,?,?,?)
                    """)) {
                insert.setString(1, namespace);
                insert.setString(2, id.toString());
                insert.setString(3, actorId.toString());
                insert.setLong(4, now);
                insert.setLong(5, now + invitationLifetimeMillis);
                insert.executeUpdate();
            }
            bump(connection, team);
            audit(connection, id, actorId, "JOIN_REQUESTED", "{}");
            return require(connection, id);
        });
    }

    public TeamSnapshot acceptJoinRequest(TeamId id, UUID actorId, UUID targetId)
            throws SQLException, DomainFailure {
        return transaction(connection -> {
            var team = requireActive(connection, id);
            requireManagePermission(connection, team, actorId, null);
            if (membershipExists(connection, targetId)) {
                throw failure(TeamErrorCode.ALREADY_IN_TEAM, "Target already belongs to a team");
            }
            if (isBanned(connection, id, targetId)) {
                throw failure(TeamErrorCode.FORBIDDEN, "Target is banned from this team");
            }
            if (team.members().size() >= team.memberLimit()) {
                throw failure(TeamErrorCode.LIMIT_REACHED, "Team member limit reached");
            }
            try (var delete = connection.prepareStatement("""
                    DELETE FROM team_join_requests
                    WHERE namespace = ? AND team_id = ? AND player_id = ?
                    """)) {
                bindTeamTarget(delete, id, targetId);
                if (delete.executeUpdate() != 1) {
                    throw failure(TeamErrorCode.NOT_FOUND, "Join request not found");
                }
            }
            insertMember(connection, id, targetId, "member", clock.millis());
            bump(connection, team);
            audit(connection, id, actorId, "JOIN_REQUEST_ACCEPTED",
                    "{\"target\":\"" + targetId + "\"}");
            return require(connection, id);
        });
    }

    public TeamSnapshot ban(
            TeamId id,
            UUID actorId,
            UUID targetId,
            String reason
    ) throws SQLException, DomainFailure {
        return transaction(connection -> {
            var team = requireActive(connection, id);
            requireManagePermission(connection, team, actorId,
                    membershipExistsInTeam(connection, id, targetId) ? targetId : null);
            if (team.ownerId().equals(targetId)) {
                throw failure(TeamErrorCode.FORBIDDEN, "Owner cannot be banned");
            }
            if (membershipExistsInTeam(connection, id, targetId)) {
                deleteMember(connection, id, targetId);
            }
            deleteTeamPlayerState(connection, id, targetId);
            try (var delete = connection.prepareStatement("""
                    DELETE FROM team_bans
                    WHERE namespace = ? AND team_id = ? AND player_id = ?
                    """)) {
                bindTeamTarget(delete, id, targetId);
                delete.executeUpdate();
            }
            try (var insert = connection.prepareStatement("""
                    INSERT INTO team_bans(
                        namespace,team_id,player_id,actor_id,reason,created_at,expires_at
                    ) VALUES(?,?,?,?,?,?,NULL)
                    """)) {
                insert.setString(1, namespace);
                insert.setString(2, id.toString());
                insert.setString(3, targetId.toString());
                insert.setString(4, actorId.toString());
                nullable(insert, 5, reason);
                insert.setLong(6, clock.millis());
                insert.executeUpdate();
            }
            bump(connection, team);
            audit(connection, id, actorId, "MEMBER_BANNED",
                    "{\"target\":\"" + targetId + "\"}");
            return require(connection, id);
        });
    }

    public TeamSnapshot unban(TeamId id, UUID actorId, UUID targetId)
            throws SQLException, DomainFailure {
        return transaction(connection -> {
            var team = requireActive(connection, id);
            requireManagePermission(connection, team, actorId, null);
            try (var delete = connection.prepareStatement("""
                    DELETE FROM team_bans
                    WHERE namespace = ? AND team_id = ? AND player_id = ?
                    """)) {
                bindTeamTarget(delete, id, targetId);
                if (delete.executeUpdate() != 1) {
                    throw failure(TeamErrorCode.NOT_FOUND, "Ban not found");
                }
            }
            bump(connection, team);
            audit(connection, id, actorId, "MEMBER_UNBANNED",
                    "{\"target\":\"" + targetId + "\"}");
            return require(connection, id);
        });
    }

    public TeamSnapshot changeRole(
            TeamId id,
            UUID actorId,
            UUID targetId,
            String roleKey
    ) throws SQLException, DomainFailure {
        return transaction(connection -> {
            var team = requireActive(connection, id);
            requirePermission(connection, id, actorId, "team.role.change");
            if (team.ownerId().equals(targetId) || roleKey.equals("owner")) {
                throw failure(TeamErrorCode.FORBIDDEN,
                        "Owner role can only change through ownership transfer");
            }
            var actorPriority = rolePriority(connection, id, actorId);
            var targetPriority = rolePriority(connection, id, targetId);
            var newRole = loadRole(connection, roleKey);
            if (actorPriority <= targetPriority || actorPriority <= newRole.priority()) {
                throw failure(TeamErrorCode.FORBIDDEN,
                        "Cannot assign an equal or higher role");
            }
            if (newRole.memberLimit() != null
                    && roleMemberCount(connection, id, roleKey) >= newRole.memberLimit()) {
                throw failure(TeamErrorCode.LIMIT_REACHED, "Role member limit reached");
            }
            setRole(connection, targetId, id, roleKey);
            bump(connection, team);
            audit(connection, id, actorId, "MEMBER_ROLE_CHANGED",
                    "{\"target\":\"" + targetId + "\",\"role\":\""
                            + json(roleKey) + "\"}");
            return require(connection, id);
        });
    }

    public TeamSnapshot setSetting(
            TeamId id,
            UUID actorId,
            String key,
            String value,
            String permission
    ) throws SQLException, DomainFailure {
        return transaction(connection -> {
            var team = requireActive(connection, id);
            requirePermission(connection, id, actorId, permission);
            try (var delete = connection.prepareStatement("""
                    DELETE FROM team_settings
                    WHERE namespace = ? AND team_id = ? AND setting_key = ?
                    """)) {
                delete.setString(1, namespace);
                delete.setString(2, id.toString());
                delete.setString(3, key);
                delete.executeUpdate();
            }
            try (var insert = connection.prepareStatement("""
                    INSERT INTO team_settings(
                        namespace,team_id,setting_key,setting_value,version
                    ) VALUES(?,?,?,?,?)
                    """)) {
                insert.setString(1, namespace);
                insert.setString(2, id.toString());
                insert.setString(3, key);
                insert.setString(4, value);
                insert.setLong(5, team.version() + 1);
                insert.executeUpdate();
            }
            bump(connection, team);
            audit(connection, id, actorId, "TEAM_SETTING_CHANGED",
                    "{\"key\":\"" + json(key) + "\"}");
            return require(connection, id);
        });
    }

    private Optional<TeamSnapshot> load(Connection connection, TeamId id) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT display_name,normalized_name,tag,owner_id,state,visibility,
                       member_limit,version,created_at,updated_at
                FROM teams WHERE namespace = ? AND id = ?
                """)) {
            bindTeam(statement, id);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                var members = loadMembers(connection, id);
                return Optional.of(new TeamSnapshot(
                        id,
                        result.getString("display_name"),
                        result.getString("normalized_name"),
                        result.getString("tag"),
                        UUID.fromString(result.getString("owner_id")),
                        TeamState.valueOf(result.getString("state")),
                        TeamVisibility.valueOf(result.getString("visibility")),
                        result.getInt("member_limit"),
                        result.getLong("version"),
                        Instant.ofEpochMilli(result.getLong("created_at")),
                        Instant.ofEpochMilli(result.getLong("updated_at")),
                        loadSettings(connection, id),
                        members
                ));
            }
        }
    }

    private Map<TeamId, TeamSnapshot> loadMany(
            Connection connection,
            Set<TeamId> teamIds
    ) throws SQLException {
        if (teamIds.isEmpty()) {
            return Map.of();
        }
        var rows = new HashMap<TeamId, TeamRow>();
        var members = new HashMap<TeamId, ArrayList<TeamMemberSnapshot>>();
        var settings = new HashMap<TeamId, Map<String, String>>();
        var ids = new ArrayList<>(teamIds);
        var permissionCache = new HashMap<String, Set<String>>();
        for (var offset = 0; offset < ids.size(); offset += 300) {
            var chunk = ids.subList(offset, Math.min(offset + 300, ids.size()));
            var in = String.join(",", java.util.Collections.nCopies(chunk.size(), "?"));
            try (var statement = connection.prepareStatement("""
                    SELECT id,display_name,normalized_name,tag,owner_id,state,visibility,
                           member_limit,version,created_at,updated_at
                    FROM teams WHERE namespace = ? AND id IN (
                    """ + in + ")")) {
                bindIds(statement, chunk);
                try (var result = statement.executeQuery()) {
                    while (result.next()) {
                        var id = TeamId.parse(result.getString("id"));
                        rows.put(id, new TeamRow(
                                result.getString("display_name"),
                                result.getString("normalized_name"),
                                result.getString("tag"),
                                UUID.fromString(result.getString("owner_id")),
                                TeamState.valueOf(result.getString("state")),
                                TeamVisibility.valueOf(result.getString("visibility")),
                                result.getInt("member_limit"),
                                result.getLong("version"),
                                Instant.ofEpochMilli(result.getLong("created_at")),
                                Instant.ofEpochMilli(result.getLong("updated_at"))));
                    }
                }
            }
            try (var statement = connection.prepareStatement("""
                    SELECT team_id,player_id,role_key,joined_at,last_active_at
                    FROM team_members WHERE namespace = ? AND team_id IN (
                    """ + in + ")")) {
                bindIds(statement, chunk);
                try (var result = statement.executeQuery()) {
                    while (result.next()) {
                        var id = TeamId.parse(result.getString("team_id"));
                        var role = result.getString("role_key");
                        var permissions = permissionCache.get(role);
                        if (permissions == null) {
                            permissions = loadPermissions(connection, role);
                            permissionCache.put(role, permissions);
                        }
                        members.computeIfAbsent(id, ignored -> new ArrayList<>()).add(
                                new TeamMemberSnapshot(
                                        UUID.fromString(result.getString("player_id")),
                                        role,
                                        permissions,
                                        Instant.ofEpochMilli(result.getLong("joined_at")),
                                        Instant.ofEpochMilli(result.getLong("last_active_at"))));
                    }
                }
            }
            try (var statement = connection.prepareStatement("""
                    SELECT team_id,setting_key,setting_value
                    FROM team_settings WHERE namespace = ? AND team_id IN (
                    """ + in + ")")) {
                bindIds(statement, chunk);
                try (var result = statement.executeQuery()) {
                    while (result.next()) {
                        settings.computeIfAbsent(
                                        TeamId.parse(result.getString("team_id")),
                                        ignored -> new HashMap<>())
                                .put(result.getString("setting_key"),
                                        result.getString("setting_value"));
                    }
                }
            }
        }
        var snapshots = new HashMap<TeamId, TeamSnapshot>();
        rows.forEach((id, row) -> {
            var teamMembers = members.getOrDefault(id, new ArrayList<>());
            teamMembers.sort(java.util.Comparator.comparing(
                    TeamMemberSnapshot::joinedAt));
            snapshots.put(id, new TeamSnapshot(
                    id, row.name(), row.normalizedName(), row.tag(), row.ownerId(),
                    row.state(), row.visibility(), row.memberLimit(), row.version(),
                    row.createdAt(), row.updatedAt(),
                    settings.getOrDefault(id, Map.of()), teamMembers));
        });
        return Map.copyOf(snapshots);
    }

    private void bindIds(PreparedStatement statement, java.util.List<TeamId> ids)
            throws SQLException {
        statement.setString(1, namespace);
        for (var index = 0; index < ids.size(); index++) {
            statement.setString(index + 2, ids.get(index).toString());
        }
    }

    private java.util.Map<String, String> loadSettings(Connection connection, TeamId id)
            throws SQLException {
        var settings = new java.util.HashMap<String, String>();
        try (var statement = connection.prepareStatement("""
                SELECT setting_key,setting_value FROM team_settings
                WHERE namespace = ? AND team_id = ?
                """)) {
            bindTeam(statement, id);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    settings.put(result.getString(1), result.getString(2));
                }
            }
        }
        return java.util.Map.copyOf(settings);
    }

    private ArrayList<TeamMemberSnapshot> loadMembers(Connection connection, TeamId id) throws SQLException {
        var members = new ArrayList<TeamMemberSnapshot>();
        try (var statement = connection.prepareStatement("""
                SELECT player_id,role_key,joined_at,last_active_at
                FROM team_members WHERE namespace = ? AND team_id = ?
                ORDER BY joined_at
                """)) {
            bindTeam(statement, id);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    members.add(new TeamMemberSnapshot(
                            UUID.fromString(result.getString("player_id")),
                            result.getString("role_key"),
                            loadPermissions(connection, result.getString("role_key")),
                            Instant.ofEpochMilli(result.getLong("joined_at")),
                            Instant.ofEpochMilli(result.getLong("last_active_at"))
                    ));
                }
            }
        }
        return members;
    }

    private java.util.Set<String> loadPermissions(Connection connection, String roleKey)
            throws SQLException {
        var permissions = new java.util.HashSet<String>();
        try (var statement = connection.prepareStatement("""
                SELECT permission_key FROM role_permissions
                WHERE namespace = ? AND role_key = ?
                """)) {
            statement.setString(1, namespace);
            statement.setString(2, roleKey);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    permissions.add(result.getString(1));
                }
            }
        }
        return java.util.Set.copyOf(permissions);
    }

    private TeamSnapshot require(Connection connection, TeamId id) throws SQLException, DomainFailure {
        return load(connection, id).orElseThrow(() ->
                failure(TeamErrorCode.NOT_FOUND, "Team not found"));
    }

    private TeamSnapshot requireActive(Connection connection, TeamId id)
            throws SQLException, DomainFailure {
        var team = require(connection, id);
        if (team.state() != TeamState.ACTIVE) {
            throw failure(TeamErrorCode.CONFLICT, "Team is not active");
        }
        return team;
    }

    private void requireOwner(Connection connection, TeamSnapshot team, UUID actorId)
            throws DomainFailure {
        if (!team.ownerId().equals(actorId)) {
            throw failure(TeamErrorCode.FORBIDDEN, "Only the owner can perform this action");
        }
    }

    private void requireManagePermission(
            Connection connection,
            TeamSnapshot team,
            UUID actorId,
            UUID targetId
    ) throws SQLException, DomainFailure {
        var actorPriority = rolePriority(connection, team.id(), actorId);
        if (actorPriority < 500) {
            throw failure(TeamErrorCode.FORBIDDEN, "Insufficient team permission");
        }
        if (targetId != null && actorPriority <= rolePriority(connection, team.id(), targetId)) {
            throw failure(TeamErrorCode.FORBIDDEN, "Cannot manage an equal or higher role");
        }
    }

    private void requirePermission(
            Connection connection,
            TeamId id,
            UUID actorId,
            String permission
    ) throws SQLException, DomainFailure {
        var role = memberRole(connection, id, actorId);
        try (var statement = connection.prepareStatement("""
                SELECT 1 FROM role_permissions
                WHERE namespace = ? AND role_key = ?
                  AND (permission_key = ? OR permission_key = '*')
                """)) {
            statement.setString(1, namespace);
            statement.setString(2, role);
            statement.setString(3, permission);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw failure(TeamErrorCode.FORBIDDEN, "Insufficient team permission");
                }
            }
        }
    }

    private String memberRole(Connection connection, TeamId id, UUID playerId)
            throws SQLException, DomainFailure {
        try (var statement = connection.prepareStatement("""
                SELECT role_key FROM team_members
                WHERE namespace = ? AND team_id = ? AND player_id = ?
                """)) {
            bindTeamTarget(statement, id, playerId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw failure(TeamErrorCode.NOT_IN_TEAM, "Player is not a team member");
                }
                return result.getString(1);
            }
        }
    }

    private RoleRow loadRole(Connection connection, String roleKey)
            throws SQLException, DomainFailure {
        try (var statement = connection.prepareStatement("""
                SELECT priority,member_limit FROM role_templates
                WHERE namespace = ? AND role_key = ?
                """)) {
            statement.setString(1, namespace);
            statement.setString(2, roleKey);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw failure(TeamErrorCode.NOT_FOUND, "Role not found");
                }
                var limit = result.getObject("member_limit") == null
                        ? null : result.getInt("member_limit");
                return new RoleRow(result.getInt("priority"), limit);
            }
        }
    }

    private int roleMemberCount(Connection connection, TeamId id, String roleKey)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM team_members
                WHERE namespace = ? AND team_id = ? AND role_key = ?
                """)) {
            statement.setString(1, namespace);
            statement.setString(2, id.toString());
            statement.setString(3, roleKey);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private int rolePriority(Connection connection, TeamId id, UUID playerId)
            throws SQLException, DomainFailure {
        try (var statement = connection.prepareStatement("""
                SELECT r.priority FROM team_members m
                JOIN role_templates r ON r.namespace = m.namespace AND r.role_key = m.role_key
                WHERE m.namespace = ? AND m.team_id = ? AND m.player_id = ?
                """)) {
            statement.setString(1, namespace);
            statement.setString(2, id.toString());
            statement.setString(3, playerId.toString());
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw failure(TeamErrorCode.NOT_IN_TEAM, "Player is not a team member");
                }
                return result.getInt(1);
            }
        }
    }

    private void requireMember(Connection connection, TeamId id, UUID playerId)
            throws SQLException, DomainFailure {
        rolePriority(connection, id, playerId);
    }

    private boolean membershipExists(Connection connection, UUID playerId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM team_members WHERE namespace = ? AND player_id = ?")) {
            statement.setString(1, namespace);
            statement.setString(2, playerId.toString());
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean membershipExistsInTeam(Connection connection, TeamId id, UUID playerId)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT 1 FROM team_members
                WHERE namespace = ? AND team_id = ? AND player_id = ?
                """)) {
            bindTeamTarget(statement, id, playerId);
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean isBanned(Connection connection, TeamId id, UUID playerId)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT 1 FROM team_bans
                WHERE namespace = ? AND team_id = ? AND player_id = ?
                  AND (expires_at IS NULL OR expires_at > ?)
                """)) {
            bindTeamTarget(statement, id, playerId);
            statement.setLong(4, clock.millis());
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void deleteTeamPlayerState(Connection connection, TeamId id, UUID playerId)
            throws SQLException {
        for (var table : java.util.List.of("team_invitations", "team_join_requests")) {
            var playerColumn = table.equals("team_invitations") ? "target_id" : "player_id";
            try (var statement = connection.prepareStatement(
                    "DELETE FROM " + table
                            + " WHERE namespace = ? AND team_id = ? AND " + playerColumn + " = ?")) {
                bindTeamTarget(statement, id, playerId);
                statement.executeUpdate();
            }
        }
    }

    private void insertMember(Connection connection, TeamId id, UUID playerId, String role, long now)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO team_members(
                    namespace,player_id,team_id,role_key,joined_at,last_active_at,version
                ) VALUES(?,?,?,?,?,?,0)
                """)) {
            statement.setString(1, namespace);
            statement.setString(2, playerId.toString());
            statement.setString(3, id.toString());
            statement.setString(4, role);
            statement.setLong(5, now);
            statement.setLong(6, now);
            statement.executeUpdate();
        }
    }

    private void deleteMember(Connection connection, TeamId id, UUID playerId)
            throws SQLException, DomainFailure {
        try (var statement = connection.prepareStatement("""
                DELETE FROM team_members WHERE namespace = ? AND team_id = ? AND player_id = ?
                """)) {
            statement.setString(1, namespace);
            statement.setString(2, id.toString());
            statement.setString(3, playerId.toString());
            if (statement.executeUpdate() != 1) {
                throw failure(TeamErrorCode.NOT_IN_TEAM, "Player is not a team member");
            }
        }
    }

    private void setRole(Connection connection, UUID playerId, TeamId id, String role)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                UPDATE team_members SET role_key = ?, version = version + 1
                WHERE namespace = ? AND team_id = ? AND player_id = ?
                """)) {
            statement.setString(1, role);
            statement.setString(2, namespace);
            statement.setString(3, id.toString());
            statement.setString(4, playerId.toString());
            statement.executeUpdate();
        }
    }

    private void bump(Connection connection, TeamSnapshot team) throws SQLException, DomainFailure {
        try (var statement = connection.prepareStatement("""
                UPDATE teams SET version = version + 1, updated_at = ?
                WHERE namespace = ? AND id = ? AND version = ?
                """)) {
            statement.setLong(1, clock.millis());
            statement.setString(2, namespace);
            statement.setString(3, team.id().toString());
            statement.setLong(4, team.version());
            requireUpdated(statement);
        }
    }

    private static void requireUpdated(PreparedStatement statement) throws SQLException, DomainFailure {
        if (statement.executeUpdate() != 1) {
            throw failure(TeamErrorCode.CONFLICT, "Team was modified concurrently");
        }
    }

    private void insertClaim(
            Connection connection,
            String table,
            String column,
            String value,
            TeamId id
    ) throws SQLException {
        var sql = "INSERT INTO " + table + "(namespace," + column + ",team_id) VALUES(?,?,?)";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, namespace);
            statement.setString(2, value);
            statement.setString(3, id.toString());
            statement.executeUpdate();
        }
    }

    private void audit(
            Connection connection,
            TeamId id,
            UUID actorId,
            String action,
            String payload
    ) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO audit_entries(
                    namespace,id,team_id,actor_id,action,correlation_id,actor_type,
                    before_json,after_json,metadata_json,created_at
                ) VALUES(?,?,?,?,?,?,?,NULL,NULL,?,?)
                """)) {
            statement.setString(1, namespace);
            statement.setString(2, UUID.randomUUID().toString());
            statement.setString(3, id.toString());
            statement.setString(4, actorId.toString());
            statement.setString(5, action);
            var correlationId = activeCorrelationId.get();
            statement.setString(6, correlationId == null
                    ? UUID.randomUUID().toString()
                    : correlationId);
            statement.setString(7, "PLAYER");
            statement.setString(8, payload);
            statement.setLong(9, clock.millis());
            statement.executeUpdate();
        }
    }

    private <T> T transaction(TransactionWork<T> work) throws SQLException, DomainFailure {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                database.assertLease(connection);
                var value = work.run(connection);
                connection.commit();
                return value;
            } catch (SQLException | DomainFailure exception) {
                connection.rollback();
                throw exception;
            } catch (RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void bindTeam(PreparedStatement statement, TeamId id) throws SQLException {
        statement.setString(1, namespace);
        statement.setString(2, id.toString());
    }

    private void bindTeamTarget(PreparedStatement statement, TeamId id, UUID target) throws SQLException {
        statement.setString(1, namespace);
        statement.setString(2, id.toString());
        statement.setString(3, target.toString());
    }

    private static void nullable(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(index, java.sql.Types.VARCHAR);
        } else {
            statement.setString(index, value.strip());
        }
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static DomainFailure failure(TeamErrorCode code, String message) {
        return new DomainFailure(code, message);
    }

    @FunctionalInterface
    private interface TransactionWork<T> {
        T run(Connection connection) throws SQLException, DomainFailure;
    }

    @FunctionalInterface
    public interface CorrelatedWork<T> {
        T run() throws SQLException, DomainFailure;
    }

    private record RoleRow(int priority, Integer memberLimit) {
    }

    private record TeamRow(
            String name,
            String normalizedName,
            String tag,
            UUID ownerId,
            TeamState state,
            TeamVisibility visibility,
            int memberLimit,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
