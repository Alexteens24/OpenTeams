package me.alexisbinh.openteams.api.extension;

import java.util.Locale;
import java.util.Map;
import org.bukkit.plugin.Plugin;

public interface TranslationRegistry {
    Registration register(Plugin owner, Locale locale, Map<String, String> translations);
}
