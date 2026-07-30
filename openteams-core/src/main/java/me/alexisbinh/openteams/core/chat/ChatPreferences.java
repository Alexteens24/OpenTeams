package me.alexisbinh.openteams.core.chat;

public record ChatPreferences(boolean teamChat, boolean staffSpy) {
    public static ChatPreferences defaults() {
        return new ChatPreferences(false, false);
    }
}
