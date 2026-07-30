package me.alexisbinh.openteams.api;

import java.util.Objects;
import java.util.UUID;

public final class TeamRequests {
    private TeamRequests() {
    }

    public record Create(UUID actorId, String name, String tag) {
        public Create {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(name, "name");
        }
    }

    public record TeamAction(UUID actorId, TeamId teamId) {
        public TeamAction {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(teamId, "teamId");
        }
    }

    public record TargetAction(UUID actorId, TeamId teamId, UUID targetId) {
        public TargetAction {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(teamId, "teamId");
            Objects.requireNonNull(targetId, "targetId");
        }
    }

    public record Rename(UUID actorId, TeamId teamId, String name) {
        public Rename {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(teamId, "teamId");
            Objects.requireNonNull(name, "name");
        }
    }

    public record SetTag(UUID actorId, TeamId teamId, String tag) {
        public SetTag {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(teamId, "teamId");
        }
    }

    public record Ban(UUID actorId, TeamId teamId, UUID targetId, String reason) {
        public Ban {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(teamId, "teamId");
            Objects.requireNonNull(targetId, "targetId");
        }
    }

    public record ChangeRole(UUID actorId, TeamId teamId, UUID targetId, String roleKey) {
        public ChangeRole {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(teamId, "teamId");
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(roleKey, "roleKey");
        }
    }

    public record SetSetting(UUID actorId, TeamId teamId, String key, String encodedValue) {
        public SetSetting {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(teamId, "teamId");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(encodedValue, "encodedValue");
        }
    }
}
