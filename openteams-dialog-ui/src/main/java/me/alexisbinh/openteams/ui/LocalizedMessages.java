package me.alexisbinh.openteams.ui;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.function.BiFunction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

public final class LocalizedMessages {
    private static final String BUNDLE = "me.alexisbinh.openteams.ui.messages";
    private static final java.util.Set<String> COLOR_ACCENTS = java.util.Set.of(
            "dashboard.title",
            "dashboard.create", "dashboard.leave",
            "action.create", "action.save", "action.accept", "action.request-join",
            "action.decline", "action.reject", "action.confirm", "action.disband",
            "member.kick", "member.ban", "settings.disband",
            "ban.title", "disband.title", "confirm.title",
            "form.confirm-team-name", "confirm.disband",
            "chat.enabled", "spy.enabled",
            "command.create-button", "command.request-button", "command.accept-button",
            "command.decline-button", "command.leave-button"
    );

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Locale fallback;
    private final BiFunction<Locale, String, String> extensionLookup;
    private final boolean followPlayerLocale;

    public LocalizedMessages(Locale fallback) {
        this(fallback, (locale, key) -> null, true);
    }

    public LocalizedMessages(Locale fallback,
                             BiFunction<Locale, String, String> extensionLookup) {
        this(fallback, extensionLookup, true);
    }

    public LocalizedMessages(Locale fallback,
                             BiFunction<Locale, String, String> extensionLookup,
                             boolean followPlayerLocale) {
        this.fallback = fallback;
        this.extensionLookup = extensionLookup;
        this.followPlayerLocale = followPlayerLocale;
    }

    public Component component(Player player, String key) {
        return component(player.locale(), key);
    }

    public Component component(Player player, String key, java.util.Map<String, String> arguments) {
        var resolvers = arguments.entrySet().stream()
                .map(entry -> Placeholder.unparsed(entry.getKey(), entry.getValue()))
                .toArray(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]::new);
        var rendered = miniMessage.deserialize(playerText(player.locale(), key), resolvers);
        return preserveAccent(key) ? rendered : withoutColor(rendered);
    }

    Component component(Locale playerLocale, String key) {
        var rendered = miniMessage.deserialize(playerText(playerLocale, key));
        if (preserveAccent(key)) return rendered;
        return withoutColor(rendered);
    }

    private static boolean preserveAccent(String key) {
        return key.startsWith("success.") || key.startsWith("error.")
                || COLOR_ACCENTS.contains(key);
    }

    private static Component withoutColor(Component rendered) {
        var builder = rendered.toBuilder().color(null);
        builder.applyDeep(child -> child.color(null));
        return builder.build();
    }

    public String playerText(Locale playerLocale, String key) {
        return text(followPlayerLocale ? playerLocale : fallback, key);
    }

    public String text(Locale locale, String key) {
        var extension = extensionLookup.apply(locale, key);
        if (extension != null) {
            return extension;
        }
        try {
            return ResourceBundle.getBundle(BUNDLE, locale).getString(key);
        } catch (MissingResourceException ignored) {
            try {
                return ResourceBundle.getBundle(BUNDLE, fallback).getString(key);
            } catch (MissingResourceException missingFallback) {
                return key;
            }
        }
    }
}
