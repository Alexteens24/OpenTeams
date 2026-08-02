package me.alexisbinh.openteams.api;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Thread-safe entry point for team queries and mutations.
 *
 * <p>Cached queries never perform database I/O. Authoritative queries and all
 * mutations complete on an OpenTeams worker thread.</p>
 */
public interface TeamService {
    Optional<TeamSnapshot> findCached(TeamId id);

    MembershipLookup membershipCached(UUID playerId);

    TeamRelation relationCached(UUID firstPlayerId, UUID secondPlayerId);

    boolean hasPermissionCached(UUID playerId, String permission);

    CompletionStage<Optional<TeamSnapshot>> find(TeamId id);

    CompletionStage<MembershipLookup> loadMembership(UUID playerId);

    CompletionStage<TeamDirectory.Page<TeamDirectory.TeamSummary>> searchPublicTeams(
            String query, int page, int pageSize);

    CompletionStage<List<TeamDirectory.Invitation>> invitations(UUID playerId);

    CompletionStage<List<TeamDirectory.JoinRequest>> joinRequests(TeamId teamId);

    CompletionStage<List<TeamDirectory.OutgoingJoinRequest>> joinRequestsByPlayer(UUID playerId);

    CompletionStage<List<TeamDirectory.OutgoingInvitation>> outgoingInvitations(TeamId teamId);

    CompletionStage<List<TeamDirectory.Ban>> bans(TeamId teamId);

    CompletionStage<List<TeamDirectory.Role>> roles();

    CompletionStage<OperationResult<TeamSnapshot>> create(TeamRequests.Create request);

    CompletionStage<OperationResult<TeamSnapshot>> disband(TeamRequests.TeamAction request);

    CompletionStage<OperationResult<TeamSnapshot>> invite(TeamRequests.TargetAction request);

    CompletionStage<OperationResult<TeamSnapshot>> acceptInvitation(TeamRequests.TargetAction request);

    CompletionStage<OperationResult<TeamSnapshot>> declineInvitation(TeamRequests.TargetAction request);

    CompletionStage<OperationResult<TeamSnapshot>> revokeInvitation(TeamRequests.TargetAction request);

    CompletionStage<OperationResult<TeamSnapshot>> leave(TeamRequests.TeamAction request);

    CompletionStage<OperationResult<TeamSnapshot>> kick(TeamRequests.TargetAction request);

    CompletionStage<OperationResult<TeamSnapshot>> transferOwnership(TeamRequests.TargetAction request);

    CompletionStage<OperationResult<TeamSnapshot>> rename(TeamRequests.Rename request);

    CompletionStage<OperationResult<TeamSnapshot>> setTag(TeamRequests.SetTag request);

    CompletionStage<OperationResult<TeamSnapshot>> requestJoin(TeamRequests.TeamAction request);

    CompletionStage<OperationResult<TeamSnapshot>> acceptJoinRequest(
            TeamRequests.TargetAction request);

    CompletionStage<OperationResult<TeamSnapshot>> rejectJoinRequest(
            TeamRequests.TargetAction request);

    CompletionStage<OperationResult<TeamSnapshot>> cancelJoinRequest(
            TeamRequests.TeamAction request);

    CompletionStage<OperationResult<TeamSnapshot>> ban(TeamRequests.Ban request);

    CompletionStage<OperationResult<TeamSnapshot>> unban(TeamRequests.TargetAction request);

    CompletionStage<OperationResult<TeamSnapshot>> changeRole(TeamRequests.ChangeRole request);

    CompletionStage<OperationResult<TeamSnapshot>> setSetting(TeamRequests.SetSetting request);

    CompletionStage<OperationResult<TeamSnapshot>> setVisibility(
            TeamRequests.SetVisibility request);
}
