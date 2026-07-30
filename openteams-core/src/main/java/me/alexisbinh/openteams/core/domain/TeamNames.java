package me.alexisbinh.openteams.core.domain;

import java.text.Normalizer;
import java.util.Locale;

public final class TeamNames {
    private TeamNames() {
    }

    public static String normalize(String value) {
        return Normalizer.normalize(value.strip(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    public static boolean validName(String value) {
        var normalized = normalize(value);
        return normalized.length() >= 3
                && normalized.length() <= 24
                && normalized.codePoints().allMatch(character ->
                Character.isLetterOrDigit(character) || character == '_' || character == ' ');
    }

    public static boolean validTag(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        var normalized = normalize(value);
        return normalized.length() >= 2
                && normalized.length() <= 8
                && normalized.codePoints().allMatch(Character::isLetterOrDigit);
    }
}
