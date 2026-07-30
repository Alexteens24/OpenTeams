package me.alexisbinh.openteams.api.extension;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

public interface CommandRegistry {
    Registration register(Plugin owner, CommandContribution contribution);

    record CommandContribution(
            String name,
            List<String> aliases,
            String permission,
            String descriptionKey,
            Handler handler
    ) {
        public CommandContribution {
            Objects.requireNonNull(name, "name");
            aliases = List.copyOf(aliases);
            Objects.requireNonNull(permission, "permission");
            Objects.requireNonNull(descriptionKey, "descriptionKey");
            Objects.requireNonNull(handler, "handler");
        }
    }

    @FunctionalInterface
    interface Handler {
        CompletionStage<Integer> execute(CommandSender sender, String[] arguments);
    }
}
