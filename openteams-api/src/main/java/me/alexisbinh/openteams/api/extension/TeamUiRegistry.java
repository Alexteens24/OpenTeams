package me.alexisbinh.openteams.api.extension;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import me.alexisbinh.openteams.api.TeamSnapshot;
import org.bukkit.plugin.Plugin;

public interface TeamUiRegistry {
    Registration register(Plugin owner, UiAction action);

    record UiAction(
            String key,
            String area,
            int priority,
            String labelTranslationKey,
            String permission,
            Visibility visibility,
            Handler handler
    ) {
        public UiAction {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(area, "area");
            Objects.requireNonNull(labelTranslationKey, "labelTranslationKey");
            Objects.requireNonNull(permission, "permission");
            Objects.requireNonNull(visibility, "visibility");
            Objects.requireNonNull(handler, "handler");
        }
    }

    @FunctionalInterface
    interface Visibility {
        boolean visible(UUID viewerId, TeamSnapshot snapshot);
    }

    @FunctionalInterface
    interface Handler {
        CompletionStage<Void> execute(UUID viewerId, TeamSnapshot snapshot);
    }
}
