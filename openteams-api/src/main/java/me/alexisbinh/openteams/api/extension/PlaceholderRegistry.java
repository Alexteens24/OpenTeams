package me.alexisbinh.openteams.api.extension;

import java.util.Objects;
import java.util.UUID;
import me.alexisbinh.openteams.api.TeamSnapshot;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.Plugin;

public interface PlaceholderRegistry {
    Registration register(Plugin owner, Placeholder placeholder);

    record Placeholder(String key, Resolver resolver, Component fallback) {
        public Placeholder {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(resolver, "resolver");
            Objects.requireNonNull(fallback, "fallback");
        }
    }

    @FunctionalInterface
    interface Resolver {
        Component resolve(UUID viewerId, TeamSnapshot snapshot);
    }
}
