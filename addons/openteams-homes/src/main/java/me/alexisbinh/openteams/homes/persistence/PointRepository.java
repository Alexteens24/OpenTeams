package me.alexisbinh.openteams.homes.persistence;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import me.alexisbinh.openteams.api.TeamId;
import me.alexisbinh.openteams.homes.domain.PointPage;
import me.alexisbinh.openteams.homes.domain.StoredLocation;
import me.alexisbinh.openteams.homes.domain.TeleportPoint;

public interface PointRepository {
    Optional<TeleportPoint> findHome(TeamId teamId) throws SQLException;

    Optional<TeleportPoint> findWarp(TeamId teamId, String normalizedName) throws SQLException;

    Optional<TeleportPoint> findById(UUID id) throws SQLException;

    PointPage searchWarps(TeamId teamId, String query, int page, int pageSize) throws SQLException;

    TeleportPoint setHome(TeamId teamId, StoredLocation location, UUID actor,
                          OptionalLong expectedVersion) throws SQLException;

    TeleportPoint createWarp(TeamId teamId, String displayName, String normalizedName,
                             StoredLocation location, UUID actor, int limit) throws SQLException;

    TeleportPoint updateLocation(UUID id, TeamId teamId, long expectedVersion,
                                 StoredLocation location) throws SQLException;

    TeleportPoint renameWarp(UUID id, TeamId teamId, long expectedVersion,
                             String displayName, String normalizedName) throws SQLException;

    void delete(UUID id, TeamId teamId, long expectedVersion) throws SQLException;

    void deleteTeam(TeamId teamId) throws SQLException;

    List<TeamId> teamIds(int limit) throws SQLException;

    final class Conflict extends SQLException {
        private static final long serialVersionUID = 1L;
        public Conflict() { super("Point changed concurrently"); }
    }

    final class DuplicateName extends SQLException {
        private static final long serialVersionUID = 1L;
        public DuplicateName() { super("Warp name already exists"); }
    }

    final class LimitReached extends SQLException {
        private static final long serialVersionUID = 1L;
        public LimitReached() { super("Warp limit reached"); }
    }
}
