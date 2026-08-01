package me.alexisbinh.openteams.api.extension;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import me.alexisbinh.openteams.api.TeamId;
import org.bukkit.plugin.Plugin;

public interface TeamUiRegistry {
    Registration register(Plugin owner, UiAction action);

    record UiAction(
            String key,
            Area area,
            int priority,
            String labelKey,
            String descriptionKey,
            String permission,
            Availability availability,
            Handler handler
    ) {
        public UiAction {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(area, "area");
            Objects.requireNonNull(labelKey, "labelKey");
            Objects.requireNonNull(descriptionKey, "descriptionKey");
            Objects.requireNonNull(permission, "permission");
            Objects.requireNonNull(availability, "availability");
            Objects.requireNonNull(handler, "handler");
        }
    }

    public enum Area {
        DASHBOARD,
        MEMBERS,
        SETTINGS
    }

    public record UiContext(UUID viewerId, TeamId teamId, long teamVersion) {
    }

    public enum ActionOutcome {
        REFRESH,
        CLOSE
    }

    @FunctionalInterface
    interface Availability {
        boolean available(UiContext context);
    }

    @FunctionalInterface
    interface Handler {
        CompletionStage<ActionOutcome> execute(UiContext context);
    }
}
