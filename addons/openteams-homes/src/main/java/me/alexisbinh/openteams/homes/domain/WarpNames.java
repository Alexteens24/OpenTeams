package me.alexisbinh.openteams.homes.domain;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class WarpNames {
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Set<String> RESERVED = Set.of(
            "home", "list", "create", "update", "rename", "delete", "info",
            "teleport", "confirm", "cancel", "page", "search", "set");

    private WarpNames() {
    }

    public static String validateAndNormalize(String input, int minimum, int maximum) {
        if (input == null || input.length() < minimum || input.length() > maximum
                || !ALLOWED.matcher(input).matches()) {
            throw new IllegalArgumentException("invalid_name");
        }
        var normalized = input.toLowerCase(Locale.ROOT);
        if (RESERVED.contains(normalized)) {
            throw new IllegalArgumentException("reserved_name");
        }
        return normalized;
    }

    public static String normalizeSearch(String input) {
        return input == null ? "" : input.strip().toLowerCase(Locale.ROOT);
    }
}
