package me.alexisbinh.openteams.api.extension;

import java.util.Objects;
import java.util.Set;
import org.bukkit.plugin.Plugin;

public interface TeamPermissionRegistry {
    Registration register(Plugin owner, Permission permission);

    record Permission(String key, String descriptionTranslationKey, Set<String> defaultRoles) {
        public Permission {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(descriptionTranslationKey, "descriptionTranslationKey");
            defaultRoles = Set.copyOf(defaultRoles);
        }
    }
}
