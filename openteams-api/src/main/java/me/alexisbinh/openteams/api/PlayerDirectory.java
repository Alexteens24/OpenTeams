package me.alexisbinh.openteams.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Authoritative directory of players that have joined this OpenTeams namespace. */
public interface PlayerDirectory {
    CompletionStage<Map<UUID, TeamDirectory.PlayerSummary>> resolve(Collection<UUID> playerIds);

    /** Returns every exact normalized-name match; old names may belong to multiple UUIDs. */
    CompletionStage<List<TeamDirectory.PlayerSummary>> findExact(String name);

    CompletionStage<List<TeamDirectory.PlayerSummary>> search(String query, int limit);

    CompletionStage<Void> remember(UUID playerId, String currentName);
}
