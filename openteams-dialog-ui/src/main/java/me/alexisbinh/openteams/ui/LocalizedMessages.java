package me.alexisbinh.openteams.ui;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

public final class LocalizedMessages {
    private static final String BUNDLE = "me.alexisbinh.openteams.ui.messages";

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Locale fallback;

    public LocalizedMessages(Locale fallback) {
        this.fallback = fallback;
    }

    public Component component(Player player, String key) {
        return miniMessage.deserialize(text(player.locale(), key));
    }

    public String text(Locale locale, String key) {
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
