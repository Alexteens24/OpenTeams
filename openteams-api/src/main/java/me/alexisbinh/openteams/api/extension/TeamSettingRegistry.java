package me.alexisbinh.openteams.api.extension;

import java.util.Objects;
import java.util.function.Predicate;
import org.bukkit.plugin.Plugin;

public interface TeamSettingRegistry {
    <T> Registration register(Plugin owner, Setting<T> setting);

    record Setting<T>(
            String key,
            Class<T> type,
            T defaultValue,
            Codec<T> codec,
            Predicate<T> validator,
            String permission
    ) {
        public Setting {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(defaultValue, "defaultValue");
            Objects.requireNonNull(codec, "codec");
            Objects.requireNonNull(validator, "validator");
            Objects.requireNonNull(permission, "permission");
        }
    }

    interface Codec<T> {
        String encode(T value);

        T decode(String value);
    }
}
